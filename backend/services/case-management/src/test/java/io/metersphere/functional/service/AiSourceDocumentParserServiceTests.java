package io.metersphere.functional.service;

import io.metersphere.functional.domain.AiSourceDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSourceDocumentParserServiceTests {
    private final AiSourceDocumentParserService parser = new AiSourceDocumentParserService();

    @Test
    void extractsPdfText() throws Exception {
        byte[] content;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("MeterSphere PDF requirement");
                stream.endText();
            }
            pdf.save(output);
            content = output.toByteArray();
        }
        assertTrue(parser.extractWithTika(content, document("requirement.pdf", "application/pdf"))
                .contains("MeterSphere PDF requirement"));
    }

    @Test
    void extractsDocxText() throws Exception {
        byte[] content;
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            docx.createParagraph().createRun().setText("MeterSphere Office requirement");
            docx.write(output);
            content = output.toByteArray();
        }
        assertTrue(parser.extractWithTika(content, document("requirement.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contains("MeterSphere Office requirement"));
    }

    @Test
    void createsOverlappingSemanticChunks() {
        String text = "# Requirement A\n" + "a".repeat(1900) + "\n# Requirement B\n" + "b".repeat(1900);
        var sections = parser.splitSections(text);
        assertTrue(sections.size() >= 3);
        assertEquals(0, sections.getFirst().getStart());
        assertTrue(sections.get(1).getStart() < sections.getFirst().getEnd());
    }

    @Test
    void rejectsEicarSignature() {
        DefaultAiDocumentVirusScanner scanner = new DefaultAiDocumentVirusScanner();
        assertThrows(RuntimeException.class, () -> scanner.scan("sample.txt",
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*".getBytes()));
    }

    private AiSourceDocument document(String name, String mime) {
        AiSourceDocument document = new AiSourceDocument();
        document.setOriginalName(name);
        document.setMimeType(mime);
        return document;
    }
}
