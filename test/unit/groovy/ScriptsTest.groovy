import com.sap.gateway.ip.core.customdev.util.Message
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile

// Run with test/unit/run.sh. Plain asserts, no test framework needed.
int passed = 0
List<String> failures = []
def test = { String name, Closure body ->
    try { body(); passed++; println "ok   $name" } catch (Throwable t) { failures << name; println "FAIL $name -> ${t.message?.readLines()?.head()}" }
}
def logs = [:]
def binding = new Binding([messageLogFactory: [getMessageLog: { m -> [addCustomHeaderProperty: { k, v -> logs[k] = v }, setStringProperty: { k, v -> logs[k] = v }] }]])
def script = { String n -> new GroovyShell(this.class.classLoader, binding).parse(new File("src/groovy/${n}.groovy")) }
def file = { String n -> new File("test/invoices/${n}.xml").bytes }
def msg = { body -> new Message(body: body) }
def codeOf = { Closure c -> try { c(); return null } catch (Exception e) { def m = (e.message =~ /^([A-Z]+_[A-Z0-9_]+)/); return m.find() ? m.group(1) : "NOT_AN_EI_CODE: ${e.message}" } }

// ---- CheckSize
test('CheckSize accepts a small payload') { script('CheckSize').processData(msg(file('TC01_xrechnung_ubl_po'))) }
test('CheckSize rejects above LW_MaxPayloadMB with EI_TOO_LARGE') {
    def m = msg(new byte[2 * 1024 * 1024]); m.setProperty('ei.maxMB', '1')
    assert codeOf { script('CheckSize').processData(m) } == 'EI_TOO_LARGE'
}
test('CheckSize defaults to 10 MB') {
    assert codeOf { script('CheckSize').processData(msg(new byte[11 * 1024 * 1024])) } == 'EI_TOO_LARGE'
    script('CheckSize').processData(msg(new byte[9 * 1024 * 1024]))
}

// ---- DetectFormat
test('DetectFormat TC01 (UBL)') {
    def m = script('DetectFormat').processData(msg(file('TC01_xrechnung_ubl_po')))
    assert m.getProperty('ei.syntax') == 'UBL'
    assert m.getProperty('ei.profile') == 'urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0'
    assert m.getProperty('ei.typeCode') == '380'
    assert m.getProperty('ei.invoiceNumber') == 'MH-2026-10457'
    assert m.getProperty('ei.issueDate') == '2026-11-16'
    assert m.getProperty('ei.sellerVat') == 'DE200000007'
    assert m.getHeader('SAP_ApplicationID', String) == 'MH-2026-10457' && m.getHeader('SAP_Sender', String) == 'DE200000007'
    assert logs.InvoiceNumber == 'MH-2026-10457' && logs.Supplier == 'DE200000007'
}
test('DetectFormat TC02 (CII, date format 102 normalised)') {
    def m = script('DetectFormat').processData(msg(file('TC02_xrechnung_cii_po')))
    assert m.getProperty('ei.syntax') == 'CII'
    assert m.getProperty('ei.invoiceNumber') == 'DW-88213' && m.getProperty('ei.sellerVat') == 'DE200000003'
    assert m.getProperty('ei.issueDate') == '2026-11-17' && m.getProperty('ei.typeCode') == '380'
}
test('DetectFormat finds a UBL credit note (UBL_CN, 381)') {
    def xml = new String(file('TC01_xrechnung_ubl_po'), 'UTF-8')
        .replace('ubl:Invoice', 'cn:CreditNote').replace('xmlns:ubl="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"', 'xmlns:cn="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"')
        .replace('InvoiceTypeCode', 'CreditNoteTypeCode').replace('>380<', '>381<')
    def m = script('DetectFormat').processData(msg(xml))
    assert m.getProperty('ei.syntax') == 'UBL_CN' && m.getProperty('ei.typeCode') == '381'
}
test('DetectFormat: an order is EI_UNKNOWN_FORMAT') {
    assert codeOf { script('DetectFormat').processData(msg('<Order xmlns="urn:oasis:names:specification:ubl:schema:xsd:Order-2"/>')) } == 'EI_UNKNOWN_FORMAT'
}
test('DetectFormat: garbage is EI_UNKNOWN_FORMAT') {
    assert codeOf { script('DetectFormat').processData(msg('hello, not xml')) } == 'EI_UNKNOWN_FORMAT'
}
test('DetectFormat refuses DOCTYPE / XXE') {
    def xxe = '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><x>&e;</x>'
    assert codeOf { script('DetectFormat').processData(msg(xxe)) } == 'EI_UNKNOWN_FORMAT'
}

