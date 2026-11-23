#!/usr/bin/env bash
# Downloads the local test tools into .tools/ (git-ignored): Saxon-HE (XSLT), Groovy, PDFBox,
# and the official KoSIT XRechnung validator configuration 3.0.2 (release 2024-06-20, ADR 0003).
set -euo pipefail
cd "$(dirname "$0")/.."
T=.tools; M=https://repo1.maven.org/maven2
mkdir -p $T/jars $T/kosit
get() { [ -f "$T/jars/$(basename "$1")" ] || curl -sfL -o "$T/jars/$(basename "$1")" "$M/$1"; }
get net/sf/saxon/Saxon-HE/12.5/Saxon-HE-12.5.jar
get org/xmlresolver/xmlresolver/5.2.2/xmlresolver-5.2.2.jar
get org/xmlresolver/xmlresolver/5.2.2/xmlresolver-5.2.2-data.jar
get org/apache/groovy/groovy/4.0.22/groovy-4.0.22.jar
get org/apache/groovy/groovy-xml/4.0.22/groovy-xml-4.0.22.jar
get org/apache/groovy/groovy-json/4.0.22/groovy-json-4.0.22.jar
get org/apache/pdfbox/pdfbox/2.0.30/pdfbox-2.0.30.jar
get org/apache/pdfbox/fontbox/2.0.30/fontbox-2.0.30.jar
get commons-logging/commons-logging/1.2/commons-logging-1.2.jar
if [ ! -d $T/kosit/resources ]; then
  curl -sfL -o $T/kosit.zip https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/release-2024-06-20/validator-configuration-xrechnung_3.0.2_2024-06-20.zip
  unzip -q -o $T/kosit.zip -d $T/kosit && rm $T/kosit.zip
fi
echo "tools ready in $T"
