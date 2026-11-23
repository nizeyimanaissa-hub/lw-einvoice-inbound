import com.sap.gateway.ip.core.customdev.util.Message
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.xml.sax.InputSource

// Step 5. Detects the syntax and reads the facts later steps need into exchange properties:
//   ei.syntax UBL | UBL_CN | CII, ei.profile (specification ID), ei.typeCode, ei.invoiceNumber,
//   ei.issueDate (YYYY-MM-DD), ei.sellerVat. Also sets SAP_ApplicationID / SAP_Sender and the custom
//   header properties InvoiceNumber and Supplier so the message is searchable in Monitor Message Processing.
// Security: the sender is untrusted, so DOCTYPE declarations (XXE, entity expansion) are refused.
// ADR 0002: a new syntax (XRechnung 4.0) is a new entry in SYNTAX_BY_ROOT and a new input mapping.
def Message processData(Message message) {
    final Map<String, String> SYNTAX_BY_ROOT = [
        '{urn:oasis:names:specification:ubl:schema:xsd:Invoice-2}Invoice'                          : 'UBL',
        '{urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2}CreditNote'                    : 'UBL_CN',
        '{urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100}CrossIndustryInvoice'       : 'CII'
    ]
    byte[] body = message.getBody(byte[].class) ?: new byte[0]
    def doc
    try {
        def f = DocumentBuilderFactory.newInstance()
        f.setNamespaceAware(true)
        f.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        f.setXIncludeAware(false)
        f.setExpandEntityReferences(false)
        doc = f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(body)))
    } catch (Exception e) {
        throw new Exception('EI_UNKNOWN_FORMAT: not well-formed XML or contains a DOCTYPE (' + e.getMessage() + ')')
    }
    def root = doc.getDocumentElement()
    String syntax = SYNTAX_BY_ROOT['{' + root.getNamespaceURI() + '}' + root.getLocalName()]
    if (syntax == null) {
        throw new Exception('EI_UNKNOWN_FORMAT: root element {' + root.getNamespaceURI() + '}' + root.getLocalName() + ' is not an invoice')
    }

    def xp = XPathFactory.newInstance().newXPath()
    xp.setNamespaceContext(new NamespaceContext() {
        private final Map<String, String> m = [
            cbc: 'urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2',
            cac: 'urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2',
            rsm: 'urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100',
            ram: 'urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100',
            udt: 'urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100']
        String getNamespaceURI(String p) { m[p] ?: XMLConstants.NULL_NS_URI }
        String getPrefix(String u) { m.find { it.value == u }?.key }
        Iterator getPrefixes(String u) { m.findAll { it.value == u }.keySet().iterator() }
    })
    def text = { String expr -> (xp.evaluate(expr, doc, XPathConstants.STRING) ?: '').toString().trim() }

    String profile, typeCode, number, issued, vat
    if (syntax == 'CII') {
        profile = text('/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID')
        typeCode = text('/rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:TypeCode')
        number = text('/rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:ID')
        issued = text('/rsm:CrossIndustryInvoice/rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString')
        vat = text("/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']")
        if (issued ==~ /\d{8}/) issued = issued.substring(0, 4) + '-' + issued.substring(4, 6) + '-' + issued.substring(6, 8) // format 102
    } else {
        profile = text('/*/cbc:CustomizationID')
        typeCode = (syntax == 'UBL_CN') ? (text('/*/cbc:CreditNoteTypeCode') ?: '381') : text('/*/cbc:InvoiceTypeCode')
        number = text('/*/cbc:ID')
        issued = text('/*/cbc:IssueDate')
        vat = text("/*/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme[cac:TaxScheme/cbc:ID='VAT']/cbc:CompanyID")
    }

    message.setProperty('ei.syntax', syntax)
    message.setProperty('ei.profile', profile)
    message.setProperty('ei.typeCode', typeCode)
    message.setProperty('ei.invoiceNumber', number)
    message.setProperty('ei.issueDate', issued)
    message.setProperty('ei.sellerVat', vat)
    if (number) message.setHeader('SAP_ApplicationID', number)
    if (vat) message.setHeader('SAP_Sender', vat)
    def log = (binding.hasVariable('messageLogFactory') && messageLogFactory != null) ? messageLogFactory.getMessageLog(message) : null
    if (log != null) {
        log.addCustomHeaderProperty('InvoiceNumber', number ?: '')
        log.addCustomHeaderProperty('Supplier', vat ?: '')
    }
    return message
}