// ---- ProfileAllowList
def decide = { String profile, String syntax = 'CII', String type = '380' -> script('ProfileAllowList').decide(profile, syntax, type) }
def XR = 'urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0'
test('allow-list: XRechnung 3.0 and its extension -> XRECHNUNG') {
    assert decide(XR) == 'XRECHNUNG'
    assert decide(XR + '#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0') == 'XRECHNUNG'
}
test('allow-list: ZUGFeRD EN 16931, BASIC, EXTENDED -> EN16931') {
    assert decide('urn:cen.eu:en16931:2017') == 'EN16931'
    assert decide('urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic') == 'EN16931'
    assert decide('urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended') == 'EN16931'
}
test('allow-list: MINIMUM and BASIC WL -> EI_PROFILE_NOT_INVOICE') {
    assert codeOf { decide('urn:factur-x.eu:1p0:minimum') } == 'EI_PROFILE_NOT_INVOICE'
    assert codeOf { decide('urn:factur-x.eu:1p0:basicwl') } == 'EI_PROFILE_NOT_INVOICE'
}
test('allow-list: exact match only, a shared prefix is not enough (the substring trap)') {
    assert codeOf { decide('urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basicwl') } == 'EI_PROFILE_UNKNOWN'
    assert codeOf { decide('urn:cen.eu:en16931:2017#whatever') } == 'EI_PROFILE_UNKNOWN'
    assert codeOf { decide('urn:xeinkauf.de:kosit:xrechnung_4.0') } == 'EI_PROFILE_UNKNOWN'
    assert codeOf { decide('') } == 'EI_PROFILE_UNKNOWN'
}
test('allow-list: credit notes -> EI_CREDIT_NOTE_MANUAL (381 or UBL_CN)') {
    assert codeOf { decide(XR, 'UBL_CN', '381') } == 'EI_CREDIT_NOTE_MANUAL'
    assert codeOf { decide(XR, 'CII', '381') } == 'EI_CREDIT_NOTE_MANUAL'
}
test('allow-list script sets ei.mode') {
    def m = msg('x'); m.setProperty('ei.profile', XR); m.setProperty('ei.syntax', 'UBL'); m.setProperty('ei.typeCode', '380')
    assert script('ProfileAllowList').processData(m).getProperty('ei.mode') == 'XRECHNUNG'
}

// ---- EvaluateSvrl, with the real reports produced by validate-kosit.sh
def svrl = { String n -> new File("build/svrl/${n}.svrl").text }
def eval = { String tc, String xml = '<restored/>' ->
    def m = msg('<svrl/>'); m.setProperty('ei.xml', xml); m.setProperty('ei.svrlEn', svrl("${tc}.en"))
    m.setProperty('ei.svrlXr', svrl("${tc}.xr")); m
}
test('EvaluateSvrl: valid invoices (TC01, TC02) pass and the body is restored') {
    ['TC01_xrechnung_ubl_po', 'TC02_xrechnung_cii_po'].each { tc ->
        def m = script('EvaluateSvrl').processData(eval(tc))
        assert m.getBody(String) == '<restored/>'
    }
}
test('EvaluateSvrl: TC05 fails in the XRechnung report only (BR-DE-15)') {
    def m = eval('TC05_missing_buyer_reference')
    assert codeOf { script('EvaluateSvrl').processData(m) } == 'EI_RULES'
    assert m.getProperty('ei.details').contains('BR-DE-15')
    assert !svrl('TC05_missing_buyer_reference.en').contains('flag="fatal"')
}
test('EvaluateSvrl: TC06 fails in the EN 16931 report only (BR-CO-16)') {
    def m = eval('TC06_wrong_payable_amount')
    assert codeOf { script('EvaluateSvrl').processData(m) } == 'EI_RULES'
    assert m.getProperty('ei.details').contains('BR-CO-16')
    assert !svrl('TC06_wrong_payable_amount.xr').contains('flag="fatal"')
}
test('EvaluateSvrl: warnings do not reject') {
    def w = '<svrl:schematron-output xmlns:svrl="http://purl.oclc.org/dsdl/svrl"><svrl:failed-assert id="BR-X" flag="warning"><svrl:text>just a warning</svrl:text></svrl:failed-assert></svrl:schematron-output>'
    def m = msg('<svrl/>'); m.setProperty('ei.svrlEn', w); m.setProperty('ei.xml', '<ok/>')
    script('EvaluateSvrl').processData(m)
    assert logs['ei.warnings'].contains('just a warning')
}

