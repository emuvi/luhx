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
 * Reads text files as UTF-8 by default, with overloads for an explicitly chosen
 * character set.
 *
 * <p>Supported extensions are listed by {@link DocReaderTXTUtils}. The reader
 * treats file contents as text and does not validate their encoding or syntax.
 */
public class DocReaderTXT {
    
    /**
     * Checks whether the file name has a supported plain-text extension.
     *
     * @param file the file to check
     * @return {@code true} if the extension is supported; {@code false} otherwise
     */
    public static boolean canRead(File file) {
        return DocReaderTXTUtils.isTXTFile(file);
    }
    
    /** The underlying plaintext file. */
    private final File file;

    /**
     * Creates a reader for a plain-text file.
     *
     * @param file file to read; must not be {@code null}
     */
    public DocReaderTXT(File file) {
        this.file = file;
    }

    /**
     * Reads the complete file and decodes it as UTF-8. Malformed input bytes are
     * replaced according to the JDK's standard UTF-8 decoder behavior.
     *
     * @return the raw string content of the file
     * @throws Exception if the file cannot be read
     */
    public String read() throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Reads and decodes the complete file using the supplied character set.
     *
     * @param charset character set used to decode the bytes
     * @return the raw string content of the file
     * @throws Exception if the file cannot be read
     */
    public String read(Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset);
    }

    /**
     * Reads the file as UTF-8 and returns its lines. Line terminators are not
     * included in the returned strings, and a trailing terminator adds no extra line.
     *
     * @return lines in file order; an empty file produces an empty list
     * @throws Exception if the file cannot be read
     */
    public List<String> readLines() throws Exception {
        return read().lines().toList();
    }

    /**
     * Reads the file using the supplied character set and returns its lines.
     *
     * @param charset character set used to decode the bytes
     * @return lines in file order; an empty file produces an empty list
     * @throws Exception if the file cannot be read
     */
    public List<String> readLines(Charset charset) throws Exception {
        return read(charset).lines().toList();
    }

    /**
     * Writes text as UTF-8, creating the file if needed and replacing any
     * existing content. Parent directories are not created.
     *
     * @param content text to write
     * @throws Exception if the file cannot be created or written
     */
    public void write(String content) throws Exception {
        Files.writeString(file.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Appends text as UTF-8, creating the file if needed. Parent directories
     * are not created.
     *
     * @param content text to append
     * @throws Exception if the file cannot be created or written
     */
    public void append(String content) throws Exception {
        Files.writeString(file.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Reads the file and returns at most the requested number of leading
     * characters.
     *
     * @param length the maximum number of characters to return; must not be negative
     * @return up to {@code length} characters
     * @throws Exception if the file cannot be read
     * @throws IndexOutOfBoundsException if {@code length} is negative
     */
    public String readPreview(int length) throws Exception {
        return DocReaderUtils.preview(read(), length);
    }

    /**
     * Reports one page because plain-text files have no intrinsic pagination.
     *
     * @return {@code 1}
     */
    public Integer countPages() throws Exception {
        return 1;
    }

    /**
     * Counts whitespace-separated tokens in the decoded text. Empty or
     * whitespace-only files have a count of zero.
     *
     * @return the word count
     * @throws Exception if the file cannot be read
     */
    public int getWordCount() throws Exception {
        return DocReaderUtils.wordCount(read());
    }

    /**
     * Counts UTF-16 code units in the decoded text, as returned by
     * {@link String#length()}.
     *
     * @return the character count
     * @throws Exception if the file cannot be read
     */
    public int getCharCount() throws Exception {
        return read().length();
    }

    /**
     * Retrieves the file-system creation time. The value is supplied by the
     * underlying file system and may be unsupported or approximate.
     *
     * @return the creation date
     * @throws Exception if the file attributes cannot be read
     */
    public Date getCreationDate() throws Exception {
        var attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return new Date(attr.creationTime().toMillis());
    }

}
