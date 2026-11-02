package br.com.pointel.luhx;

/**
 * Package-private text operations shared by the document readers.
 *
 * <p>This class centralizes preview slicing and whitespace-token counting so
 * the supported document formats use the same definitions.
 */
final class DocReaderUtils {

    private DocReaderUtils() {}

    static String preview(String text, int length) {
        return text.substring(0, Math.min(text.length(), length));
    }

    static int wordCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
