#!/usr/bin/env bash
# Runs the official KoSIT Schematron XSLTs (EN 16931 and XRechnung) with Saxon on every test invoice,
# writes the SVRL reports to build/svrl/ and checks the fatal rule IDs per test case.
set -uo pipefail
cd "$(dirname "$0")/../.."
J=.tools/jars; R=.tools/kosit/resources; O=build/svrl; mkdir -p $O
CP="$J/Saxon-HE-12.5.jar:$J/xmlresolver-5.2.2.jar:$J/xmlresolver-5.2.2-data.jar"
# case file : expected fatal rules (space separated, "-" = none)
CASES=("TC01_xrechnung_ubl_po:-" "TC02_xrechnung_cii_po:-" "TC05_missing_buyer_reference:BR-DE-15" "TC06_wrong_payable_amount:BR-CO-16")
fail=0
for c in "${CASES[@]}"; do
  tc=${c%%:*}; want=${c##*:}; f=test/invoices/$tc.xml
  if grep -q CrossIndustryInvoice "$f"; then K=cii/16b/xsl/EN16931-CII-validation.xsl; X=xrechnung/3.0.2/xsl/XRechnung-CII-validation.xsl
  else K=ubl/2.1/xsl/EN16931-UBL-validation.xsl; X=xrechnung/3.0.2/xsl/XRechnung-UBL-validation.xsl; fi
  java -cp "$CP" net.sf.saxon.Transform -s:"$f" -xsl:$R/$K -o:$O/$tc.en.svrl 2>/dev/null
  java -cp "$CP" net.sf.saxon.Transform -s:"$f" -xsl:$R/$X -o:$O/$tc.xr.svrl 2>/dev/null
  got=$(python3 - $O/$tc.en.svrl $O/$tc.xr.svrl <<'PY'
import sys, xml.etree.ElementTree as ET
ns = '{http://purl.oclc.org/dsdl/svrl}'
ids = sorted({e.get('id') for p in sys.argv[1:] for e in ET.parse(p).iter(ns + 'failed-assert') if e.get('flag') in (None, 'fatal')})
print(' '.join(ids))
PY
)
  [ -z "$got" ] && got="-"
  if [ "$got" == "$want" ]; then echo "ok   $tc  fatal: $got"; else echo "FAIL $tc  expected: $want  got: $got"; fail=1; fi
done
exit $fail
