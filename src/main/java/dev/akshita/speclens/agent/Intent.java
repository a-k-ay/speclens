package dev.akshita.speclens.agent;

/** What a question is asking for; decides which systems SpecLens consults. See docs/adr/0010. */
public enum Intent {

	/** What the documents say or agreed. Uses document retrieval only. */
	DOC_QUESTION,

	/** The current state of work. Uses the project tracker only. */
	LIVE_STATUS,

	/** Whether something agreed in the documents was built and tested. Uses both. */
	TRACEABILITY,

	/** Unrelated to the project. Refused. */
	OUT_OF_SCOPE

}
