import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.mapping.ValueMappingApi
import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import org.w3c.dom.Node
import org.xml.sax.InputSource

// Phase 5 tests: the three XSLTs (Saxon-HE) and the two enrichment scripts, chained like the iFlow does.
// Run with test/unit/run.sh. Plain asserts, no test framework needed.
int passed = 0
List<String> failures = []
def test = { String name, Closure body ->
    try { body(); passed++; println "ok   $name" } catch (Throwable t) { failures << name; println "FAIL $name -> ${t.message?.readLines()?.head()}" }
}
def script = { String n -> new GroovyShell(this.class.classLoader, new Binding()).parse(new File("src/groovy/${n}.groovy")) }
def codeOf = { Closure c -> try { c(); return null } catch (Exception e) { def m = (e.message =~ /^([A-Z]+_[A-Z0-9_]+)/); return m.find() ? m.group(1) : "NOT_AN_EI_CODE: ${e.message}" } }

def xslt = { String sheet, String input, Map params = [:] ->
    def t = TransformerFactory.newInstance().newTransformer(new StreamSource(new File("src/xslt/${sheet}.xsl")))
    params.each { k, v -> t.setParameter(k, v) }
    def out = new StringWriter()
    t.transform(new StreamSource(new StringReader(input)), new StreamResult(out))
    out.toString()
}
def dom = { String xml ->
    def f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true)
    def d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
    def strip
    strip = { Node n -> // whitespace-only text between elements is layout, not content
        for (def c = n.firstChild; c != null;) { def next = c.nextSibling
            if (c.nodeType == Node.TEXT_NODE && c.textContent.trim().isEmpty() && n.childNodes.length > 1) n.removeChild(c) else strip(c)
            c = next }
    }
    strip(d.documentElement); d.documentElement.normalize(); d
}
def sameXml = { String a, String b -> assert dom(a).documentElement.isEqualNode(dom(b).documentElement), "XML differs:\n$a\nvs\n$b" }
def read = { String p -> new File(p).getText('UTF-8') }

// Value mapping from the committed artefact (iflow/LW_EI_ValueMappings.jar), same lookup as the CPI API: null = no entry
def valueMapping = {
    def zip = new ZipFile('iflow/LW_EI_ValueMappings.jar')
    def root = new XmlSlurper().parse(zip.getInputStream(zip.getEntry('value_mapping.xml')))
    def groups = root.group.collect { g -> g.entry.collect { [agency: it.agency.text(), schema: it.schema.text(), value: it.value.text()] } }
    return { String sa, String si, String sv, String ta, String ti ->
        def g = groups.find { it.any { e -> e.agency == sa && e.schema == si && e.value == sv } }
        g?.find { it.agency == ta && it.schema == ti }?.value
    } as ValueMappingApi
}()
ITApiFactory.stub = valueMapping

def supplierReply = { String... ids -> '<A_Suppliers>' + ids.collect { "<A_Supplier><Supplier>${it}</Supplier></A_Supplier>" }.join() + '</A_Suppliers>' }
// the iFlow chain from the canonical invoice to the adapter request; the lookup reply stands in for the S/4HANA call
def chain = { String canonical, String reply, String posting = '2026-11-20' ->
    def m = script('PrepareEnrich').processData(new Message(body: canonical))
    m.setBody(reply)
    m = script('Enrich').processData(m)
    return xslt('canonical-to-api', m.getBody(String), [postingDate: posting])
}
// adapter XML -> the OData V2 JSON of the guide (navigation properties as {results:[...]}, dates as /Date(ms)/)
def toJson
toJson = { Node n ->
    def kids = (0..<n.childNodes.length).collect { n.childNodes.item(it) }.findAll { it.nodeType == Node.ELEMENT_NODE }
    if (!kids) {
        String v = n.textContent
        if (v ==~ /\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d/) return "/Date(${LocalDateTime.parse(v).toInstant(ZoneOffset.UTC).toEpochMilli()})/"
        return n.nodeName == 'TaxIsCalculatedAutomatically' ? Boolean.parseBoolean(v) : v
    }
    if (n.nodeName.startsWith('to_')) return [results: kids.collect { toJson(it) }]
    return kids.collectEntries { [(it.nodeName): toJson(it)] }
}
def json = { String p -> new JsonSlurper().parseText(read(p)) }
def TC01 = read('test/invoices/TC01_xrechnung_ubl_po.xml'), TC02 = read('test/invoices/TC02_xrechnung_cii_po.xml')