test('EvaluateSvrl: a property that is not XML (e.g. a Constant "${in.body}") is named in the error, not a bare parser message') {
    def m = msg('<svrl/>'); m.setProperty('ei.xml', '<ok/>'); m.setProperty('ei.svrlEn', svrl('TC01_xrechnung_ubl_po.en')); m.setProperty('ei.svrlXr', '${in.body}')
    try { script('EvaluateSvrl').processData(m); assert false, 'should fail' }
    catch (IllegalStateException e) { assert e.message.contains('ei.svrlXr') && e.message.contains('${in.body}') }
}
test('EvaluateSvrl: properties holding the raw bytes of the body (as Cloud Integration stores ${in.body}) work') {
    def m = msg('<svrl/>')
    m.setProperty('ei.xml', file('TC02_xrechnung_cii_po'))
    m.setProperty('ei.svrlEn', svrl('TC02_xrechnung_cii_po.en').getBytes('UTF-8')); m.setProperty('ei.svrlXr', svrl('TC02_xrechnung_cii_po.xr').getBytes('UTF-8'))
    def out = script('EvaluateSvrl').processData(m)
    assert out.getBody(String).startsWith('<?xml') && out.getBody(String).contains('DW-88213')
    def bad = msg('<svrl/>'); bad.setProperty('ei.svrlEn', svrl('TC06_wrong_payable_amount.en').getBytes('UTF-8'))
    assert codeOf { script('EvaluateSvrl').processData(bad) } == 'EI_RULES'
}
test('EvaluateSvrl: a BOM or blank before the SVRL is tolerated') {
    def m = eval('TC01_xrechnung_ubl_po'); m.setProperty('ei.svrlEn', '\uFEFF \n' + svrl('TC01_xrechnung_ubl_po.en'))
    script('EvaluateSvrl').processData(m)
}

// ---- ExtractZugferd
def pdfWith = { Closure fill ->
    def doc = new PDDocument(); doc.addPage(new PDPage()); fill(doc)
    def out = new ByteArrayOutputStream(); doc.save(out); doc.close(); out.toByteArray()
}
def spec = { PDDocument doc, String name, byte[] data ->
    def ef = new PDEmbeddedFile(doc, new ByteArrayInputStream(data)); ef.setSubtype('text/xml'); ef.setSize(data.length)
    def fs = new PDComplexFileSpecification(); fs.setFile(name); fs.setEmbeddedFile(ef); fs
}
def attach = { PDDocument doc, PDEmbeddedFilesNameTreeNode tree ->
    def names = new PDDocumentNameDictionary(doc.documentCatalog); names.setEmbeddedFiles(tree); doc.documentCatalog.setNames(names)
}
test('ExtractZugferd leaves XML untouched') {
    def m = script('ExtractZugferd').processData(msg(file('TC01_xrechnung_ubl_po')))
    assert m.getProperty('ei.source') == 'XML'
}
test('ExtractZugferd takes factur-x.xml out of a PDF') {
    def xml = file('TC02_xrechnung_cii_po')
    def pdf = pdfWith { doc -> def t = new PDEmbeddedFilesNameTreeNode(); t.setNames(['factur-x.xml': spec(doc, 'factur-x.xml', xml)]); attach(doc, t) }
    def m = script('ExtractZugferd').processData(msg(pdf))
    assert m.getProperty('ei.source') == 'PDF' && m.getProperty('ei.embeddedName') == 'factur-x.xml'
    assert Arrays.equals(m.getBody(byte[].class), xml)
}
test('ExtractZugferd finds a file in a nested (kids) name tree') {
    def xml = file('TC02_xrechnung_cii_po')
    def pdf = pdfWith { doc ->
        def kid = new PDEmbeddedFilesNameTreeNode(); kid.setNames(['xrechnung.xml': spec(doc, 'xrechnung.xml', xml)])
        def root = new PDEmbeddedFilesNameTreeNode(); root.setKids([kid]); attach(doc, root)
    }
    assert Arrays.equals(script('ExtractZugferd').processData(msg(pdf)).getBody(byte[].class), xml)
}
test('ExtractZugferd: PDF without embedded XML -> EI_PDF_WITHOUT_XML') {
    assert codeOf { script('ExtractZugferd').processData(msg(pdfWith { })) } == 'EI_PDF_WITHOUT_XML'
}
test('ExtractZugferd: an attachment with another name is not an invoice') {
    def pdf = pdfWith { doc -> def t = new PDEmbeddedFilesNameTreeNode(); t.setNames(['notes.txt': spec(doc, 'notes.txt', 'hi'.bytes)]); attach(doc, t) }
    assert codeOf { script('ExtractZugferd').processData(msg(pdf)) } == 'EI_PDF_WITHOUT_XML'
}
test('ExtractZugferd: a broken PDF -> EI_PDF_WITHOUT_XML') {
    assert codeOf { script('ExtractZugferd').processData(msg('%PDF-1.4 this is not a pdf'.bytes)) } == 'EI_PDF_WITHOUT_XML'
}

