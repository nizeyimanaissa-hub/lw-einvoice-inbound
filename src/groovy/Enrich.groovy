import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.mapping.ValueMappingApi
import java.time.LocalDate
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

// Phase 5, step 5.2 (after the supplier lookup). Body = response of the request-reply
// A_Supplier?$select=Supplier&$filter=VATRegistration eq '<ei.vatFilter>'; property ei.canonical = canonical invoice.
// Adds an <S4> block to Header, every Line and every Tax with the keys the API needs:
//   Header/S4  CompanyCode (BuyerReference -> CompanyCode), InvoicingParty (the supplier found by VAT ID)
//   Line/S4    Unit (UnitCode -> Unit), TaxCode (TaxCategoryRate -> TaxCode, key "S|19")
//   Tax/S4     TaxCode
// Nothing is defaulted: a lookup without result is a business error that names the value
//   EI_SUPPLIER_UNKNOWN: VATRegistration=DE200000099      EI_SUPPLIER_AMBIGUOUS     EI_VM_MISSING: UnitCode=MTR
// The result replaces the body; property postingDate (today, Europe/Berlin) feeds canonical-to-api.xsl.
def Message processData(Message message) {
    String canonical = message.getProperty('ei.canonical') as String
    String vat = message.getProperty('ei.vatFilter') as String
    if (!canonical) throw new IllegalStateException('Enrich: property ei.canonical is missing (PrepareEnrich did not run)')

    List<String> suppliers = suppliersIn(message.getBody(String) as String)
    if (suppliers.isEmpty()) throw new Exception('EI_SUPPLIER_UNKNOWN: VATRegistration=' + vat + ' is not a supplier in S/4HANA')
    if (suppliers.size() > 1) throw new Exception('EI_SUPPLIER_AMBIGUOUS: VATRegistration=' + vat + ' belongs to ' + suppliers.size() + ' suppliers: ' + suppliers.join(', '))

    ValueMappingApi vm = ITApiFactory.getApi(ValueMappingApi.class, null)
    def map = { String sourceId, String value, String targetId ->
        String mapped = null
        try { mapped = vm.getMappedValue('EInvoice', sourceId, value, 'S4', targetId) } catch (Exception ignored) { /* no entry: same as null */ }
        if (!mapped) throw new Exception('EI_VM_MISSING: ' + sourceId + '=' + value)
        return mapped
    }

    def doc = parse(canonical)
    def root = doc.getDocumentElement()
    def header = first(root, 'Header')
    s4(doc, header, [CompanyCode: map('BuyerReference', text(header, 'BuyerReference'), 'CompanyCode'), InvoicingParty: suppliers[0]])
    each(root, 'Line') { line ->
        s4(doc, line, [Unit    : map('UnitCode', first(line, 'Quantity').getAttribute('unit'), 'Unit'),
                       TaxCode : map('TaxCategoryRate', text(line, 'TaxCategory') + '|' + text(line, 'TaxRate'), 'TaxCode')])
    }
    each(root, 'Tax') { tax ->
        s4(doc, tax, [TaxCode: map('TaxCategoryRate', text(tax, 'Category') + '|' + text(tax, 'Rate'), 'TaxCode')])
    }

    def out = new StringWriter()
    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(out))
    message.setBody(out.toString())
    message.setProperty('postingDate', LocalDate.now(ZoneId.of('Europe/Berlin')).toString())
    return message
}

def suppliersIn(String body) {
    if (!body?.trim()) return []
    def f = DocumentBuilderFactory.newInstance()
    f.setNamespaceAware(true)
    f.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
    def doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(body)))
    def list = doc.getElementsByTagNameNS('*', 'Supplier')
    return (0..<list.getLength()).collect { list.item(it).getTextContent().trim() }.findAll { it }.unique()
}

def parse(String xml) {
    def f = DocumentBuilderFactory.newInstance()
    f.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
    return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
}

def first(parent, String name) {
    for (def c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
        if (c.getNodeType() == 1 && c.getNodeName() == name) return c
    }
    return null
}

def text(parent, String name) { first(parent, name)?.getTextContent()?.trim() ?: '' }

def each(root, String name, Closure body) {
    def list = root.getElementsByTagName(name)
    (0..<list.getLength()).collect { list.item(it) }.each(body)
}

def s4(doc, parent, Map<String, String> values) {
    def block = doc.createElement('S4')
    values.each { k, v -> def e = doc.createElement(k); e.setTextContent(v); block.appendChild(e) }
    parent.appendChild(block)
}
