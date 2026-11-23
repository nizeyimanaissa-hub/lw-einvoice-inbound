import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import javax.xml.XMLConstants
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

// Step 8. Reads the SVRL reports kept by the flow (property ei.svrlEn always, ei.svrlXr in XRECHNUNG mode),
// collects every failed-assert with flag "fatal" (an assert without a flag counts as fatal, the safe side),
// and throws EI_RULES with the list in property ei.details (JSON array "BR-DE-15 text"). Warnings go to the
// message log only. Finally the invoice XML is restored from ei.xml as the body.
//
// Why two SVRLs: the EN 16931 and the XRechnung Schematron each run on the ORIGINAL invoice. Chained XSLT
// steps would feed the first report into the second, so the flow stashes each report and restores the body.
def Message processData(Message message) {
    List<String> fatal = []
    List<String> warnings = []
    ['ei.svrlEn', 'ei.svrlXr'].each { String prop ->
        String svrl = message.getProperty(prop) as String
        if (svrl) collect(svrl, fatal, warnings)
    }
    def log = (binding.hasVariable('messageLogFactory') && messageLogFactory != null) ? messageLogFactory.getMessageLog(message) : null
    if (log != null && warnings) log.setStringProperty('ei.warnings', warnings.join(' | '))
    String xml = message.getProperty('ei.xml') as String
    if (xml != null) message.setBody(xml)
    if (fatal) {
        message.setProperty('ei.details', JsonOutput.toJson(fatal))
        throw new Exception('EI_RULES: ' + fatal.size() + ' fatal rule violation(s): ' + fatal.collect { it.split(' ')[0] }.join(', '))
    }
    return message
}

def collect(String svrl, List<String> fatal, List<String> warnings) {
    def f = DocumentBuilderFactory.newInstance()
    f.setNamespaceAware(true)
    f.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
    def doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(svrl)))
    def xp = XPathFactory.newInstance().newXPath()
    xp.setNamespaceContext(new NamespaceContext() {
        String getNamespaceURI(String p) { p == 'svrl' ? 'http://purl.oclc.org/dsdl/svrl' : XMLConstants.NULL_NS_URI }
        String getPrefix(String u) { null }
        Iterator getPrefixes(String u) { [].iterator() }
    })
    NodeList asserts = xp.evaluate('//svrl:failed-assert', doc, XPathConstants.NODESET) as NodeList
    for (int i = 0; i < asserts.getLength(); i++) {
        def a = asserts.item(i)
        String flag = a.getAttributes().getNamedItem('flag')?.getNodeValue()
        String id = a.getAttributes().getNamedItem('id')?.getNodeValue() ?: '?'
        String text = (xp.evaluate('svrl:text', a, XPathConstants.STRING) as String).replaceAll(/\s+/, ' ').trim()
        // the XSLTs already prefix the text with "[BR-xx]"; do not repeat the id
        String line = text.startsWith('[' + id + ']') ? text : (id + ' ' + text)
        line = line.replaceFirst(/^\[([^\]]+)\]\s*-?\s*/, '$1 ')
        if (flag == null || flag == 'fatal') fatal << line else warnings << line
    }
}
