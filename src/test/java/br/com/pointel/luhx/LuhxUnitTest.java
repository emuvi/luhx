package br.com.pointel.luhx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuhxUnitTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBuildLuceneQueryFromSingleAndMultipleKeys() {
        assertEquals("lucene", Luhx.buildLuceneQuery("lucene"));
        assertEquals("lucene AND h2", Luhx.buildLuceneQuery("lucene", "h2"));
        assertEquals("java AND database AND data", Luhx.buildLuceneQuery(" java ", "database", " data "));
    }

    @Test
    void shouldPreserveExplicitBooleanOperatorsInLuceneQuery() {
        assertEquals("lucene OR h2", Luhx.buildLuceneQuery("lucene", "OR", "h2"));
        assertEquals("lucene AND NOT h2", Luhx.buildLuceneQuery("lucene", "AND", "NOT", "h2"));
        assertEquals("java OR (lucene AND h2)", Luhx.buildLuceneQuery("java OR (lucene AND h2)"));
    }

    @Test
    void shouldNormalizeShorthandOperatorsOutsideQuotedText() {
        assertEquals("lucene AND h2", Luhx.buildLuceneQuery("lucene & h2"));
        assertEquals("lucene OR h2", Luhx.buildLuceneQuery("lucene || h2"));
        assertEquals("lucene NOT h2", Luhx.buildLuceneQuery("lucene !h2"));
        assertEquals("\"rock & roll\" AND music", Luhx.buildLuceneQuery("\"rock & roll\"", "music"));
        assertEquals("literal \\& symbol", Luhx.buildLuceneQuery("literal \\& symbol"));
    }

    @Test
    void shouldCombineOnlyNonBlankTermsWhenBuildingLuceneQuery() {
        assertEquals("lucene AND h2", Luhx.buildLuceneQuery("lucene", null, " ", "h2"));
        assertEquals("java OR h2", Luhx.buildLuceneQuery("java", "OR", null, "h2"));
        assertEquals("lucene AND database", Luhx.buildLuceneQuery("lucene AND database"));
    }

    @Test
    void shouldReturnEmptyQueryForNullOrBlankKeys() {
        assertEquals("", Luhx.buildLuceneQuery());
        assertEquals("", Luhx.buildLuceneQuery((String[]) null));
        assertEquals("", Luhx.buildLuceneQuery("", "   ", null));
    }

    @Test
    void shouldConstructExpectedJdbcUrlInsideTargetFolder() {
        String url = Luhx.getJdbcUrl(tempDir);
        assertTrue(url.startsWith("jdbc:h2:file:"));
        assertTrue(url.contains(Luhx.INDEX_DIR_NAME));
        assertTrue(url.endsWith(Luhx.DB_NAME));
    }

    @Test
    void shouldThrowExceptionWhenIndexingNonExistentOrNonDirectoryPath() throws Exception {
        Path nonExistent = tempDir.resolve("missing_folder");
        assertThrows(IllegalArgumentException.class, () -> Luhx.indexFolder(nonExistent));
        assertThrows(IllegalArgumentException.class, () -> Luhx.indexFolder(null));

        Path regularFile = tempDir.resolve("single_file.txt");
        Files.writeString(regularFile, "content");
        assertThrows(IllegalArgumentException.class, () -> Luhx.indexFolder(regularFile));
    }

    @Test
    void shouldReturnEmptyListWhenSearchingUnindexedFolder() throws Exception {
        List<Luhx.SearchResult> results = Luhx.searchFolder(tempDir, "anything");
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRejectInvalidSearchPageArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> Luhx.searchFolderPage(null, 0, 1, "term"));
        assertThrows(IllegalArgumentException.class,
                () -> Luhx.searchFolderPage(tempDir, -1, 1, "term"));
        assertThrows(IllegalArgumentException.class,
                () -> Luhx.searchFolderPage(tempDir, 0, 0, "term"));
        assertThrows(IllegalArgumentException.class,
                () -> Luhx.searchFolderPage(tempDir, 0, -1, "term"));
    }

}
