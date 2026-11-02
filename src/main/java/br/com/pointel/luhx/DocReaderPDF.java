package br.com.pointel.luhx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Extracts text, metadata, page counts, and page images from PDF files using
 * Apache PDFBox.
 *
 * <p>Extracted page text includes non-blank annotation contents. This class
 * recognizes files by extension; PDF validity is checked when an operation
 * opens the document.
 */
public class DocReaderPDF {
    
    /**
     * Checks whether the file name has an extension recognized as PDF.
     *
     * @param file the file to check
     * @return {@code true} if the extension is recognized; {@code false} otherwise
     */
    public static boolean canRead(File file) {
        return DocReaderPDFUtils.isPDFFile(file);
    }
    
    /** The underlying PDF file. */
    private final File file;

    /**
     * Creates a reader for a PDF file.
     *
     * @param file file to read; must not be {@code null}
     */
    public DocReaderPDF(File file) {
        this.file = file;
    }

    /**
     * Extracts text from all pages in page order, including non-blank annotation
     * contents. Adjacent page texts are separated by a line containing
     * {@code ---}.
     *
     * @return the combined extracted text
     * @throws Exception if the PDF cannot be opened or read
     */
    public String read() throws Exception {
        // Temp-file buffering keeps large PDFs out of the heap during bulk indexing.
        try (var doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
            var count = doc.getNumberOfPages();
            var builder = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    builder.append(System.lineSeparator());
                    builder.append("---");
                    builder.append(System.lineSeparator());
                    builder.append(System.lineSeparator());
                }
                builder.append(readPage(doc, i));
            }
            return builder.toString();
        }
    }

    /**
     * Returns the number of pages in the PDF.
     *
     * @return the total page count
     * @throws Exception if the PDF cannot be opened or its page tree cannot be read
     */
    public Integer countPages() throws Exception {
        try (var doc = PDDocument.load(file)) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Extracts text and non-blank annotation contents from one page.
     *
     * @param pageNumber zero-based page index
     * @return extracted text and annotation contents, if any
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document
     * @throws Exception if the PDF cannot be opened or the page cannot be read
     */
    public String readPage(int pageNumber) throws Exception {
        try (var doc = PDDocument.load(file)) {
            return readPage(doc, pageNumber);
        }
    }

    /**
     * Extracts text and non-blank annotation contents from a page in an already
     * loaded document. Annotation contents are appended after the page text.
     *
     * @param doc        open PDF document; ownership remains with the caller
     * @param pageNumber zero-based page index
     * @return extracted page text followed by annotation contents, if present
     * @throws IOException if PDFBox cannot read page text or annotations
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document
     */
    public static String readPage(PDDocument doc, int pageNumber) throws IOException {
        int numPages = doc.getNumberOfPages();
        if (pageNumber < 0 || pageNumber >= numPages) {
            throw new IllegalArgumentException("Page number " + pageNumber + " is out of bounds. The PDF has " + numPages + " pages.");
        }
        var stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber + 1);
        stripper.setEndPage(pageNumber + 1);
        String text = stripper.getText(doc).trim();

        var page = doc.getPage(pageNumber);
        var annotations = page.getAnnotations();
        StringBuilder comments = new StringBuilder();
        for (var annotation : annotations) {
            var contents = annotation.getContents();
            if (contents != null && !contents.isBlank()) {
                if (comments.length() > 0) {
                    comments.append(System.lineSeparator());
                    comments.append("--:");
                    comments.append(System.lineSeparator());
                    comments.append(System.lineSeparator());
                }
                comments.append(contents.trim());
            }
        }

        if (comments.length() > 0) {
            text = text
                            + System.lineSeparator()
                            + "--\\" 
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + comments.toString();
        }
        return text;
    }

    /**
     * Renders a page to an RGB image at the requested resolution.
     *
     * @param pageNumber zero-based page index
     * @param dpi        rendering resolution in dots per inch
     * @return rendered page image
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document
     * @throws Exception if the PDF cannot be opened or rendered
     */
    public BufferedImage getPageAsImage(int pageNumber, int dpi) throws Exception {
        try (var doc = PDDocument.load(file)) {
            if (pageNumber < 0 || pageNumber >= doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Page number " + pageNumber + " is out of bounds. The PDF has " + doc.getNumberOfPages() + " pages.");
            }
            PDFRenderer pdfRenderer = new PDFRenderer(doc);
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pageNumber, dpi);
            return bufferedImage;
        }
    }
    /**
     * Reads all extracted page text and splits it at line-feed boundaries.
     *
     * @return extracted lines in document order
     * @throws Exception if the PDF cannot be read
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
     * @throws Exception if the PDF cannot be read
     * @throws IndexOutOfBoundsException if {@code length} is negative
     */
    public String readPreview(int length) throws Exception {
        return DocReaderUtils.preview(read(), length);
    }

    /**
     * Counts whitespace-separated tokens in the extracted text. An empty
     * document has a count of zero.
     *
     * @return the word count
     * @throws Exception if the PDF cannot be read
     */
    public int getWordCount() throws Exception {
        return DocReaderUtils.wordCount(read());
    }

    /**
     * Counts UTF-16 code units in the extracted text, as returned by
     * {@link String#length()}.
     *
     * @return the character count
     * @throws Exception if the PDF cannot be read
     */
    public int getCharCount() throws Exception {
        return read().length();
    }

    /**
     * Retrieves the author field from the PDF document information dictionary.
     *
     * @return the author, or {@code null} if the field is absent
     * @throws Exception if the PDF cannot be opened or its metadata cannot be read
     */
    public String getAuthor() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getAuthor() : null;
        }
    }

    /**
     * Retrieves the title field from the PDF document information dictionary.
     *
     * @return the title, or {@code null} if the field is absent
     * @throws Exception if the PDF cannot be opened or its metadata cannot be read
     */
    public String getTitle() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getTitle() : null;
        }
    }

    /**
     * Retrieves the subject field from the PDF document information dictionary.
     *
     * @return the subject, or {@code null} if the field is absent
     * @throws Exception if the PDF cannot be opened or its metadata cannot be read
     */
    public String getSubject() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getSubject() : null;
        }
    }

    /**
     * Retrieves the creator field from the PDF document information dictionary.
     *
     * @return the creator, or {@code null} if the field is absent
     * @throws Exception if the PDF cannot be opened or its metadata cannot be read
     */
    public String getCreator() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            return info != null ? info.getCreator() : null;
        }
    }

    /**
     * Retrieves the PDF metadata creation date, falling back to the file-system
     * creation time when the metadata date is absent.
     *
     * @return the metadata or file-system creation date
     * @throws Exception if the PDF or required file attributes cannot be read
     */
    public Date getCreationDate() throws Exception {
        try (var doc = PDDocument.load(file)) {
            var info = doc.getDocumentInformation();
            if (info != null && info.getCreationDate() != null) {
                return info.getCreationDate().getTime();
            }
        }
        var attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return new Date(attr.creationTime().toMillis());
    }

    /**
     * Extracts image XObjects directly referenced by the page resources. Images
     * nested inside other XObjects or painted as inline image operators are not
     * included.
     *
     * @param pageNumber zero-based page index
     * @return images encountered in the page's resources, in resource iteration order
     * @throws IllegalArgumentException if {@code pageNumber} is outside the document
     * @throws Exception if the PDF or an image cannot be read
     */
    public List<BufferedImage> getImages(int pageNumber) throws Exception {
        var images = new ArrayList<BufferedImage>();
        try (var doc = PDDocument.load(file)) {
            if (pageNumber < 0 || pageNumber >= doc.getNumberOfPages()) {
                throw new IllegalArgumentException("Page number " + pageNumber + " is out of bounds.");
            }
            var page = doc.getPage(pageNumber);
            var resources = page.getResources();
            for (COSName c : resources.getXObjectNames()) {
                var o = resources.getXObject(c);
                if (o instanceof PDImageXObject) {
                    images.add(((PDImageXObject) o).getImage());
                }
            }
        }
        return images;
    }

}
