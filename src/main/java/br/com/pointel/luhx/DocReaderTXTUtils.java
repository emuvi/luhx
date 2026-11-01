package br.com.pointel.luhx;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Utility class providing file extension matching algorithms to identify 
 * plain text and source code files.
 */
public class DocReaderTXTUtils {

    /** List of supported plaintext file extensions. */
    public static String[] TXT_EXTENSIONS = new String[]{ "txt", "md", "htm", "html", "php", "phtml", "java", "py", "log" };

    /**
     * Checks if the given file has a recognized plaintext extension.
     *
     * @param file the file to check
     * @return true if the file is recognized as a plaintext format
     */
    public static boolean isTXTFile(File file) {
        return isTXTFile(file.getName());
    }

    /**
     * Checks if the given file name has a recognized plaintext extension.
     *
     * @param fileName the name of the file to check
     * @return true if the file name has a recognized plaintext extension
     */
    public static boolean isTXTFile(String fileName) {
        return UtilsArrays.has(FilenameUtils.getExtension(fileName).toLowerCase(), TXT_EXTENSIONS);
    }
    /**
     * Retrieves all registered plaintext extensions.
     *
     * @return a list of all supported plaintext file extensions
     */
    public static List<String> getAllExtensions() {
        return Arrays.asList(TXT_EXTENSIONS);
    }
    
}
