package br.com.pointel.luhx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Reads text and metadata from supported PDF, Microsoft Office, and plain-text
 * documents through a single interface.
 *
 * <p>The reader selects a format from the file extension using
 * {@link DocReaderPDFUtils}, {@link DocReaderMSOUtils}, and
 * {@link DocReaderTXTUtils}; it does not validate file contents until a read
 * operation is performed. Unsupported extensions return empty or zero values
 * where documented, or {@code null} for unavailable metadata.
 */
public class DocReader {

    private enum Format {
        PDF,
        OFFICE,
        TEXT,
        UNSUPPORTED
    }

    /**
     * Checks whether the file name has an extension supported by a specialized
     * reader. This check does not inspect the file contents.
     *
     * @param file the file to check
     * @return {@code true} if the file extension is supported; {@code false} otherwise
     */
    public static boolean canRead(File file) {
        return DocReaderPDF.canRead(file)
                || DocReaderMSO.canRead(file)
                || DocReaderTXT.canRead(file);
    }

    /** The underlying document file. */
    private final File file;

    private Format format() {
        if (DocReaderPDF.canRead(file)) {
            return Format.PDF;
        }
        if (DocReaderMSO.canRead(file)) {
            return Format.OFFICE;
        }
        if (DocReaderTXT.canRead(file)) {
            return Format.TEXT;
        }
        return Format.UNSUPPORTED;
    }

    /**
     * Creates a reader that selects its document format from the file extension.
     *
     * @param file document to read; must not be {@code null}
     */
    public DocReader(File file) {
        this.file = file;
    }

    /**
     * Extracts text using the reader selected by the file extension. PDF page
     * text includes extracted annotation contents; Office extraction follows
     * the format-specific Apache POI extractor.
     *
     * @return extracted text, or an empty string for an unsupported extension
     * @throws Exception if the file cannot be read or parsed
     */
    public String read() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).read();
            case OFFICE -> new DocReaderMSO(file).read();
            case TEXT -> new DocReaderTXT(file).read();
            case UNSUPPORTED -> "";
        };
    }

    /**
     * Returns the count reported for the document format. Office Word counts
     * use document metadata, Excel counts use sheets, and PowerPoint counts
     * use slides. Plain-text files report one; unsupported extensions report zero.
     *
     * @return the format-specific count, or {@code 0} for an unsupported extension
     * @throws Exception if the document or its metadata cannot be read
     */
    public Integer countPages() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).countPages();
            case OFFICE -> new DocReaderMSO(file).countPages();
            case TEXT -> new DocReaderTXT(file).countPages();
            case UNSUPPORTED -> 0;
        };
    }

    /**
     * Renders a PDF page as a raster image. Office and plain-text formats do not
     * currently support page rendering.
     *
     * @param pageNumber the zero-based index of the page to render
     * @param dpi        rendering resolution in dots per inch
     * @return the rendered image, or {@code null} when the extension is not PDF
     * @throws IllegalArgumentException if a PDF page number is outside the document
     * @throws Exception if the PDF cannot be read or rendered
     */
    public BufferedImage getPageAsImage(int pageNumber, int dpi) throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getPageAsImage(pageNumber, dpi);
            case OFFICE, TEXT, UNSUPPORTED -> null;
        };
    }

    /**
     * Returns the file associated with this reader.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns the file name, including its extension.
     *
     * @return the file name
     */
    public String getFileName() {
        return file.getName();
    }

    /**
     * Returns the file extension without the leading period.
     *
     * @return the file extension
     */
    public String getExtension() {
        return FilenameUtils.getExtension(file.getName());
    }

    /**
     * Returns the current file size in bytes.
     *
     * @return the file size
     */
    public long getFileSize() {
        return file.length();
    }

    /**
     * Returns the current last-modified time reported by the file system.
     *
     * @return the last modified time in milliseconds
     */
    public long getLastModified() {
        return file.lastModified();
    }

    /**
     * Reads extracted text and splits it into lines. Line boundaries follow
     * {@link String#lines()} for plain text and the reader's newline splitting
     * for PDF and Office documents.
     *
     * @return extracted lines, or an empty list for an unsupported extension
     * @throws Exception if the document cannot be read or parsed
     */
    public List<String> readLines() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).readLines();
            case OFFICE -> new DocReaderMSO(file).readLines();
            case TEXT -> new DocReaderTXT(file).readLines();
            case UNSUPPORTED -> new ArrayList<>();
        };
    }

    /**
     * Reads at most the requested number of characters from the start of the
     * extracted text.
     *
     * @param length the maximum number of characters to return; must not be negative
     * @return up to {@code length} characters, or an empty string if the format is unsupported
     * @throws Exception if the document cannot be read or parsed
     * @throws IndexOutOfBoundsException if {@code length} is negative for a supported format
     */
    public String readPreview(int length) throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).readPreview(length);
            case OFFICE -> new DocReaderMSO(file).readPreview(length);
            case TEXT -> new DocReaderTXT(file).readPreview(length);
            case UNSUPPORTED -> "";
        };
    }

    /**
     * Counts whitespace-separated tokens in the extracted text. Empty or
     * whitespace-only text has a count of zero.
     *
     * @return the word count, or zero for an unsupported extension
     * @throws Exception if the document cannot be read or parsed
     */
    public int getWordCount() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getWordCount();
            case OFFICE -> new DocReaderMSO(file).getWordCount();
            case TEXT -> new DocReaderTXT(file).getWordCount();
            case UNSUPPORTED -> 0;
        };
    }

    /**
     * Counts UTF-16 code units in the extracted text, as returned by
     * {@link String#length()}.
     *
     * @return the character count, or zero for an unsupported extension
     * @throws Exception if the document cannot be read or parsed
     */
    public int getCharCount() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getCharCount();
            case OFFICE -> new DocReaderMSO(file).getCharCount();
            case TEXT -> new DocReaderTXT(file).getCharCount();
            case UNSUPPORTED -> 0;
        };
    }

    /**
     * Retrieves the author recorded in PDF or Office document metadata.
     *
     * @return the author, or {@code null} when unsupported or unavailable
     * @throws Exception if the document metadata cannot be read
     */
    public String getAuthor() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getAuthor();
            case OFFICE -> new DocReaderMSO(file).getAuthor();
            case TEXT, UNSUPPORTED -> null;
        };
    }

    /**
     * Retrieves the title recorded in PDF or Office document metadata.
     *
     * @return the title, or {@code null} when unsupported or unavailable
     * @throws Exception if the document metadata cannot be read
     */
    public String getTitle() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getTitle();
            case OFFICE -> new DocReaderMSO(file).getTitle();
            case TEXT, UNSUPPORTED -> null;
        };
    }

    /**
     * Retrieves the document creation date. PDF and Office readers prefer
     * embedded metadata and fall back to the file-system creation time where
     * supported; plain-text files use the file-system attribute.
     *
     * @return the creation date, or {@code null} for an unsupported extension
     * @throws Exception if the document metadata or file attributes cannot be read
     */
    public Date getCreationDate() throws Exception {
        return switch (format()) {
            case PDF -> new DocReaderPDF(file).getCreationDate();
            case OFFICE -> new DocReaderMSO(file).getCreationDate();
            case TEXT -> new DocReaderTXT(file).getCreationDate();
            case UNSUPPORTED -> null;
        };
    }

}
