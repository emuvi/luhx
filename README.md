# Luhx

Projeto de exemplo utilizando **H2 Database + Apache Lucene** (`FullTextLucene` / `FTL_INIT`) para indexação incremental e busca textual (Full-Text Search) em arquivos suportados pelo `DocReader` (Textos/Códigos, PDFs e documentos Microsoft Office).

## Compilação

```shell
mvn clean package
```

O JAR executável será gerado em `dist/luhx.jar` (com as dependências em `dist/lib/`).

## Funcionalidades

### 1. Criar ou atualizar o índice de uma pasta (`--index`)

```shell
java -jar dist/luhx.jar --index <path>
```

- Lê recursivamente todos os arquivos suportados por `DocReader.canRead(file)` dentro de `<path>`.
- Salva o banco H2 e o índice invertido do Lucene na própria pasta (dentro de `<path>/.luhx`).
- Caso o índice já exista, realiza a atualização incremental:
  - **Criados**: novos arquivos encontrados na pasta são extraídos e inseridos no índice.
  - **Modificados**: arquivos cuja data de modificação ou tamanho mudaram são reprocessados e atualizados no índice.
  - **Excluídos**: arquivos que estavam no índice mas foram removidos da pasta são excluídos do índice.

### 2. Pesquisar no índice (`--search`)

```shell
java -jar dist/luhx.jar --search <path> <keys>...
```

- Consulta o índice já criado na pasta `<path>` pelas chaves passadas (`<keys>...`) utilizando `FTL_SEARCH_DATA` do H2 + Lucene e imprime na tela os arquivos encontrados ordenados por relevância (BM25 Score).
