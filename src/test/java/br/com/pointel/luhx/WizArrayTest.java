package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WizArrayTest {

    @Test
    void shouldReturnTrueWhenElementExistsInArray() {
        assertTrue(UtilsArrays.has("pdf", "txt", "pdf", "docx"));
        assertTrue(UtilsArrays.has(42, 10, 20, 42, 99));
    }

    @Test
    void shouldReturnFalseWhenElementDoesNotExistInArray() {
        assertFalse(UtilsArrays.has("xlsx", "txt", "pdf", "docx"));
        assertFalse(UtilsArrays.has("PDF", "txt", "pdf", "docx"));
    }

    @Test
    void shouldHandleNullAndEmptyInputsSafely() {
        assertFalse(UtilsArrays.has(null, "a", "b"));
        assertFalse(UtilsArrays.has("a", (String[]) null));
        assertFalse(UtilsArrays.has("a"));
        assertFalse(UtilsArrays.has("a", null, "b"));
        assertTrue(UtilsArrays.has("b", null, "b"));
    }

}

