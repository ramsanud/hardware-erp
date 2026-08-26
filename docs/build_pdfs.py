#!/usr/bin/env python3
"""
Renders the Module 1 documentation from Markdown to PDF.

Markdown is the source of truth so the docs stay diffable and reviewable in
git; the PDFs are a build artefact. Run:

    python3 docs/build_pdfs.py

Supported subset, chosen to match what the docs actually use:
  # ## ### headings, paragraphs, - and 1. lists, > callouts, | tables |,
  ``` fenced code ```, `inline code`, **bold**, horizontal rules.

Callout syntax: a blockquote whose first token is a tag.
    > [!SETUP] What you need first
    Tags: SETUP COMMAND SUCCESS IMPORTANT DEPENDENCY TROUBLESHOOTING WHY VERIFY
"""
import pathlib
import re
import sys

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate, Frame, KeepTogether, PageBreak, PageTemplate, Paragraph,
    Spacer, Table, TableStyle,
)

HERE = pathlib.Path(__file__).parent
SRC = HERE / "module-01-authentication-user-management" / "src"
OUT = HERE / "module-01-authentication-user-management" / "pdf"

INK = colors.HexColor("#111827")
MUTED = colors.HexColor("#6B7280")
RULE = colors.HexColor("#E5E7EB")
ACCENT = colors.HexColor("#1D4ED8")
CODE_BG = colors.HexColor("#F3F4F6")

# tag -> (label, accent, background)
CALLOUTS = {
    "SETUP":           ("SETUP",           colors.HexColor("#B45309"), colors.HexColor("#FFFBEB")),
    "COMMAND":         ("RUN THIS",        colors.HexColor("#1D4ED8"), colors.HexColor("#EFF6FF")),
    "SUCCESS":         ("WHAT YOU SHOULD SEE", colors.HexColor("#15803D"), colors.HexColor("#F0FDF4")),
    "IMPORTANT":       ("IMPORTANT",       colors.HexColor("#B91C1C"), colors.HexColor("#FEF2F2")),
    "DEPENDENCY":      ("DEPENDENCY",      colors.HexColor("#C2410C"), colors.HexColor("#FFF7ED")),
    "TROUBLESHOOTING": ("IF IT FAILS",     colors.HexColor("#A16207"), colors.HexColor("#FEFCE8")),
    "WHY":             ("WHY",             colors.HexColor("#6D28D9"), colors.HexColor("#F5F3FF")),
    "VERIFY":          ("VERIFY",          colors.HexColor("#7E22CE"), colors.HexColor("#FAF5FF")),
}


def styles():
    base = getSampleStyleSheet()
    s = {}
    s["title"] = ParagraphStyle("title", parent=base["Title"], fontName="Helvetica-Bold",
                                fontSize=26, leading=31, textColor=INK, alignment=TA_LEFT,
                                spaceAfter=4)
    s["subtitle"] = ParagraphStyle("subtitle", fontName="Helvetica", fontSize=12,
                                   leading=17, textColor=MUTED, spaceAfter=18)
    s["h1"] = ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=17, leading=22,
                             textColor=INK, spaceBefore=18, spaceAfter=8)
    s["h2"] = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=13, leading=17,
                             textColor=INK, spaceBefore=14, spaceAfter=6)
    s["h3"] = ParagraphStyle("h3", fontName="Helvetica-Bold", fontSize=11, leading=15,
                             textColor=ACCENT, spaceBefore=11, spaceAfter=4)
    s["body"] = ParagraphStyle("body", fontName="Helvetica", fontSize=10, leading=15,
                               textColor=INK, spaceAfter=7)
    s["bullet"] = ParagraphStyle("bullet", parent=s["body"], leftIndent=13,
                                 bulletIndent=3, spaceAfter=3)
    s["code"] = ParagraphStyle("code", fontName="Courier", fontSize=8.4, leading=12,
                               textColor=INK)
    s["cell"] = ParagraphStyle("cell", fontName="Helvetica", fontSize=8.6, leading=12,
                               textColor=INK)
    s["cellhead"] = ParagraphStyle("cellhead", parent=s["cell"],
                                   fontName="Helvetica-Bold", textColor=colors.white)
    s["calloutlabel"] = ParagraphStyle("calloutlabel", fontName="Helvetica-Bold",
                                       fontSize=7.6, leading=10)
    s["calloutbody"] = ParagraphStyle("calloutbody", fontName="Helvetica", fontSize=9.4,
                                      leading=13.5, textColor=INK)
    s["calloutcode"] = ParagraphStyle("calloutcode", fontName="Courier", fontSize=8.4,
                                      leading=12, textColor=INK)
    s["footer"] = ParagraphStyle("footer", fontName="Helvetica", fontSize=7.6,
                                 textColor=MUTED)
    return s


S = styles()


def esc(text):
    """Markdown inline -> ReportLab mini-HTML."""
    out = (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
    out = re.sub(r"`([^`]+)`",
                 r'<font face="Courier" size="8.8" backColor="#F3F4F6">\1</font>', out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])", r"<i>\1</i>", out)
    return out


def code_block(lines, style=None, bg=CODE_BG):
    rows = [[Paragraph(
        line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace(" ", "&nbsp;") or "&nbsp;",
        style or S["code"])] for line in lines]
    table = Table(rows, colWidths=[165 * mm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 1.5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 1.5),
        ("LINEBEFORE", (0, 0), (0, -1), 2.5, ACCENT),
    ]))
    return table


