package dev.akshita.speclens.document;

/** An abbreviation defined in a document, e.g. e-POD = electronic proof of delivery. */
public record GlossaryTerm(String shortForm, String longForm, String documentName) {
}
