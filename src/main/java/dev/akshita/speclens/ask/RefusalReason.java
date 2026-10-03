package dev.akshita.speclens.ask;

/** Which safeguard stopped the answer. Three layers, cheapest first. */
public enum RefusalReason {

	/** Nothing retrieved was similar enough to the question; the model was not called. */
	NO_RELEVANT_SOURCES,

	/** The model read the sources and said the answer is not in them. */
	NOT_IN_SOURCES,

	/** The model answered without citing any real source, so the answer was withheld. */
	UNGROUNDED_ANSWER

}
