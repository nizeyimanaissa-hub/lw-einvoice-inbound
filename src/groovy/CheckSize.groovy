import com.sap.gateway.ip.core.customdev.util.Message

// Step 2 of the core flow. Property ei.maxMB comes from the externalised parameter LW_MaxPayloadMB
// (set in the "Init" Content Modifier as ${LW_MaxPayloadMB}). Default 10.
// Error convention: every business error is an Exception whose message starts with its EI_* code,
// because Cloud Integration cannot share a custom exception class between script steps.
def Message processData(Message message) {
    def raw = message.getProperty('ei.maxMB')
    double maxMB = raw ? Double.parseDouble(raw.toString().trim()) : 10d
    byte[] body = message.getBody(byte[].class) ?: new byte[0]
    if (body.length > maxMB * 1024 * 1024) {
        throw new Exception(String.format(Locale.ROOT, 'EI_TOO_LARGE: %.1f MB exceeds the limit of %.0f MB', body.length / 1048576d, maxMB))
    }
    message.setProperty('ei.sizeBytes', body.length)
    return message
}
