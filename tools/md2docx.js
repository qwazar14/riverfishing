// Markdown -> .docx for the River Fishing research reference.
//
// Neither pandoc nor LibreOffice is on this machine, so the document is built with docx-js directly.
// The input is a known shape — my own generator wrote it — so this handles exactly what that generator
// emits and nothing more: h1/h2/h3, bold labels, `code` spans, [text](url) links, bullet lists and
// `---` rules. Anything unexpected still renders, as plain text, rather than being dropped.
//
//   node md2docx.js <in.md> <out.docx>

const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, ExternalHyperlink,
  AlignmentType, BorderStyle, LevelFormat, convertInchesToTwip,
} = require("docx");

const [, , IN, OUT] = process.argv;
if (!IN || !OUT) {
  console.error("usage: node md2docx.js <in.md> <out.docx>");
  process.exit(1);
}

const MONO = "Consolas";
const BODY = "Georgia";      // a reading face: this is 300 pages of prose, not a spec sheet
const HEAD = "Segoe UI";

/** Split one line of markdown into styled runs. Order matters: links first, they contain other syntax. */
function runs(text, base = {}) {
  const out = [];
  // Three link shapes appear in this document, and only two should become real hyperlinks:
  //   [label](http…)  -> a link on the label
  //   a bare http…    -> a link on itself; the source list is 328 of these, so this is the important one
  //   [label](#anchor) -> in-document anchor. Word navigates by heading, so render the label as plain
  //                       text rather than emit a dead link.
  const linkRe = /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)|\[([^\]]+)\]\(#[^)]*\)|(https?:\/\/[^\s)\],]+)/g;
  let last = 0, m;
  while ((m = linkRe.exec(text)) !== null) {
    if (m.index > last) out.push(...inline(text.slice(last, m.index), base));
    if (m[2]) {
      out.push(new ExternalHyperlink({
        link: m[2],
        children: [new TextRun({ ...base, text: m[1], style: "Hyperlink" })],
      }));
    } else if (m[3]) {
      out.push(...inline(m[3], base));
    } else {
      out.push(new ExternalHyperlink({
        link: m[4],
        children: [new TextRun({ ...base, text: m[4], style: "Hyperlink", size: 18, font: MONO })],
      }));
    }
    last = linkRe.lastIndex;
  }
  if (last < text.length) out.push(...inline(text.slice(last), base));
  return out.length ? out : [new TextRun({ ...base, text: "" })];
}

/** **bold** and `code`, applied to a link-free fragment. */
function inline(text, base) {
  const out = [];
  // One pass over an alternation, so bold and code cannot swallow each other.
  const re = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0, m;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) out.push(new TextRun({ ...base, text: text.slice(last, m.index) }));
    const tok = m[1];
    if (tok.startsWith("**")) {
      out.push(new TextRun({ ...base, text: tok.slice(2, -2), bold: true }));
    } else {
      out.push(new TextRun({ ...base, text: tok.slice(1, -1), font: MONO, size: 19 }));
    }
    last = re.lastIndex;
  }
  if (last < text.length) out.push(new TextRun({ ...base, text: text.slice(last) }));
  return out;
}

const RULE = {
  border: { bottom: { style: BorderStyle.SINGLE, size: 6, space: 8, color: "B9B2A4" } },
  spacing: { before: 240, after: 240 },
};

function build(md) {
  const lines = md.split(/\r?\n/);
  const kids = [];
  let pending = [];   // consecutive non-empty body lines form one paragraph

  const flush = () => {
    if (!pending.length) return;
    kids.push(new Paragraph({
      children: runs(pending.join(" "), { font: BODY, size: 21 }),
      spacing: { after: 140, line: 300 },
      alignment: AlignmentType.JUSTIFIED,
    }));
    pending = [];
  };

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, "");
    if (!line.trim()) { flush(); continue; }

    if (/^---+$/.test(line.trim())) {
      flush();
      kids.push(new Paragraph({ ...RULE, children: [new TextRun("")] }));
      continue;
    }
    let m;
    if ((m = line.match(/^(#{1,3})\s+(.*)$/))) {
      flush();
      const depth = m[1].length;
      const txt = m[2];
      if (depth === 1) {
        kids.push(new Paragraph({
          children: runs(txt, { font: HEAD, size: 40, bold: true, color: "1F3A5F" }),
          heading: HeadingLevel.TITLE, spacing: { after: 300 },
        }));
      } else if (depth === 2) {
        kids.push(new Paragraph({
          children: runs(txt, { font: HEAD, size: 30, bold: true, color: "1F3A5F" }),
          heading: HeadingLevel.HEADING_1, pageBreakBefore: true,
          spacing: { before: 120, after: 200 },
        }));
      } else {
        kids.push(new Paragraph({
          children: runs(txt, { font: HEAD, size: 24, bold: true, color: "36566E" }),
          heading: HeadingLevel.HEADING_2, spacing: { before: 260, after: 120 },
          keepNext: true,
        }));
      }
      continue;
    }
    if ((m = line.match(/^\s*[-*]\s+(.*)$/))) {
      flush();
      kids.push(new Paragraph({
        children: runs(m[1], { font: BODY, size: 20 }),
        numbering: { reference: "bullets", level: 0 },
        spacing: { after: 60 },
      }));
      continue;
    }
    if (/^\s*\*[^*].*\*\s*$/.test(line)) {           // *32 идеи.* — the section's own count line
      flush();
      kids.push(new Paragraph({
        children: [new TextRun({ font: BODY, size: 20, italics: true, color: "6B6357",
                                 text: line.trim().replace(/^\*|\*$/g, "") })],
        spacing: { after: 200 },
      }));
      continue;
    }
    pending.push(line.trim());
  }
  flush();
  return kids;
}

const md = fs.readFileSync(IN, "utf8");
const doc = new Document({
  creator: "River Fishing",
  title: "Исследование к 0.7.0",
  description: "Идеи из рыболовных игр, модов и реальной рыбалки",
  numbering: {
    config: [{
      reference: "bullets",
      levels: [{
        level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: convertInchesToTwip(0.3), hanging: convertInchesToTwip(0.18) } } },
      }],
    }],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },            // A4 portrait, in DXA
        margin: { top: 1134, bottom: 1134, left: 1418, right: 1134 },
      },
    },
    children: build(md),
  }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(OUT, buf);
  const n = (md.match(/^### /gm) || []).length;
  const s = (md.match(/^## /gm) || []).length;
  console.log(`-> ${OUT}  ${(buf.length / 1024).toFixed(0)} KB`);
  console.log(`   ${s} разделов, ${n} идей, ${md.split(/\r?\n/).length} строк исходника`);
});