def callout(tag, body_lines):
    label, accent, bg = CALLOUTS[tag]
    flow = [Paragraph(f'<font color="{accent.hexval()}">{label}</font>', S["calloutlabel"]),
            Spacer(1, 3)]
    buffer, in_code = [], False
    for line in body_lines:
        if line.strip().startswith("```"):
            if in_code and buffer:
                flow.append(code_block(buffer, S["calloutcode"], colors.white))
                flow.append(Spacer(1, 3))
                buffer = []
            in_code = not in_code
            continue
        if in_code:
            buffer.append(line)
        elif line.strip():
            flow.append(Paragraph(esc(line.strip()), S["calloutbody"]))
    if buffer:
        flow.append(code_block(buffer, S["calloutcode"], colors.white))

    inner = Table([[flow]], colWidths=[165 * mm])
    inner.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("LINEBEFORE", (0, 0), (0, -1), 3, accent),
    ]))
    return inner


def build_table(rows):
    header, body = rows[0], rows[1:]
    data = [[Paragraph(esc(c), S["cellhead"]) for c in header]]
    data += [[Paragraph(esc(c), S["cell"]) for c in row] for row in body]
    columns = len(header)
    width = 165 * mm
    # First column carries names and needs more room than value columns.
    if columns == 2:
        widths = [width * 0.38, width * 0.62]
    elif columns == 3:
        widths = [width * 0.3, width * 0.28, width * 0.42]
    else:
        widths = [width / columns] * columns

    table = Table(data, colWidths=widths, repeatRows=1)
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1F2937")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.4, RULE),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F9FAFB")]),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    return table


def parse(markdown_text):
    flow, lines, i = [], markdown_text.split("\n"), 0
    subtitle_pending = False

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        if stripped == "---":
            flow.append(Spacer(1, 5))
            i += 1
            continue

        if stripped == "<PAGEBREAK>":
            flow.append(PageBreak())
            i += 1
            continue

        if stripped.startswith("```"):
            i += 1
            buf = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            flow.append(code_block(buf))
            flow.append(Spacer(1, 8))
            continue

        callout_match = re.match(r"^>\s*\[!(\w+)\]\s*(.*)$", stripped)
        if callout_match and callout_match.group(1) in CALLOUTS:
            tag = callout_match.group(1)
            buf = [callout_match.group(2)] if callout_match.group(2) else []
            i += 1
            while i < len(lines) and lines[i].strip().startswith(">"):
                buf.append(re.sub(r"^>\s?", "", lines[i].strip()))
                i += 1
            flow.append(callout(tag, buf))
            flow.append(Spacer(1, 9))
            continue

        if stripped.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                raw = lines[i].strip().strip("|")
                if not re.match(r"^[\s\-:|]+$", raw):
                    rows.append([c.strip() for c in raw.split("|")])
                i += 1
            if rows:
                flow.append(build_table(rows))
                flow.append(Spacer(1, 10))
            continue

        if stripped.startswith("# "):
            flow.append(Paragraph(esc(stripped[2:]), S["title"]))
            subtitle_pending = True
            i += 1
            continue

        if stripped.startswith("## "):
            flow.append(Paragraph(esc(stripped[3:]), S["h1"]))
            i += 1
            continue

        if stripped.startswith("### "):
            flow.append(Paragraph(esc(stripped[4:]), S["h2"]))
            i += 1
            continue

        if stripped.startswith("#### "):
            flow.append(Paragraph(esc(stripped[5:]), S["h3"]))
            i += 1
            continue

        bullet = re.match(r"^[-*]\s+(.*)$", stripped)
        if bullet:
            flow.append(Paragraph(esc(bullet.group(1)), S["bullet"], bulletText="\u2022"))
            i += 1
            continue

        numbered = re.match(r"^(\d+)\.\s+(.*)$", stripped)
        if numbered:
            flow.append(Paragraph(esc(numbered.group(2)), S["bullet"],
                                  bulletText=f"{numbered.group(1)}."))
            i += 1
            continue

        if subtitle_pending:
            flow.append(Paragraph(esc(stripped), S["subtitle"]))
            subtitle_pending = False
        else:
            flow.append(Paragraph(esc(stripped), S["body"]))
        i += 1

    return flow


def render(md_path, pdf_path):
    text = md_path.read_text()
    title = next((l[2:].strip() for l in text.split("\n") if l.startswith("# ")),
                 md_path.stem)

    doc = BaseDocTemplate(
        str(pdf_path), pagesize=A4,
        leftMargin=22 * mm, rightMargin=22 * mm,
        topMargin=18 * mm, bottomMargin=18 * mm,
        title=title, author="Hardware ERP", subject="Module 1 documentation")

    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="body")

    def decorate(canvas, document):
        canvas.saveState()
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.4)
        y = doc.bottomMargin - 6 * mm
        canvas.line(doc.leftMargin, y, doc.leftMargin + doc.width, y)
        canvas.setFont("Helvetica", 7.6)
        canvas.setFillColor(MUTED)
        canvas.drawString(doc.leftMargin, y - 5 * mm,
                          f"Hardware ERP - Module 1 - {md_path.stem}")
        canvas.drawRightString(doc.leftMargin + doc.width, y - 5 * mm,
                               f"Page {document.page}")
        canvas.restoreState()

    doc.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=decorate)])
    doc.build(parse(text))
    return pdf_path


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sources = sorted(SRC.glob("*.md"))
    if not sources:
        print(f"No markdown found in {SRC}")
        return 1
    for md in sources:
        pdf = OUT / f"{md.stem}.pdf"
        render(md, pdf)
        print(f"  {pdf.name:52} {pdf.stat().st_size // 1024:>5} KB")
    print(f"\n{len(sources)} PDF(s) written to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
