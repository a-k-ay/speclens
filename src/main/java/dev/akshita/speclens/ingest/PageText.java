package dev.akshita.speclens.ingest;

/** Text of one page. Page numbers start at 1, as a reader would cite them. */
public record PageText(int pageNumber, String text) {
}
