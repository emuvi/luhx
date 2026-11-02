package br.com.pointel.luhx;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.List;

/**
 * A basic document reader that extracts raw text from plain text files
 * and other source code formats.
 */
public class DocReaderTXT {
    
    /**
     * Checks if the file is a recognized plaintext format.
     *
     * @param file the file to check
     * @return true if the file is a text file
     */
    public static boolean canRead(File file) {
        return DocReaderTXTUtils.isTXTFile(file);
    }
    
    /** The underlying plaintext file. */
    private final File file;

    /**
     * Constructs a reader for a plaintext document.
     *
     * @param file the file to read
     */
    public DocReaderTXT(File file) {
        this.file = file;
    }

    /**
     * Extracts the complete text from the file using UTF-8.
     *
     * @return the raw string content of the file
     * @throws Exception if an I/O error occurs
     */
    public String read() throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Extracts the complete text from the file utilizing a specific character set.
     *
     * @param charset the character set to use
     * @return the raw string content of the file
     * @throws Exception if an I/O error occurs
     */
    public String read(Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset);
    }

    /**
     * Reads all lines from the file using UTF-8.
     *
     * @return a list containing all lines
     * @throws Exception if an I/O error occurs
     */
    public List<String> readLines() throws Exception {
        return read().lines().toList();
    }

    /**
     * Reads all lines from the file utilizing a specific character set.
     *
     * @param charset the character set to use
     * @return a list containing all lines
     * @throws Exception if an I/O error occurs
     */
    public List<String> readLines(Charset charset) throws Exception {
        return read(charset).lines().toList();
    }

    /**
     * Writes the given text content to the file, replacing existing content.
     *
     * @param content the text to write
     * @throws Exception if an I/O error occurs
     */
    public void write(String content) throws Exception {
        Files.writeString(file.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Appends the given text content to the file.
     *
     * @param content the text to append
     * @throws Exception if an I/O error occurs
     */
    public void append(String content) throws Exception {
        Files.writeString(file.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Reads the file and returns a preview of its contents up to the given length.
     *
     * @param length the maximum number of characters to return; must not be negative
     * @return up to {@code length} characters
     * @throws Exception if an I/O error occurs
     * @throws IndexOutOfBoundsException if {@code length} is negative
     */
    public String readPreview(int length) throws Exception {
        return DocReaderUtils.preview(read(), length);
    }

    /**
     * Calculates the total number of physical pages. 
     * Plain text files are not paginated, so this defaults to 1.
     *
     * @return the total page count (1)
     */
    public Integer countPages() throws Exception {
        return 1;
    }

    /**
     * Calculates the total number of words in the text file.
     *
     * @return the word count
     * @throws Exception if an I/O error occurs
     */
    public int getWordCount() throws Exception {
        return DocReaderUtils.wordCount(read());
    }

    /**
     * Calculates the total number of characters in the text file.
     *
     * @return the character count
     * @throws Exception if an I/O error occurs
     */
    public int getCharCount() throws Exception {
        return read().length();
    }

    /**
     * Retrieves the file creation date.
     *
     * @return the creation date
     * @throws Exception if an error occurs reading attributes
     */
    public Date getCreationDate() throws Exception {
        var attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return new Date(attr.creationTime().toMillis());
    }

}
