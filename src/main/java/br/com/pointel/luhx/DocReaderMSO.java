package br.com.pointel.luhx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackageProperties;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.xml.sax.InputSource;

/**
 * Extracts text, basic metadata, and format-specific counts from Microsoft
 * Word, Excel, and PowerPoint documents using Apache POI.
 *
 * <p>Recognized extensions are {@code doc}, {@code docx}, {@code xls},
 * {@code xlsx}, {@code ppt}, and {@code pptx}. Detection is extension-based;
 * the file contents are parsed only when a read operation is requested.
 */
public class DocReaderMSO {

    /**
     * Checks whether the file name has a supported Microsoft Office extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean canRead(File file) {
        return DocReaderMSOUtils.isMSWordFile(file)
                        || DocReaderMSOUtils.isMSExcelFile(file)
                        || DocReaderMSOUtils.isMSPowerPointFile(file);
    }

    /** The underlying document file. */
    private final File path;

    /**
     * Creates a reader for a Microsoft Office document.
     *
     * @param path file to read; must not be {@code null}
     */
    public DocReaderMSO(File path) {
        this.path = path;
    }

    /**
     * Extracts text using the parser selected by the file extension. PowerPoint
     * extraction includes slide content, notes, and comments; spreadsheet
     * extraction includes sheet names and cell values.
     *
     * @return extracted text in document order
     * @throws Exception if the file cannot be read or parsed
     */
    public String read() throws Exception {
        if (DocReaderMSOUtils.isMSWordFile(path)) {
            return extractTextFromWord();
        }
        if (DocReaderMSOUtils.isMSExcelFile(path)) {
            return extractTextFromExcel();
        }
        if (DocReaderMSOUtils.isMSPowerPointFile(path)) {
            return extractTextFromPowerPoint();
        }
        throw new Exception("File type not expected: " + path.getName());
    }

    /**
     * Extracts text from a Microsoft Word document, automatically handling OLE2 and OOXML formats.
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file or format is unsupported
     */
    private String extractTextFromWord() throws Exception {
        if (hasExtension("doc")) {
            return extractTextFromWordOle();
        }
        if (hasExtension("docx")) {
            return extractTextFromWordXml();
        }
        throw new Exception("File type not expected: " + path.getName());
    }

    /**
     * Extracts text from a legacy Microsoft Word OLE2 document (.doc).
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file
     */
    private String extractTextFromWordOle() throws Exception {
        try (var fis = new FileInputStream(path)) {
            try (var document = new HWPFDocument(fis);
                var extractor = new WordExtractor(document);) {
                return extractor.getText();
            }
        }
    }

    /**
     * Extracts text from a modern Microsoft Word OOXML document (.docx).
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file
     */
    private String extractTextFromWordXml() throws Exception {
        try (var fis = new FileInputStream(path)) {
            try (var document = new XWPFDocument(fis);
                var extractor = new XWPFWordExtractor(document);) {
                return extractor.getText();
            }
        }
    }

    /**
     * Extracts text from a Microsoft Excel document, automatically handling OLE2 and OOXML formats.
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file or format is unsupported
     */
    private String extractTextFromExcel() throws Exception {
        if (isXlsxFile()) {
            return extractTextFromExcelXml();
        }
        try (Workbook workbook = WorkbookFactory.create(path)) {
            if (workbook instanceof HSSFWorkbook legacyWorkbook) {
                try (var extractor = new ExcelExtractor(legacyWorkbook)) {
                    extractor.setFormulasNotResults(false);
                    extractor.setIncludeSheetNames(true);
                    return extractor.getText();
                }
            }
            throw new IOException("Unsupported Excel workbook type: " + path.getName());
        }
    }

