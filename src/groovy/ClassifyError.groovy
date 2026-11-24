import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

// Exception subprocess (first version for Phase 4; Phase 6 adds HTTP/OData errors, retries and mails).
// Reads the caught exception, sets ei.result, ei.code, ei.reason and answers the HTTPS caller:
//   REJECTED -> 422, EXCEPTION_QUEUED -> 202, anything else is technical -> 500 (retried in Phase 6).
def Message processData(Message message) {
    final Set<String> REJECTED = ['EI_UNKNOWN_FORMAT', 'EI_PDF_WITHOUT_XML', 'EI_PROFILE_NOT_INVOICE', 'EI_PROFILE_UNKNOWN',
                                  'EI_SCHEMA', 'EI_RULES', 'EI_TOO_LARGE'] as Set
    final Set<String> QUEUED = ['EI_SUPPLIER_UNKNOWN', 'EI_VM_MISSING', 'EI_NO_PO_MANUAL', 'EI_CREDIT_NOTE_MANUAL',
                                'EI_SUPPLIER_AMBIGUOUS', 'EI_FIELD_TOO_LONG',
                                'ST_PO_MISMATCH', 'ST_DUPLICATE', 'ST_BALANCE', 'ST_TAXCODE'] as Set
    def ex = message.getProperty('CamelExceptionCaught')
    String text = ex != null ? String.valueOf(ex.getMessage()) : ''
    // Cloud Integration wraps script exceptions: "java.lang.Exception: java.lang.Exception: EI_X: reason@ line 16 in Foo.groovy".
    // Look for the code anywhere in the message or its causes, as "EI_..:" / "ST_..:" at a word start, and cut the wrapper off the reason.
    String code = null
    String reason = null
    for (Throwable t = ex as Throwable; t != null && code == null; t = (t.getCause() != t ? t.getCause() : null)) {
        def m = (String.valueOf(t.getMessage()) =~ /(?:^|[\s:])((?:EI|ST)_[A-Z0-9_]+):\s*(.*)$/)
        if (m.find()) { code = m.group(1); reason = m.group(2).replaceFirst(/\s*@ line \d+ in \S+\s*$/, '').trim() }
    }
    // the XML Validator step throws its own exception type: no EI_ code in its message
    if (code == null && ex != null && (ex.getClass().getName().toLowerCase().contains('validat') || text.toLowerCase().contains('xml validation'))) {
        code = 'EI_SCHEMA'
    }
    String result = REJECTED.contains(code) ? 'REJECTED' : (QUEUED.contains(code) ? 'EXCEPTION_QUEUED' : 'TECHNICAL')
    if (reason == null) reason = text
    message.setProperty('ei.result', result)
    message.setProperty('ei.code', code ?: 'EI_TECHNICAL')
    message.setProperty('ei.reason', reason)
    def body = [result: result, code: code ?: 'EI_TECHNICAL']
    String details = message.getProperty('ei.details') as String
    body.details = details ? new groovy.json.JsonSlurper().parseText(details) : [reason]
    message.setBody(JsonOutput.toJson(body))
    message.setHeader('Content-Type', 'application/json')
    message.setHeader('CamelHttpResponseCode', result == 'REJECTED' ? 422 : (result == 'EXCEPTION_QUEUED' ? 202 : 500))
    return message
}
