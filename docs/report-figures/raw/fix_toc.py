"""
Resolves the table of contents to real page numbers.

Because adding the contents list changes pagination, this rebuilds and
re-measures until the page numbers stop moving (a fixed point), the same
thing Word does internally when it updates a TOC field.
"""
import json
import os
import re
import subprocess
import sys

RAW = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.abspath(os.path.join(RAW, "..", ".."))
DOCX = os.path.join(DOCS, "AsteroidsFX-Technical-Report.docx")
PDF = os.path.join(DOCS, "AsteroidsFX-Technical-Report.pdf")
SOFFICE = r"C:\Program Files\LibreOffice\program\soffice.exe"

HEADINGS = [
    ("Abstract", 1), ("1  Introduction", 1), ("1.1  Background and motivation", 2),
    ("1.2  Aim and scope", 2), ("1.3  Structure of this report", 2),
    ("2  Requirements", 1), ("2.1  Functional requirements", 2),
    ("2.2  Non-functional requirements", 2),
    ("2.3  Mandated components and their interfaces", 2),
    ("3  Analysis", 1), ("3.1  Use cases", 2), ("3.2  Domain model", 2),
    ("3.3  Behavioural model", 2), ("3.4  Identified and missing components", 2),
    ("4  Design", 1), ("4.1  Architectural overview", 2),
    ("4.2  Components and connections", 2), ("4.3  Operation contracts", 2),
    ("4.4  The two-phase frame", 2), ("4.5  Module layers and split packages", 2),
    ("4.6  The scoring boundary", 2),
    ("5  Implementation", 1), ("5.1  Registration and access of components", 2),
    ("5.2  Reliable configuration and strong encapsulation", 2),
    ("5.3  Component models applied", 2),
    ("5.4  Dynamic installation and uninstallation", 2),
    ("5.5  Collision, splitting and scoring", 2), ("5.6  The scoring client", 2),
    ("6  Test", 1), ("6.1  Unit testing strategy", 2),
    ("6.2  Build and unit test results", 2),
    ("6.3  Integration in a real deployment", 2),
    ("6.4  Experimental validation of dynamic update", 2),
    ("6.5  Summary of results", 2),
    ("7  Discussion", 1), ("7.1  How well the design met the requirements", 2),
    ("7.2  Trade-offs accepted", 2), ("7.3  Limitations", 2),
    ("7.4  Reflection on the development process", 2),
    ("8  Conclusion", 1), ("9  References", 1),
    ("Appendix A  Repository map", 1), ("Appendix B  Reproducing the results", 1),
]


def norm(s):
    return re.sub(r"\s+", " ", s).strip().lower()


def build_docx():
    subprocess.run([sys.executable, "report_content.py"], cwd=RAW, check=True,
                   stdout=subprocess.DEVNULL)


def export_pdf():
    if os.path.exists(PDF):
        os.remove(PDF)
    subprocess.run([SOFFICE, "--headless", "--convert-to", "pdf", "--outdir", DOCS, DOCX],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def measure():
    import pypdf
    pages = [p.extract_text() or "" for p in pypdf.PdfReader(PDF).pages]
    # find where the contents list ends, so its own entries are not matched
    body_start = 1
    for i, txt in enumerate(pages):
        if "table of contents" in norm(txt):
            body_start = i + 1
            while body_start < len(pages) and "abstract" not in norm(pages[body_start]):
                body_start += 1
            break
    result = []
    for title, lvl in HEADINGS:
        key = norm(title)
        page = None
        for i in range(body_start, len(pages)):
            if key in norm(pages[i]):
                page = i + 1
                break
        if page is None:
            raise SystemExit(f"heading not found in PDF: {title}")
        result.append([title, lvl, page])
    return result


def main():
    toc_file = os.path.join(RAW, "toc.json")
    previous = json.load(open(toc_file)) if os.path.exists(toc_file) else None

    for attempt in range(1, 7):
        build_docx()
        export_pdf()
        current = measure()
        if current == previous:
            print(f"converged after {attempt} pass(es)")
            import pypdf
            print("final page count:", len(pypdf.PdfReader(PDF).pages))
            return
        json.dump(current, open(toc_file, "w"), indent=0)
        previous = current
        print(f"pass {attempt}: page numbers updated, rebuilding")

    print("did not converge; inspect toc.json")


if __name__ == "__main__":
    main()
