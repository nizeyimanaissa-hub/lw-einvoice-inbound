import com.sap.gateway.ip.core.customdev.util.Message

// Step 5b ("Profile allowed?"). The allow-list is an exact-match map, not a substring test: the ZUGFeRD
// BASIC and EXTENDED IDs also START with "urn:cen.eu:en16931:2017", so a prefix or contains() test would
// accept them (and anything else) by accident instead of by rule.
// Sets ei.mode = EN16931 (EN 16931 Schematron) or XRECHNUNG (EN 16931 + XRechnung Schematron),
// or throws the rejecting error. Throwing replaces the router of the guide: every non-accepted outcome
// ends in the exception subprocess anyway, and the logic stays unit-testable.
//
// Full specification IDs (the guide's table abbreviates the ZUGFeRD ones):
//   XRechnung 3.0          urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0
//   XRechnung 3.0 ext.     ...#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0 (appended to the above)
//   ZUGFeRD EN 16931       urn:cen.eu:en16931:2017
//   ZUGFeRD BASIC          urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic
//   ZUGFeRD EXTENDED       urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended
//   ZUGFeRD MINIMUM        urn:factur-x.eu:1p0:minimum              (not an invoice, rejected)
//   ZUGFeRD BASIC WL       urn:factur-x.eu:1p0:basicwl              (not an invoice, rejected)
def Message processData(Message message) {
    String mode = decide(message.getProperty('ei.profile') as String, message.getProperty('ei.syntax') as String,
                         message.getProperty('ei.typeCode') as String)
    message.setProperty('ei.mode', mode)
    return message
}

def String decide(String profile, String syntax, String typeCode) {
    final String XR = 'urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0'
    final Map<String, String> ALLOWED = [
        (XR)                                                                                    : 'XRECHNUNG',
        (XR + '#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0')                      : 'XRECHNUNG',
        'urn:cen.eu:en16931:2017'                                                               : 'EN16931',
        'urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic'                           : 'EN16931',
        'urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended'                       : 'EN16931'
    ]
    final List<String> NOT_INVOICES = ['urn:factur-x.eu:1p0:minimum', 'urn:factur-x.eu:1p0:basicwl']
    String id = (profile ?: '').trim()
    if (NOT_INVOICES.contains(id)) {
        throw new Exception('EI_PROFILE_NOT_INVOICE: profile ' + id + ' is not a valid e-invoice (BMF)')
    }
    String mode = ALLOWED[id]
    if (mode == null) {
        throw new Exception('EI_PROFILE_UNKNOWN: specification ID "' + id + '" is not accepted')
    }
    if (syntax == 'UBL_CN' || typeCode == '381') {
        throw new Exception('EI_CREDIT_NOTE_MANUAL: credit notes (381) are handled manually in v1')
    }
    return mode
}
