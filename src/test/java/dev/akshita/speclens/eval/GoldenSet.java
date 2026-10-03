package dev.akshita.speclens.eval;

import java.nio.file.Path;
import java.util.List;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** eval/golden-set.json as Java records. */
record GoldenSet(String description, List<String> documents, List<Answerable> answerable,
		List<Unanswerable> unanswerable) {

	record Answerable(String id, String question, List<Location> expected, List<String> answerMustContain) {

		boolean isExpected(String document, int page) {
			return expected.stream().anyMatch(l -> l.document().equals(document) && l.page() == page);
		}

	}

	record Unanswerable(String id, String question) {
	}

	record Location(String document, int page) {

		@Override
		public String toString() {
			return document + " p." + page;
		}

	}

	static GoldenSet load(Path path) {
		return JsonMapper.builder()
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build()
				.readValue(path.toFile(), GoldenSet.class);
	}

}
