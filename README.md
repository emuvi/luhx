# Luhx

Luhx is a Java library and command-line application for indexing and searching documents with Apache Lucene and H2. It extracts text from supported text and code files, PDFs, and Microsoft Office documents.

## Requirements

- Java 21 or later
- Maven

## Build

```shell
mvn clean package
```

The executable JAR is created at `dist/luhx.jar`; its runtime dependencies are copied to `dist/lib/`.

## API documentation

Generate the HTML Javadocs with:

```shell
mvn javadoc:javadoc
```

Open `target/site/apidocs/index.html` to browse the API. The package also attaches
a Javadoc JAR when building with `mvn package`.

## Command-line usage

Both commands take the folder to index or search. The index is stored in a `.luhx` subfolder inside it.

### Create or update an index

```shell
java -jar dist/luhx.jar --index <folder>
```

Indexing scans the folder recursively. On subsequent runs, Luhx adds new files, reindexes files whose size or modification time changed, removes entries for deleted files, and leaves unchanged files alone. The command reports counts for each category.

### Search an index

```shell
java -jar dist/luhx.jar --search <folder> <term> [<term>...]
```

Run `--index` before searching. Results are ordered by Lucene's BM25 relevance score. When multiple terms are supplied without Boolean operators, they are combined with `AND`.

Examples:

```shell
java -jar dist/luhx.jar --index ./documents
java -jar dist/luhx.jar --search ./documents lucene
java -jar dist/luhx.jar --search ./documents lucene AND indexing
```

## Use as a library

The public API is `br.com.pointel.luhx.Luhx`. `indexFolder(Path)` indexes or updates a folder and returns `IndexStats`; `searchFolder(Path, String...)` returns matching files as `SearchResult` values without printing to the console.

```java
import br.com.pointel.luhx.Luhx;
import java.nio.file.Path;
import java.util.List;

Luhx.IndexStats stats = Luhx.indexFolder(Path.of("documents"));
List<Luhx.SearchResult> results =
        Luhx.searchFolder(Path.of("documents"), "lucene", "h2");
```
