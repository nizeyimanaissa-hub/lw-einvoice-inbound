import com.sap.gateway.ip.core.customdev.util.Message
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

// Phase 5, step 5.2 (before the supplier lookup). Takes the canonical invoice from the body and runs every
// check that needs no S/4HANA call, so a hopeless invoice never costs an API call:
//   no PO number (BT-13) or a line without PO line (BT-132)  -> EI_NO_PO_MANUAL
//   invoice number longer than the API field (16)            -> EI_FIELD_TOO_LONG
//   seller VAT ID that is not a plain alphanumeric ID        -> EI_SUPPLIER_UNKNOWN (never put it into a $filter)
// Then it keeps the canonical XML in property ei.canonical and sets ei.vatFilter, the value the OData query
// "$filter=VATRegistration eq '${property.ei.vatFilter}'" uses.
def Message processData(Message message) {
    String xml = message.getBody(String) as String
    def doc = parse(xml)
    String po = text(doc, 'Header/PurchaseOrder')
    if (!po) throw new Exception('EI_NO_PO_MANUAL: the invoice has no purchase order reference (BT-13); non-PO invoices are handled manually in v1')
    def lines = doc.getElementsByTagName('Line')
    for (int i = 0; i < lines.getLength(); i++) {
        def line = lines.item(i)
        if (!child(line, 'PurchaseOrderItem')) {
            throw new Exception('EI_NO_PO_MANUAL: invoice line ' + (child(line, 'Id') ?: '?') + ' has no purchase order line reference (BT-132)')
        }
    }
    String number = text(doc, 'Header/Number')
    if (number.length() > 16) {
        throw new Exception('EI_FIELD_TOO_LONG: invoice number "' + number + '" has ' + number.length() + ' characters, the S/4HANA field holds 16')
    }
    String vat = text(doc, 'Header/SellerVatId').replaceAll(/\s+/, '').toUpperCase()
    if (!(vat ==~ /[A-Z0-9]{2,20}/)) {
        throw new Exception('EI_SUPPLIER_UNKNOWN: seller VAT ID "' + vat + '" is empty or not a valid VAT ID')
    }
    message.setProperty('ei.canonical', xml)
    message.setProperty('ei.vatFilter', vat)
    return message
}

def parse(String xml) {
    def f = DocumentBuilderFactory.newInstance()
    f.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
    return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
}

// text of a path of direct children below the root, e.g. 'Header/Number' ('' when missing)
def text(doc, String path) {
    def n = doc.getDocumentElement()
    for (String step : path.split('/')) {
        n = n == null ? null : child(n, step, true)
    }
    return n == null ? '' : n.getTextContent().trim()
}

def child(parent, String name, boolean asNode = false) {
    for (def c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
        if (c.getNodeType() == 1 && c.getNodeName() == name) return asNode ? c : c.getTextContent().trim()
    }
    return asNode ? null : ''
}
