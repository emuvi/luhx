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

## Windows distributions

Run `dist-cmd.bat` to build a self-contained command-line application, or
`dist-gui.bat` to build the graphical interface. Both scripts require JDK 21 or
later, Maven, and WiX Toolset 3.x on Windows. The command-line distribution
includes a console; the GUI distribution does not. Each script analyzes the app
and its dependencies and includes only the Java runtime modules they require.

The outputs are kept separate:

- `dist/cmd/luhx-cmd-win.zip` and `dist/cmd/luhx-cmd-1.0.msi`
- `dist/gui/luhx-gui-win.zip` and `dist/gui/luhx-gui-1.0.msi`

The graphical interface saves and restores the positions and sizes of its windows,
including dialogs and the folder chooser, in `config.ini` in the application's
working directory. The main window, search results, and indexing monitor are
independent maximizable windows. Search results show the highest-ranked matches
first and load further results in batches of 50.

## API documentation

Generate the HTML Javadocs with:

```shell
mvn javadoc:javadoc
```

Open `target/site/apidocs/index.html` to browse the API. The package also attaches
a Javadoc JAR when building with `mvn package`.

## Command-line usage

Both commands take the folder to index or search. The index is stored in a `.luhx` subfolder inside it.

### Print the version

```shell
java -jar dist/luhx.jar --version
```

### Create or update an index

```shell
java -jar dist/luhx.jar --index <folder>
```

Indexing scans the folder recursively. On subsequent runs, Luhx adds new files,
reindexes files whose size or modification time changed, removes entries for
deleted files, and leaves unchanged files alone. If an individual file cannot
be indexed, its error is printed and indexing continues with the remaining files.
The command reports counts for each successfully processed category.

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
java -jar dist/luhx.jar --search ./documents "lucene & indexing"
```

### Boolean operators

The command line and the graphical search box use the same Lucene query syntax. Besides the words `AND`, `OR` and `NOT`, these shortcuts are accepted:

| Type | Meaning | Example |
|------|---------|---------|
| `&` or `&&` | `AND` | `lucene & indexing` |
| `\|` or `\|\|` | `OR` | `lucene \| h2` |
| `!` | `NOT` | `lucene !h2` |

Text inside double quotes is left untouched, so `"rock & roll"` searches for that exact phrase. Other Lucene syntax (wildcards, fields, grouping with parentheses) is passed through as is. In the graphical interface, type the whole query in the search box; terms without an operator are combined with `AND`.

## Use as a library

The public API is `br.com.pointel.luhx.Luhx`. `indexFolder(Path)` indexes or updates a folder and returns `IndexStats`; `indexFolder(Path, IndexObserver)` does the same while reporting progress (`onStart`, `onFile` with an `IndexAction`, `onError`, `onFinish`) to an optional observer; `searchFolder(Path, String...)` returns matching files as `SearchResult` values without printing to the console, and `searchFolderPage(Path, offset, limit, String...)` returns one ranked page of matches.

```java
import br.com.pointel.luhx.Luhx;
import java.nio.file.Path;
import java.util.List;

Luhx.IndexStats stats = Luhx.indexFolder(Path.of("documents"));
List<Luhx.SearchResult> results =
        Luhx.searchFolder(Path.of("documents"), "lucene", "h2");
```
