package br.com.pointel.luhx;

/** Shared text operations used by the document readers. */
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
