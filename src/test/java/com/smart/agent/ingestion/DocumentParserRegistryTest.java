package com.smart.agent.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentParserRegistryTest {

    private final DocumentParserRegistry registry = DocumentParserRegistry.defaults();
    private final ParseLimits limits = new ParseLimits(2_000_000, 20_000, 10, 1_000, 5_000_000);

    @Test
    void detectsSupportedTypesFromBytesAndDoesNotTrustAnUnknownExtension() throws Exception {
        assertParsed("document.bin", pdf("Page one"), DetectedContentType.PDF);
        assertParsed("document.docx", docx("Project procedure"), DetectedContentType.DOCX);
        assertParsed("document.xlsx", xlsx(), DetectedContentType.XLSX);
        assertParsed("README", "# Safety\n\nWear a helmet".getBytes(StandardCharsets.UTF_8),
                DetectedContentType.MARKDOWN);
        assertParsed("notes.data", "plain notes\nsecond line".getBytes(StandardCharsets.UTF_8),
                DetectedContentType.TEXT);
    }

    @Test
    void treatsCommonImagesAsMetadataOnlyDocuments() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", output);

        DocumentParseResult result = parse("photo.png", output.toByteArray(), limits);

        assertThat(result.status()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(result.detectedType()).isEqualTo(DetectedContentType.IMAGE);
        assertThat(result.blocks()).isEmpty();
        assertThat(result.metadata()).containsEntry("Content-Type", "image/png");
        assertThat(result.metadata()).containsEntry("image:width", "3");
        assertThat(result.metadata()).containsEntry("image:height", "2");
    }

    @Test
    void marksExecutablesAndDangerousFilenameContentMismatchesForQuarantine() throws Exception {
        DocumentParseResult executable = parse("report.pdf", new byte[] {'M', 'Z', 0, 0, 0, 0}, limits);
        DocumentParseResult mismatch = parse("report.txt", pdf("not text"), limits);

        assertThat(executable.status()).isEqualTo(DocumentParseStatus.QUARANTINED);
        assertThat(executable.failureCode()).isEqualTo("EXECUTABLE_CONTENT");
        assertThat(mismatch.status()).isEqualTo(DocumentParseStatus.QUARANTINED);
        assertThat(mismatch.failureCode()).isEqualTo("CONTENT_TYPE_MISMATCH");
    }

    @Test
    void marksUnknownBinaryAsUnsupported() throws Exception {
        DocumentParseResult result = parse("payload.bin", new byte[] {0, 1, 2, 3, 4, 5, 0, 9}, limits);

        assertThat(result.status()).isEqualTo(DocumentParseStatus.UNSUPPORTED);
        assertThat(result.detectedType()).isEqualTo(DetectedContentType.UNKNOWN);
        assertThat(result.failureCode()).isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void preservesPageSheetAndSectionLocations() throws Exception {
        DocumentParseResult pdf = parse("pages.pdf", pdf("First page", "Second page"), limits);
        DocumentParseResult workbook = parse("costs.xlsx", xlsx(), limits);
        DocumentParseResult markdown = parse("guide.md",
                "# Safety\nInspect equipment.\n\n## PPE\nWear a helmet.".getBytes(StandardCharsets.UTF_8), limits);

        assertThat(pdf.blocks()).extracting(ParsedTextBlock::pageNumber).containsExactly(1, 2);
        assertThat(workbook.blocks()).extracting(ParsedTextBlock::sheetName)
                .containsExactly("Summary", "Details");
        assertThat(markdown.blocks()).extracting(ParsedTextBlock::sectionTitle)
                .containsExactly("Safety", "PPE");
    }

    @Test
    void enforcesPageCharacterAndExpandedArchiveLimits() throws Exception {
        assertThatThrownBy(() -> parse("pages.pdf", pdf("one", "two"),
                new ParseLimits(2_000_000, 20_000, 1, 1_000, 5_000_000)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> parse("notes.txt", "123456".getBytes(StandardCharsets.UTF_8),
                new ParseLimits(2_000_000, 5, 10, 1_000, 5_000_000)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("character");
        assertThatThrownBy(() -> parse("document.docx", docx("content"),
                new ParseLimits(2_000_000, 20_000, 10, 1, 5_000_000)))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("archive entr");
    }

    private void assertParsed(String filename, byte[] bytes, DetectedContentType type) throws Exception {
        DocumentParseResult result = parse(filename, bytes, limits);
        assertThat(result.status()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(result.detectedType()).isEqualTo(type);
    }

    private DocumentParseResult parse(String filename, byte[] bytes, ParseLimits parseLimits) {
        return registry.parse(filename, new ByteArrayInputStream(bytes), parseLimits);
    }

    private static byte[] pdf(String... pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(text);
                    stream.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            try (PDDocument ignored = Loader.loadPDF(output.toByteArray())) {
                return output.toByteArray();
            }
        }
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] xlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Cell summary = workbook.createSheet("Summary").createRow(0).createCell(0);
            summary.setCellValue("Total");
            Cell formula = workbook.getSheet("Summary").createRow(1).createCell(0);
            formula.setCellFormula("1+1");
            workbook.createSheet("Details").createRow(0).createCell(0).setCellValue("Line item");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