// ---- ClassifyError
def classify = { Exception e, String details = null ->
    def m = msg(''); m.setProperty('CamelExceptionCaught', e); if (details) m.setProperty('ei.details', details)
    script('ClassifyError').processData(m)
}
test('ClassifyError: rejects with 422 and the rule list') {
    def m = classify(new Exception('EI_RULES: 1 fatal'), '["BR-DE-15 Buyer reference missing"]')
    assert m.getProperty('ei.result') == 'REJECTED' && m.getProperty('ei.code') == 'EI_RULES'
    assert m.getHeader('CamelHttpResponseCode', Integer) == 422
    def json = new groovy.json.JsonSlurper().parseText(m.getBody(String))
    assert json.result == 'REJECTED' && json.details == ['BR-DE-15 Buyer reference missing']
}
test('ClassifyError: credit note is queued for Aylin with 202') {
    def m = classify(new Exception('EI_CREDIT_NOTE_MANUAL: x'))
    assert m.getProperty('ei.result') == 'EXCEPTION_QUEUED' && m.getHeader('CamelHttpResponseCode', Integer) == 202
}
test('ClassifyError: finds the code in a message wrapped by Cloud Integration, and cleans the reason') {
    def m = classify(new Exception('java.lang.Exception: java.lang.Exception: EI_NO_PO_MANUAL: the invoice has no purchase order reference (BT-13)@ line 16 in PrepareEnrich.groovy'))
    assert m.getProperty('ei.code') == 'EI_NO_PO_MANUAL' && m.getProperty('ei.result') == 'EXCEPTION_QUEUED' && m.getHeader('CamelHttpResponseCode', Integer) == 202
    assert m.getProperty('ei.reason') == 'the invoice has no purchase order reference (BT-13)'
    def r = classify(new Exception('java.lang.Exception: EI_RULES: 1 fatal rule violation(s): BR-DE-15@ line 40 in EvaluateSvrl.groovy'), '["BR-DE-15 x"]')
    assert r.getProperty('ei.result') == 'REJECTED' && r.getHeader('CamelHttpResponseCode', Integer) == 422
}
test('ClassifyError: the code is also found in the cause chain') {
    def m = classify(new RuntimeException('script failed', new Exception('EI_VM_MISSING: UnitCode=MTR')))
    assert m.getProperty('ei.code') == 'EI_VM_MISSING' && m.getProperty('ei.reason') == 'UnitCode=MTR'
}
test('ClassifyError: a message that merely mentions a code in the middle of prose is technical') {
    assert classify(new Exception('see EI_RULES in the docs')).getProperty('ei.result') == 'TECHNICAL'
}
test('ClassifyError: XML Validator failures become EI_SCHEMA') {
    def m = classify(new Exception('XML validation failed: cvc-complex-type.2.4.a'))
    assert m.getProperty('ei.code') == 'EI_SCHEMA' && m.getProperty('ei.result') == 'REJECTED'
}
test('ClassifyError: anything else is technical (500)') {
    def m = classify(new IOException('Connection refused'))
    assert m.getProperty('ei.result') == 'TECHNICAL' && m.getHeader('CamelHttpResponseCode', Integer) == 500
}

println "\n${passed} passed, ${failures.size()} failed"
if (failures) { println "Failed: ${failures}"; System.exit(1) }
