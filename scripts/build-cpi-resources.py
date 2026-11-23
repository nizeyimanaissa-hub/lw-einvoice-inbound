#!/usr/bin/env python3
"""Builds build/cpi-resources/ for upload into the core iFlow (Phase 4, steps 6 and 7).

Cloud Integration keeps XSDs as flat files and resolves imports by file name, while the KoSIT XSDs import
each other by relative path. This script follows the imports of the two main XSDs, copies every file flat
and rewrites each schemaLocation to the bare file name. It also copies the four Schematron XSLTs the
official validator uses (scenarios.xml of the KoSIT configuration), not the top-level *.xslt files.
"""
import re, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CFG = ROOT / ".tools/kosit/resources"
OUT = ROOT / "build/cpi-resources"
MAIN_XSDS = {"ubl": CFG / "ubl/2.1/xsd/maindoc/UBL-Invoice-2.1.xsd", "cii": CFG / "cii/16b/xsd/CrossIndustryInvoice_100pD16B.xsd"}
XSLTS = [CFG / "ubl/2.1/xsl/EN16931-UBL-validation.xsl", CFG / "cii/16b/xsl/EN16931-CII-validation.xsl",
         CFG / "xrechnung/3.0.2/xsl/XRechnung-UBL-validation.xsl", CFG / "xrechnung/3.0.2/xsl/XRechnung-CII-validation.xsl"]
LOC = re.compile(r'(schemaLocation\s*=\s*")([^"]+)(")')

def closure(main: Path) -> dict:
    files, todo = {}, [main]
    while todo:
        f = todo.pop()
        if f in files: continue
        text = f.read_text(encoding="utf-8")
        files[f] = text
        for m in LOC.finditer(text):
            if m.group(2).startswith("http"): sys.exit(f"{f.name} imports a remote schema: {m.group(2)}")
            todo.append((f.parent / m.group(2)).resolve())
    return files

if not CFG.exists(): sys.exit("run scripts/fetch-tools.sh first")
shutil.rmtree(OUT, ignore_errors=True)
for kind, main in MAIN_XSDS.items():
    d = OUT / f"xsd-{kind}"; d.mkdir(parents=True)
    files = closure(main.resolve())
    names = [f.name for f in files]
    assert len(names) == len(set(names)), f"file name clash in {kind}: {names}"
    for f, text in files.items():
        (d / f.name).write_text(LOC.sub(lambda m: m.group(1) + Path(m.group(2)).name + m.group(3), text), encoding="utf-8")
    print(f"xsd-{kind}: main {main.name}, {len(files)} files")
x = OUT / "xslt"; x.mkdir()
for f in XSLTS:
    shutil.copy(f, x / f.name); print(f"xslt: {f.name} ({f.stat().st_size // 1024} KB)")
