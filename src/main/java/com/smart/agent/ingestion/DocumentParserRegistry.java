package com.smart.agent.ingestion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public final class DocumentParserRegistry {
    private final ContentTypeDetector detector;
    private final List<DocumentParser> parsers;

    public DocumentParserRegistry(ContentTypeDetector detector, List<DocumentParser> parsers) {
        if (detector == null || parsers == null) {
            throw new IllegalArgumentException("detector and parsers are required");
        }
        this.detector = detector;
        this.parsers = List.copyOf(parsers);
    }

    public static DocumentParserRegistry defaults() {
        return new DocumentParserRegistry(new ContentTypeDetector(), List.of(
                new PdfParser(), new DocxParser(), new XlsxParser(),
                new MarkdownParser(), new TextParser(), new ImageParser()));
    }

    public DocumentParseResult parse(String filename, InputStream input, ParseLimits limits) {
        if (input == null || limits == null) {
            throw new IllegalArgumentException("input and limits are required");
        }
        byte[] bytes = readBounded(input, limits.maxSourceBytes());
        ContentTypeDetector.Detection detection = detector.detect(filename, bytes);
        Map<String, String> baseMetadata = Map.of("Content-Type", detection.mediaType());
        if (detection.quarantined()) {
            return new DocumentParseResult(DocumentParseStatus.QUARANTINED, detection.type(), detection.mediaType(),
                    List.of(), baseMetadata, detection.failureCode());
        }
        if (detection.type() == DetectedContentType.UNKNOWN) {
            return new DocumentParseResult(DocumentParseStatus.UNSUPPORTED, detection.type(), detection.mediaType(),
                    List.of(), baseMetadata, "UNSUPPORTED_TYPE");
        }
        if (detection.type() == DetectedContentType.DOCX || detection.type() == DetectedContentType.XLSX) {
            inspectOfficeArchive(bytes, limits);
        }
        DocumentParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(detection.type()))
                .findFirst()
                .orElse(null);
        if (parser == null) {
            return new DocumentParseResult(DocumentParseStatus.UNSUPPORTED, detection.type(), detection.mediaType(),
                    List.of(), baseMetadata, "UNSUPPORTED_TYPE");
        }
        ParsedDocument parsed = parser.parse(new ByteArrayInputStream(bytes), limits);
        Map<String, String> metadata = new LinkedHashMap<>(parsed.metadata());
        metadata.put("Content-Type", detection.mediaType());
        return new DocumentParseResult(DocumentParseStatus.PARSED, detection.type(), detection.mediaType(),
                parsed.blocks(), metadata, null);
    }

    private static byte[] readBounded(InputStream input, long maximum) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximum) {
                    throw new DocumentParseException("SOURCE_TOO_LARGE", "Source byte limit exceeded");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new DocumentParseException("SOURCE_READ_FAILED", "Source content could not be read", exception);
        }
    }

    private static void inspectOfficeArchive(byte[] bytes, ParseLimits limits) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[16 * 1024];
            int entries = 0;
            long expanded = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > limits.maxArchiveEntries()) {
                    throw new DocumentParseException("ARCHIVE_ENTRY_LIMIT", "Office archive entry limit exceeded");
                }
                if (entry.getSize() > 0 && expanded + entry.getSize() > limits.maxExpandedBytes()) {
                    throw new DocumentParseException("ARCHIVE_EXPANSION_LIMIT", "Office archive expanded byte limit exceeded");
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > limits.maxExpandedBytes()) {
                        throw new DocumentParseException(
                                "ARCHIVE_EXPANSION_LIMIT", "Office archive expanded byte limit exceeded");
                    }
                }
                zip.closeEntry();
            }
        } catch (DocumentParseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentParseException("INVALID_OFFICE_ARCHIVE", "Office archive validation failed", exception);
        }
    }

    private abstract static class TypedParser implements DocumentParser {
        private final DetectedContentType type;

        private TypedParser(DetectedContentType type) {
            this.type = type;
        }

        @Override
        public boolean supports(DetectedContentType candidate) {
            return type == candidate;
        }

        ParsedDocument guarded(Supplier<ParsedDocument> operation) {
            try {
                return operation.get();
            } catch (DocumentParseException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new DocumentParseException("PARSER_FAILED", "Document parser failed", exception);
            }
        }
    }

    private static final class PdfParser extends TypedParser {
        private PdfParser() {
            super(DetectedContentType.PDF);
        }

        @Override
        public ParsedDocument parse(InputStream input, ParseLimits limits) {
            return guarded(() -> {
                try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
                    if (document.getNumberOfPages() > limits.maxPages()) {
                        throw new DocumentParseException("PAGE_LIMIT", "PDF page limit exceeded");
                    }
                    List<ParsedTextBlock> blocks = new ArrayList<>();
                    CharacterBudget budget = new CharacterBudget(limits.maxExtractedCharacters());
                    PDFTextStripper stripper = new PDFTextStripper();
                    for (int page = 1; page <= document.getNumberOfPages(); page++) {
                        stripper.setStartPage(page);
                        stripper.setEndPage(page);
                        String text = normalize(stripper.getText(document));
                        if (!text.isBlank()) {
                            budget.add(text);
                            blocks.add(new ParsedTextBlock(text, page, null, null));
                        }
                    }
                    return new ParsedDocument(blocks, Map.of("pdf:pages", Integer.toString(document.getNumberOfPages())));
                } catch (IOException exception) {
                    throw new DocumentParseException("PARSER_FAILED", "PDF parsing failed", exception);
                }
            });
        }
    }

    private static final class DocxParser extends TypedParser {
        private DocxParser() {
            super(DetectedContentType.DOCX);
        }

        @Override
        public ParsedDocument parse(InputStream input, ParseLimits limits) {
            return guarded(() -> {
                try (XWPFDocument document = new XWPFDocument(input)) {
                    List<ParsedTextBlock> blocks = new ArrayList<>();
                    CharacterBudget budget = new CharacterBudget(limits.maxExtractedCharacters());
                    String section = null;
                    StringBuilder content = new StringBuilder();
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        String text = normalize(paragraph.getText());
                        if (text.isBlank()) continue;
                        if (isHeading(paragraph)) {
                            addSection(blocks, budget, content, section);
                            section = text;
                        } else {
                            if (!content.isEmpty()) content.append('\n');
                            content.append(text);
                        }
                    }
                    addSection(blocks, budget, content, section);
                    return new ParsedDocument(blocks, Map.of());
                } catch (IOException exception) {
                    throw new DocumentParseException("PARSER_FAILED", "DOCX parsing failed", exception);
                }
            });
        }

        private static boolean isHeading(XWPFParagraph paragraph) {
            String style = paragraph.getStyle();
            return style != null && style.toLowerCase(java.util.Locale.ROOT).startsWith("heading");
        }
    }

    private static final class XlsxParser extends TypedParser {
        private XlsxParser() {
            super(DetectedContentType.XLSX);
        }

        @Override
        public ParsedDocument parse(InputStream input, ParseLimits limits) {
            return guarded(() -> {
                try (Workbook workbook = WorkbookFactory.create(input)) {
                    List<ParsedTextBlock> blocks = new ArrayList<>();
                    CharacterBudget budget = new CharacterBudget(limits.maxExtractedCharacters());
                    DataFormatter formatter = new DataFormatter(java.util.Locale.ROOT);
                    for (Sheet sheet : workbook) {
                        StringBuilder content = new StringBuilder();
                        for (Row row : sheet) {
                            List<String> values = new ArrayList<>();
                            for (Cell cell : row) {
                                String value = cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
                                        ? "=" + cell.getCellFormula() : formatter.formatCellValue(cell);
                                if (!value.isBlank()) values.add(value);
                            }
                            if (!values.isEmpty()) {
                                if (!content.isEmpty()) content.append('\n');
                                content.append(String.join("\t", values));
                            }
                        }
                        String text = content.toString();
                        if (!text.isBlank()) {
                            budget.add(text);
                            blocks.add(new ParsedTextBlock(text, null, sheet.getSheetName(), null));
                        }
                    }
                    return new ParsedDocument(blocks, Map.of("xlsx:sheets", Integer.toString(workbook.getNumberOfSheets())));
                } catch (IOException exception) {
                    throw new DocumentParseException("PARSER_FAILED", "XLSX parsing failed", exception);
                }
            });
        }
    }

    private static final class MarkdownParser extends TypedParser {
        private MarkdownParser() {
            super(DetectedContentType.MARKDOWN);
        }

        @Override
        public ParsedDocument parse(InputStream input, ParseLimits limits) {
            return guarded(() -> sections(readUtf8(input), limits));
        }
    }

    private static final class TextParser extends TypedParser {
        private TextParser() {
            super(DetectedContentType.TEXT);
        }

        @Override
        public ParsedDocument parse(InputStream input, ParseLimits limits) {
            return guarded(() -> {
                String text = normalize(readUtf8(input));
                CharacterBudget budget = new CharacterBudget(limits.maxExtractedCharacters());
                if (text.isBlank()) return new ParsedDocument(List.of(), Map.of());
                budget.add(text);
                return new ParsedDocument(List.of(new ParsedTextBlock(text, null, null, null)), Map.of());
            });
        }
    }

    private static final class ImageParser extends TypedParser {
        private ImageParser() {
            super(DetectedContentType.IMAGE);
        }

        @Override
        public ParsedDocument parse(InputStream input, ParseLimits limits) {
            return guarded(() -> {
                try (ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
                    if (imageInput == null) return new ParsedDocument(List.of(), Map.of());
                    java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                    if (!readers.hasNext()) return new ParsedDocument(List.of(), Map.of());
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(imageInput, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if (width > limits.maxImageWidth() || height > limits.maxImageHeight()
                                || ((long) width * height) > limits.maxImagePixels()) {
                            throw new DocumentParseException("IMAGE_PIXEL_LIMIT", "Image pixel limit exceeded");
                        }
                        return new ParsedDocument(List.of(), Map.of(
                                "image:width", Integer.toString(width),
                                "image:height", Integer.toString(height)));
                    } finally {
                        reader.dispose();
                    }
                } catch (IOException exception) {
                    throw new DocumentParseException("PARSER_FAILED", "Image metadata parsing failed", exception);
                }
            });
        }
    }

    private static ParsedDocument sections(String source, ParseLimits limits) {
        List<ParsedTextBlock> blocks = new ArrayList<>();
        CharacterBudget budget = new CharacterBudget(limits.maxExtractedCharacters());
        String section = null;
        StringBuilder content = new StringBuilder();
        for (String line : normalize(source).split("\\n", -1)) {
            if (line.matches("^#{1,6}\\s+.+$")) {
                addSection(blocks, budget, content, section);
                section = line.replaceFirst("^#{1,6}\\s+", "").trim();
            } else {
                if (!content.isEmpty()) content.append('\n');
                content.append(line);
            }
        }
        addSection(blocks, budget, content, section);
        return new ParsedDocument(blocks, Map.of());
    }

    private static void addSection(List<ParsedTextBlock> blocks, CharacterBudget budget,
            StringBuilder content, String section) {
        String text = normalize(content.toString());
        content.setLength(0);
        if (text.isBlank() && section != null) text = section;
        if (!text.isBlank()) {
            budget.add(text);
            blocks.add(new ParsedTextBlock(text, null, null, section));
        }
    }

    private static String readUtf8(InputStream input) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(input.readAllBytes()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new DocumentParseException("INVALID_TEXT_ENCODING", "Text is not valid UTF-8", exception);
        } catch (IOException exception) {
            throw new DocumentParseException("SOURCE_READ_FAILED", "Text source could not be read", exception);
        }
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static final class CharacterBudget {
        private final int maximum;
        private int used;

        private CharacterBudget(int maximum) {
            this.maximum = maximum;
        }

        private void add(String text) {
            used += text.codePointCount(0, text.length());
            if (used > maximum) {
                throw new DocumentParseException("CHARACTER_LIMIT", "Extracted character limit exceeded");
            }
        }
    }
}
