package dev.akshita.speclens.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.akshita.speclens.retrieval.RetrievedChunk;

/**
 * Decides which requirement IDs a traceability question is about, so SpecLens knows what to
 * look up in the tracker:
 *
 * 1. IDs named in the question come first.
 * 2. A change request expands to the requirements it changes: the BR IDs mentioned in the
 *    same retrieved passage as the CR (e.g. CR-003 changes BR-8.1).
 * 3. With no ID in the question, the requirement whose own line in the retrieved passages
 *    shares the most words with the question (requirements are one per line, e.g.
 *    "BR-7.4 When there is no mobile network...").
 */
final class RequirementFinder {

	static final int MAX_REQUIREMENTS = 3;

	private static final Pattern ID = Pattern.compile("\\b(?:BR-\\d+\\.\\d+|NFR-\\d+|CR-\\d{3})\\b");
	private static final Pattern BR = Pattern.compile("\\bBR-\\d+\\.\\d+\\b");
	private static final Pattern WORD = Pattern.compile("[a-z0-9][a-z0-9-]*");
	private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "has", "have", "been", "was", "were",
			"are", "is", "it", "its", "this", "that", "with", "from", "what", "which", "when", "does", "did", "built",
			"tested", "test", "passed", "pass", "uat", "requirement", "requirements", "rule", "status", "yet", "done",
			"implemented", "shall", "system", "will", "how", "who", "why", "where", "there", "any", "all");

	private RequirementFinder() {
	}

	static List<String> find(List<String> fromQuestion, String question, List<RetrievedChunk> sources) {
		Set<String> ids = new LinkedHashSet<>(fromQuestion);

		for (String id : fromQuestion) {
			if (id.startsWith("CR-")) {
				changedByChangeRequest(id, sources).ifPresent(ids::add);
			}
		}

		if (ids.isEmpty()) {
			ids.addAll(bestMatchingLines(question, sources));
		}
		return ids.stream().limit(MAX_REQUIREMENTS).toList();
	}

	/**
	 * The BR most often mentioned in the same retrieved passages as the change request. Many
	 * pages mention a CR alongside several BRs (a UAT plan lists many); the requirement the CR
	 * actually changes is the one that keeps appearing with it. Ties go to the first seen.
	 */
	private static java.util.Optional<String> changedByChangeRequest(String cr, List<RetrievedChunk> sources) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (RetrievedChunk source : sources) {
			String text = source.chunk().content();
			if (text.contains(cr)) {
				all(BR, text).forEach(br -> counts.merge(br, 1, Integer::sum));
			}
		}
		return counts.entrySet().stream()
				.reduce((best, next) -> next.getValue() > best.getValue() ? next : best)
				.map(Map.Entry::getKey);
	}

	/** IDs whose own statement overlaps the question most; ties are all kept, best sources first. */
	private static List<String> bestMatchingLines(String question, List<RetrievedChunk> sources) {
		Set<String> questionWords = contentWords(question);
		Map<String, Integer> scoreById = new LinkedHashMap<>();
		for (RetrievedChunk source : sources.subList(0, Math.min(3, sources.size()))) {
			// One segment per requirement statement: cut before every ID, so this works whether
			// or not the passage kept its line breaks.
			for (String line : source.chunk().content().split("\\n|(?=\\b(?:BR-\\d+\\.\\d+|NFR-\\d+|CR-\\d{3})\\b)")) {
				List<String> idsOnLine = all(ID, line);
				if (idsOnLine.isEmpty()) {
					continue;
				}
				int score = overlap(questionWords, contentWords(line));
				for (String id : idsOnLine) {
					scoreById.merge(id, score, Math::max);
				}
			}
		}
		int best = scoreById.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		if (best == 0) {
			return List.of();
		}
		List<String> winners = new ArrayList<>();
		scoreById.forEach((id, score) -> {
			if (score == best) {
				winners.add(id);
			}
		});
		return winners;
	}

	/** Words match when they share their first five letters, a crude stem ("e-pods" ~ "e-pod"). */
	private static int overlap(Set<String> question, Set<String> line) {
		int score = 0;
		for (String q : question) {
			String stem = q.substring(0, Math.min(5, q.length()));
			if (line.stream().anyMatch(w -> w.startsWith(stem))) {
				score++;
			}
		}
		return score;
	}

	private static Set<String> contentWords(String text) {
		Set<String> words = new LinkedHashSet<>();
		Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
		while (m.find()) {
			String w = m.group();
			if (w.length() >= 3 && !STOP_WORDS.contains(w) && !ID.matcher(w.toUpperCase(Locale.ROOT)).matches()) {
				words.add(w);
			}
		}
		return words;
	}

	private static List<String> all(Pattern pattern, String text) {
		Set<String> found = new LinkedHashSet<>();
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			found.add(m.group());
		}
		return List.copyOf(found);
	}

}