    private String extractTextFromExcelXml() throws Exception {
        try (OPCPackage pkg = OPCPackage.open(path, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            reader.setUseReadOnlySharedStringsTable(true);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();
            DataFormatter formatter = new DataFormatter();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            StringBuilder text = new StringBuilder();

            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    if (text.length() > 0) {
                        text.append(System.lineSeparator());
                    }
                    text.append(sheets.getSheetName()).append(System.lineSeparator());

                    var contents = new XSSFSheetXMLHandler.SheetContentsHandler() {
                        @Override
                        public void startRow(int rowNum) {}

                        @Override
                        public void endRow(int rowNum) {
                            text.append(System.lineSeparator());
                        }

                        @Override
                        public void cell(String cellReference, String formattedValue,
                                org.apache.poi.xssf.usermodel.XSSFComment comment) {
                            if (formattedValue != null) {
                                text.append(formattedValue).append('\t');
                            }
                        }

                        @Override
                        public void headerFooter(String value, boolean isHeader, String tagName) {
                            if (value != null && !value.isBlank()) {
                                text.append(value).append(System.lineSeparator());
                            }
                        }
                    };
                    var parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(
                            styles, sharedStrings, contents, formatter, false));
                    parser.parse(new InputSource(sheet));
                }
            }
            return text.toString();
        }
    }

    private boolean isXlsxFile() throws IOException {
        return FileMagic.valueOf(path) == FileMagic.OOXML;
    }

    /**
     * Extracts text from a Microsoft PowerPoint document, automatically handling OLE2 and OOXML formats.
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file or format is unsupported
     */
    private String extractTextFromPowerPoint() throws Exception {
        if (hasExtension("ppt")) {
            return extractTextFromPowerPointOle();
        }
        if (hasExtension("pptx")) {
            return extractTextFromPowerPointXml();
        }
        throw new Exception("File type not expected: " + path.getName());
    }

    /**
     * Extracts text from a legacy Microsoft PowerPoint OLE2 document (.ppt).
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file
     */
    @SuppressWarnings("all")
    private String extractTextFromPowerPointOle() throws Exception {
        try (var fis = new FileInputStream(path)) {
            try (var slideShow = SlideShowFactory.create(fis);
                var extractor = new SlideShowExtractor(slideShow);) {
                extractor.setMasterByDefault(true);
                extractor.setSlidesByDefault(true);
                extractor.setNotesByDefault(true);
                extractor.setCommentsByDefault(true);
                return extractor.getText();
            }
        }
    }

    /**
     * Extracts text from a modern Microsoft PowerPoint OOXML document (.pptx).
     *
     * @return the extracted raw text
     * @throws Exception if an error occurs reading the file
     */
    @SuppressWarnings("all")
    private String extractTextFromPowerPointXml() throws Exception {
        try (var fis = new FileInputStream(path)) {
            try (var slideShow = new XMLSlideShow(fis);
                 var extractor = new SlideShowExtractor(slideShow);) {
                extractor.setMasterByDefault(true);
                extractor.setSlidesByDefault(true);
                extractor.setNotesByDefault(true);
                extractor.setCommentsByDefault(true);
                return extractor.getText();
            }
        }
    }

    /**
     * Extracts document text and splits it at line-feed boundaries.
     *
     * @return extracted lines in document order
     * @throws Exception if the document cannot be read or parsed
     */
    public List<String> readLines() throws Exception {
        return Arrays.asList(read().split("\\r?\\n"));
    }

    /**
     * Returns at most the requested number of leading characters from the
     * extracted document text.
     *
     * @param length the maximum number of characters to return; must not be negative
     * @return up to {@code length} characters
     * @throws Exception if the document cannot be read or parsed
     * @throws IndexOutOfBoundsException if {@code length} is negative
     */
    public String readPreview(int length) throws Exception {
        return DocReaderUtils.preview(read(), length);
    }

    /**
     * Returns the count reported for the document format: page metadata for
     * Word, worksheet count for Excel, or slide count for PowerPoint. Word
     * formats that do not provide page metadata report one.
     *
     * @return the format-specific count, or {@code 1} if the format is unsupported
     *         or Word page metadata is unavailable
     * @throws Exception if the document or its count metadata cannot be read
     */
    public Integer countPages() throws Exception {
        if (DocReaderMSOUtils.isMSWordFile(path)) {
            return countWordPages();
        }
        if (DocReaderMSOUtils.isMSExcelFile(path)) {
            return countExcelSheets();
        }
        if (DocReaderMSOUtils.isMSPowerPointFile(path)) {
            return countPowerPointSlides();
        }
        return 1;
    }

    private int countWordPages() throws Exception {
        if (hasExtension("doc")) {
            try (var input = new FileInputStream(path); var document = new HWPFDocument(input)) {
                var summary = document.getSummaryInformation();
                return summary == null ? 1 : summary.getPageCount();
            }
        }
        try (var input = new FileInputStream(path); var document = new XWPFDocument(input)) {
            var properties = document.getProperties().getExtendedProperties();
            return properties == null ? 1 : properties.getPages();
        }
    }

    private int countExcelSheets() throws Exception {
        if (!isXlsxFile()) {
            try (Workbook workbook = WorkbookFactory.create(path)) {
                return workbook.getNumberOfSheets();
            }
        }
        try (OPCPackage pkg = OPCPackage.open(path, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            Iterator<InputStream> sheets = reader.getSheetsData();
            int count = 0;
            while (sheets.hasNext()) {
                try (InputStream ignored = sheets.next()) {
                    count++;
                }
            }
            return count;
        }
    }

    private int countPowerPointSlides() throws Exception {
        if (hasExtension("ppt")) {
            try (var input = new FileInputStream(path); var slideShow = SlideShowFactory.create(input)) {
                return slideShow.getSlides().size();
            }
        }
        if (hasExtension("pptx")) {
            try (var input = new FileInputStream(path); var slideShow = new XMLSlideShow(input)) {
                return slideShow.getSlides().size();
            }
        }
        return 1;
    }

    private boolean hasExtension(String extension) {
        return path.getName().toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    /**
     * Counts whitespace-separated tokens in the extracted text. Empty text has
     * a count of zero.
     *
     * @return the word count
     * @throws Exception if the document cannot be read or parsed
     */
    public int getWordCount() throws Exception {
        return DocReaderUtils.wordCount(read());
    }

    /**
     * Counts UTF-16 code units in the extracted text, as returned by
     * {@link String#length()}.
     *
     * @return the character count
     * @throws Exception if the document cannot be read or parsed
     */
    public int getCharCount() throws Exception {
        return read().length();
    }

    /**
     * Retrieves the author property from supported Office document metadata.
     *
     * @return the author, or {@code null} if the format or property is unsupported
     * @throws Exception if the document metadata cannot be read
     */
    public String getAuthor() throws Exception {
        return extractPoiStringProperty("author");
    }

    /**
     * Retrieves the title property from supported Office document metadata.
     *
     * @return the title, or {@code null} if the format or property is unsupported
     * @throws Exception if the document metadata cannot be read
     */
    public String getTitle() throws Exception {
        return extractPoiStringProperty("title");
    }

    /**
     * Retrieves the creation date from supported Office metadata, falling back
     * to the file-system creation time when no metadata date is available.
     *
     * @return the metadata or file-system creation date
     * @throws Exception if the document metadata or file attributes cannot be read
     */
    public Date getCreationDate() throws Exception {
        Object val = extractPoiProperty("creationDate");
        if (val instanceof Date) return (Date) val;
        // Fallback to file creation date
        var attr = Files.readAttributes(path.toPath(), BasicFileAttributes.class);
        return new Date(attr.creationTime().toMillis());
    }

    /**
     * Extracts a metadata property as a String using Apache POI.
     *
     * @param property the name of the property to extract (e.g., "author", "title")
     * @return the property value as a String, or null if unavailable
     * @throws Exception if an error occurs reading the file
     */
    private String extractPoiStringProperty(String property) throws Exception {
        Object val = extractPoiProperty(property);
        return val != null ? val.toString() : null;
    }

    /**
     * Extracts a metadata property as an Object using Apache POI.
     * Supports Word and Excel in legacy OLE2 and modern OOXML formats, and
     * modern OOXML PowerPoint files.
     *
     * @param property the name of the property to extract (e.g., "author", "title", "creationDate")
     * @return the extracted property value as an Object, or null if unavailable
     * @throws Exception if an error occurs reading the file
     */
    private Object extractPoiProperty(String property) throws Exception {
        if (DocReaderMSOUtils.isMSWordFile(path)) {
            if (path.getName().toLowerCase().endsWith(".doc")) {
                try (var fis = new FileInputStream(path); var doc = new HWPFDocument(fis)) {
                    var info = doc.getSummaryInformation();
                    if (info == null) return null;
                    if ("author".equals(property)) return info.getAuthor();
                    if ("title".equals(property)) return info.getTitle();
                    if ("creationDate".equals(property)) return info.getCreateDateTime();
                }
            } else {
                try (var fis = new FileInputStream(path); var doc = new XWPFDocument(fis)) {
                    var props = doc.getProperties().getCoreProperties();
                    if (props == null) return null;
                    if ("author".equals(property)) return props.getCreator();
                    if ("title".equals(property)) return props.getTitle();
                    if ("creationDate".equals(property)) return props.getCreated();
                }
            }
        } else if (DocReaderMSOUtils.isMSExcelFile(path)) {
            if (isXlsxFile()) {
                try (OPCPackage pkg = OPCPackage.open(path, PackageAccess.READ)) {
                    PackageProperties properties = pkg.getPackageProperties();
                    if ("author".equals(property)) return properties.getCreatorProperty().orElse(null);
                    if ("title".equals(property)) return properties.getTitleProperty().orElse(null);
                    if ("creationDate".equals(property)) return properties.getCreatedProperty().orElse(null);
                }
            }
            try (Workbook workbook = WorkbookFactory.create(path)) {
                if (workbook instanceof HSSFWorkbook doc) {
                    var info = doc.getSummaryInformation();
                    if (info == null) return null;
                    if ("author".equals(property)) return info.getAuthor();
                    if ("title".equals(property)) return info.getTitle();
                    if ("creationDate".equals(property)) return info.getCreateDateTime();
                }
            }
        } else if (DocReaderMSOUtils.isMSPowerPointFile(path)) {
            if (path.getName().toLowerCase().endsWith(".pptx")) {
                try (var fis = new FileInputStream(path); var doc = new XMLSlideShow(fis)) {
                    var props = doc.getProperties().getCoreProperties();
                    if (props == null) return null;
                    if ("author".equals(property)) return props.getCreator();
                    if ("title".equals(property)) return props.getTitle();
                    if ("creationDate".equals(property)) return props.getCreated();
                }
            }
        }
        return null;
    }

}