// ---- XSLT 1 and 2: syntax -> canonical
test('ubl-to-canonical: TC01 equals the committed expected file') { sameXml(xslt('ubl-to-canonical', TC01), read('test/expected/TC01.canonical.xml')) }
test('cii-to-canonical: TC02 equals the committed expected file') { sameXml(xslt('cii-to-canonical', TC02), read('test/expected/TC02.canonical.xml')) }
test('canonical: UBL and CII give the same set of element names') {
    def names = { String xml -> def l = []; def walk; walk = { Node n -> l << n.nodeName; (0..<n.childNodes.length).collect { n.childNodes.item(it) }.findAll { it.nodeType == 1 }.each(walk) }; walk(dom(xml).documentElement); l }
    assert names(xslt('ubl-to-canonical', TC01)).toSet() == names(xslt('cii-to-canonical', TC02)).toSet()
}
test('ubl-to-canonical: no PO reference stays empty (decided later by PrepareEnrich, not defaulted here)') {
    def c = xslt('ubl-to-canonical', TC01.replaceAll(/(?s)<cac:OrderReference>.*?<\/cac:OrderReference>/, ''))
    assert dom(c).getElementsByTagName('PurchaseOrder').item(0).textContent == ''
}
test('cii-to-canonical: amounts are normalised to 2 decimals, rates and quantities without trailing zeros') {
    def c = dom(xslt('cii-to-canonical', TC02.replace('>600.00</ram:LineTotalAmount>', '>600</ram:LineTotalAmount>')
        .replace('>5000</ram:BilledQuantity>', '>5000.0000</ram:BilledQuantity>').replace('>19</ram:RateApplicablePercent>', '>19.00</ram:RateApplicablePercent>')))
    assert c.getElementsByTagName('NetAmount').item(0).textContent == '600.00'
    assert c.getElementsByTagName('Quantity').item(0).textContent == '5000'
    assert c.getElementsByTagName('Rate').item(0).textContent == '19' && c.getElementsByTagName('TaxRate').item(0).textContent == '19'
}

