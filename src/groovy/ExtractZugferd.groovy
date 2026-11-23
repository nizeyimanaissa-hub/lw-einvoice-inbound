import com.sap.gateway.ip.core.customdev.util.Message
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification

// Step 4. No-op for XML. For a PDF (body starts with %PDF) the embedded invoice XML replaces the body.
// Needs the PDFBox 2.0.x JARs (pdfbox, fontbox) as iFlow resources. Sets ei.source = XML | PDF.
def Message processData(Message message) {
    byte[] body = message.getBody(byte[].class) ?: new byte[0]
    boolean pdf = body.length >= 4 && body[0] == 0x25 && body[1] == 0x50 && body[2] == 0x44 && body[3] == 0x46 // %PDF
    if (!pdf) {
        message.setProperty('ei.source', 'XML')
        return message
    }
    message.setProperty('ei.source', 'PDF')
    final List<String> WANTED = ['factur-x.xml', 'zugferd-invoice.xml', 'xrechnung.xml']
    PDDocument doc = null
    try {
        doc = PDDocument.load(body)
        Map<String, PDComplexFileSpecification> found = new LinkedHashMap<String, PDComplexFileSpecification>()
        collect(doc.getDocumentCatalog().getNames()?.getEmbeddedFiles(), found)
        COSArray af = doc.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.getPDFName('AF')) as COSArray
        for (int i = 0; af != null && i < af.size(); i++) {
            def d = af.getObject(i)
            if (d instanceof COSDictionary) {
                def spec = new PDComplexFileSpecification((COSDictionary) d)
                found.put(spec.getFilename() ?: ('af' + i), spec)
            }
        }
        for (String want : WANTED) {
            String key = found.keySet().find { it != null && it.toLowerCase(Locale.ROOT).endsWith(want) }
            def stream = key ? (found[key].getEmbeddedFile() ?: found[key].getEmbeddedFileUnicode()) : null
            if (stream != null) {
                message.setBody(stream.toByteArray())
                message.setProperty('ei.embeddedName', key)
                return message
            }
        }
    } catch (IOException e) {
        throw new Exception('EI_PDF_WITHOUT_XML: the PDF cannot be read (' + e.getMessage() + ')')
    } finally {
        if (doc != null) doc.close()
    }
    throw new Exception('EI_PDF_WITHOUT_XML: no embedded file named ' + WANTED.join(', '))
}

// The embedded-files name tree is either flat (names) or nested (kids).
def collect(node, Map found) {
    if (node == null) return
    node.getNames()?.each { k, v -> found.put(k, v) }
    node.getKids()?.each { kid -> collect(kid, found) }
}
