/**
 * Document extraction, indexing, and full-text search for PDF, Microsoft Office,
 * plain-text, and source-code files.
 *
 * <p>{@link br.com.pointel.luhx.Luhx} provides the indexing and search API,
 * while {@link br.com.pointel.luhx.DocReader} provides a unified interface for
 * extracting document text and metadata. Indexes are stored beside their source
 * documents in a {@code .luhx} subdirectory. Supported file types are determined
 * by filename extension; see the format-specific reader utility classes.
 */
package br.com.pointel.luhx;