// ---- Enrichment scripts and XSLT 3: the whole chain against the request of the guide
test('chain: TC01 gives the request of the guide (Phase 5 done-when)') {
    def api = chain(xslt('ubl-to-canonical', TC01), supplierReply('20000007'))
    assert toJson(dom(api).documentElement) == json('test/expected/TC01.api.json')
}
test('chain: TC02 posts Skonto 2 % / 14 days, H87 -> PC, NetPaymentDays 30') {
    def api = chain(xslt('cii-to-canonical', TC02), supplierReply('20000003'))
    assert toJson(dom(api).documentElement) == json('test/expected/TC02.api.json')
}
test('canonical-to-api: element order follows the EDMX (adapter XSD is a sequence)') {
    def api = dom(chain(xslt('ubl-to-canonical', TC01), supplierReply('20000007'))).documentElement
    def top = (0..<api.childNodes.length).collect { api.childNodes.item(it) }.findAll { it.nodeType == 1 }*.nodeName
    assert top == ['CompanyCode', 'DocumentDate', 'PostingDate', 'SupplierInvoiceIDByInvcgParty', 'InvoicingParty', 'DocumentCurrency',
                   'InvoiceGrossAmount', 'DueCalculationBaseDate', 'NetPaymentDays', 'TaxIsCalculatedAutomatically', 'to_SuplrInvcItemPurOrdRef', 'to_SupplierInvoiceTax']
}
test('canonical-to-api: two Skonto lines fill CashDiscount1 and 2; no due date means no NetPaymentDays') {
    def c = xslt('cii-to-canonical', TC02.replace('#SKONTO#TAGE=14#PROZENT=2.00#', '#SKONTO#TAGE=7#PROZENT=3.00#\n#SKONTO#TAGE=14#PROZENT=2.00#')
        .replaceAll(/(?s)<ram:DueDateDateTime>.*?<\/ram:DueDateDateTime>/, ''))
    def j = toJson(dom(chain(c, supplierReply('20000003'))).documentElement)
    assert j.CashDiscount1Days == '7' && j.CashDiscount1Percent == '3.00' && j.CashDiscount2Days == '14' && j.CashDiscount2Percent == '2.00'
    assert !j.containsKey('NetPaymentDays')
}
test('canonical-to-api: several VAT rates give one tax line each') {
    def c = xslt('ubl-to-canonical', TC01)
    c = c.replace('</Taxes>', '<Tax><Category>S</Category><Rate>7</Rate><BaseAmount>100.00</BaseAmount><Amount>7.00</Amount></Tax></Taxes>')
    def j = toJson(dom(chain(c, supplierReply('20000007'))).documentElement)
    assert j.to_SupplierInvoiceTax.results*.TaxCode == ['V1', 'V2'] && j.to_SupplierInvoiceTax.results*.TaxAmount == ['456.00', '7.00']
}
test('canonical-to-api refuses an invoice that was not enriched') {
    try { xslt('canonical-to-api', xslt('ubl-to-canonical', TC01), [postingDate: '2026-11-20']); assert false, 'should stop' }
    catch (javax.xml.transform.TransformerException e) { assert e.message.contains('not enriched') }
}
test('canonical-to-api refuses a missing or malformed postingDate') {
    def enriched = script('Enrich').processData(script('PrepareEnrich').processData(new Message(body: xslt('ubl-to-canonical', TC01))).with { it.setBody(supplierReply('20000007')); it }).getBody(String)
    ['', '20.11.2026'].each { bad ->
        try { xslt('canonical-to-api', enriched, [postingDate: bad]); assert false, 'should stop' }
        catch (javax.xml.transform.TransformerException e) { assert e.message.contains('postingDate') }
    }
}

// ---- PrepareEnrich
def prepare = { String xml -> script('PrepareEnrich').processData(new Message(body: xml)) }
def canon = xslt('ubl-to-canonical', TC01)
test('PrepareEnrich keeps the canonical invoice and sets the VAT filter value') {
    def m = prepare(canon)
    assert m.getProperty('ei.canonical') == canon && m.getProperty('ei.vatFilter') == 'DE200000007'
}
test('PrepareEnrich: no PO -> EI_NO_PO_MANUAL') {
    assert codeOf { prepare(canon.replace('<PurchaseOrder>4500000101</PurchaseOrder>', '<PurchaseOrder/>')) } == 'EI_NO_PO_MANUAL'
}
test('PrepareEnrich: a line without PO line -> EI_NO_PO_MANUAL') {
    assert codeOf { prepare(canon.replace('<PurchaseOrderItem>10</PurchaseOrderItem>', '<PurchaseOrderItem/>')) } == 'EI_NO_PO_MANUAL'
}
test('PrepareEnrich: invoice number over 16 characters -> EI_FIELD_TOO_LONG') {
    assert codeOf { prepare(canon.replace('MH-2026-10457', 'MH-2026-10457-ABCD')) } == 'EI_FIELD_TOO_LONG'
    prepare(canon.replace('MH-2026-10457', 'MH-2026-10457-AB')) // exactly 16 passes
}
test('PrepareEnrich: a VAT ID with a quote never reaches the OData filter') {
    assert codeOf { prepare(canon.replace('DE200000007', "DE1' or Supplier ne '")) } == 'EI_SUPPLIER_UNKNOWN'
    assert codeOf { prepare(canon.replace('DE200000007', '')) } == 'EI_SUPPLIER_UNKNOWN'
    assert prepare(canon.replace('DE200000007', 'de 200 000 007')).getProperty('ei.vatFilter') == 'DE200000007'
}

