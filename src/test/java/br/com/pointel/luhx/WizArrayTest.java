package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WizArrayTest {

    @Test
    void shouldReturnTrueWhenElementExistsInArray() {
        assertTrue(WizArray.has("pdf", "txt", "pdf", "docx"));
        assertTrue(WizArray.has(42, 10, 20, 42, 99));
    }

    @Test
    void shouldReturnFalseWhenElementDoesNotExistInArray() {
        assertFalse(WizArray.has("xlsx", "txt", "pdf", "docx"));
        assertFalse(WizArray.has("PDF", "txt", "pdf", "docx"));
    }

    @Test
    void shouldHandleNullAndEmptyInputsSafely() {
        assertFalse(WizArray.has(null, "a", "b"));
        assertFalse(WizArray.has("a", (String[]) null));
        assertFalse(WizArray.has("a"));
        assertFalse(WizArray.has("a", null, "b"));
        assertTrue(WizArray.has("b", null, "b"));
    }

}