// ---- Enrich
def enrich = { String xml, String reply -> def m = prepare(xml); m.setBody(reply); script('Enrich').processData(m) }
test('Enrich: unknown supplier (TC09) -> EI_SUPPLIER_UNKNOWN naming the VAT ID') {
    def c = canon.replace('DE200000007', 'DE200000099')
    try { enrich(c, supplierReply()); assert false } catch (Exception e) { assert e.message.startsWith('EI_SUPPLIER_UNKNOWN') && e.message.contains('DE200000099') }
    assert codeOf { enrich(c, '') } == 'EI_SUPPLIER_UNKNOWN'
}
test('Enrich: two suppliers for one VAT ID -> EI_SUPPLIER_AMBIGUOUS, never a guess') {
    assert codeOf { enrich(canon, supplierReply('20000007', '20000008')) } == 'EI_SUPPLIER_AMBIGUOUS'
}
test('Enrich: the same supplier twice in the reply is not ambiguous') { enrich(canon, supplierReply('20000007', '20000007')) }
test('Enrich: unknown buyer reference (TC15) -> EI_VM_MISSING naming the value') {
    try { enrich(canon.replace('LW-1100', 'LW-9999'), supplierReply('20000007')); assert false } catch (Exception e) { assert e.message == 'EI_VM_MISSING: BuyerReference=LW-9999' }
}
test('Enrich: unmapped unit code and unmapped tax rate -> EI_VM_MISSING') {
    try { enrich(canon.replace('unit="KGM"', 'unit="MTR"'), supplierReply('20000007')); assert false } catch (Exception e) { assert e.message == 'EI_VM_MISSING: UnitCode=MTR' }
    try { enrich(canon.replace('<Rate>19</Rate>', '<Rate>16</Rate>'), supplierReply('20000007')); assert false } catch (Exception e) { assert e.message == 'EI_VM_MISSING: TaxCategoryRate=S|16' }
}
test('Enrich: a value mapping that throws counts as missing') {
    ITApiFactory.stub = { String a, String b, String c, String d, String e -> throw new RuntimeException('no such mapping') } as ValueMappingApi
    try { assert codeOf { enrich(canon, supplierReply('20000007')) } == 'EI_VM_MISSING' } finally { ITApiFactory.stub = valueMapping }
}
test('Enrich: sets postingDate to today in Europe/Berlin and replaces the body with the enriched invoice') {
    def m = enrich(canon, supplierReply('20000007'))
    assert m.getProperty('postingDate') == java.time.LocalDate.now(java.time.ZoneId.of('Europe/Berlin')).toString()
    def d = dom(m.getBody(String)).documentElement
    assert d.getElementsByTagName('InvoicingParty').item(0).textContent == '20000007' && d.getElementsByTagName('Unit').item(0).textContent == 'KG'
}
test('Enrich accepts the lookup reply with namespaces (OData atom/XML) and ignores other elements') {
    def reply = '<feed xmlns="http://www.w3.org/2005/Atom" xmlns:d="http://schemas.microsoft.com/ado/2007/08/dataservices"><entry><content><d:Supplier>20000007</d:Supplier><d:SupplierName>x</d:SupplierName></content></entry></feed>'
    assert enrich(canon, reply).getBody(String).contains('<InvoicingParty>20000007</InvoicingParty>')
}

// ---- ClassifyError knows the Phase 5 codes
test('ClassifyError: Phase 5 business errors are queued for Aylin (202)') {
    ['EI_NO_PO_MANUAL', 'EI_SUPPLIER_UNKNOWN', 'EI_SUPPLIER_AMBIGUOUS', 'EI_VM_MISSING', 'EI_FIELD_TOO_LONG'].each { code ->
        def m = new Message(body: ''); m.setProperty('CamelExceptionCaught', new Exception(code + ': x'))
        m = script('ClassifyError').processData(m)
        assert m.getProperty('ei.result') == 'EXCEPTION_QUEUED' && m.getHeader('CamelHttpResponseCode', Integer) == 202, code
    }
}

println "\n${passed} passed, ${failures.size()} failed"
if (failures) { println "Failed: ${failures}"; System.exit(1) }
