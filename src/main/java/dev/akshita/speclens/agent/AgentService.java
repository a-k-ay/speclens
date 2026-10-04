package dev.akshita.speclens.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.akshita.speclens.agent.AgentResponse.Route;
import dev.akshita.speclens.ai.AiUnavailableException;
import dev.akshita.speclens.ask.AskService;
import dev.akshita.speclens.ask.Citation;
import dev.akshita.speclens.ask.RefusalReason;
import dev.akshita.speclens.project.ProjectNotFoundException;
import dev.akshita.speclens.project.ProjectRepository;
import dev.akshita.speclens.retrieval.RetrievedChunk;
import dev.akshita.speclens.tracker.TrackerClient;
import dev.akshita.speclens.tracker.TrackerModels.TestRun;
import dev.akshita.speclens.tracker.TrackerModels.Ticket;
import dev.akshita.speclens.tracker.TrackerTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Classify, then act (docs/adr/0010). Every question is first classified; the intent then
 * decides which systems are consulted:
 *
 * DOC_QUESTION  -> the document pipeline, unchanged
 * LIVE_STATUS   -> the model answers with read-only tracker tools it chooses itself
 * TRACEABILITY  -> documents + tracker calls SpecLens plans itself, combined in one answer
 * OUT_OF_SCOPE  -> refused
 *
 * Low classifier confidence uses the document path. If the tracker is down, the answer
 * falls back to the documents and says live data is unavailable.
 */
@Service
public class AgentService {

	private static final Logger log = LoggerFactory.getLogger(AgentService.class);

	private final ProjectRepository projects;
	private final IntentClassifier classifier;
	private final AskService documents;
	private final TrackerClient tracker;
	private final ChatClient chat;

	public AgentService(ProjectRepository projects, IntentClassifier classifier, AskService documents,
			TrackerClient tracker, ChatClient.Builder chatClientBuilder) {
		this.projects = projects;
		this.classifier = classifier;
		this.documents = documents;
		this.tracker = tracker;
		this.chat = chatClientBuilder.build();
	}

	public AgentResponse ask(long projectId, String question) {
		projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
		String q = question.strip();
		IntentClassification c = classifier.classify(q);
		log.info("Intent {} ({}) for project {}: {}", c.intent(), c.confidence(), projectId, c.reason());

		if (c.confidence() < IntentClassifier.MIN_CONFIDENCE) {
			// Not sure what the question is: the document path is the safe default, with its
			// own three refusal layers.
			return AgentResponse.fromDocuments(documents.ask(projectId, q), c, true, List.of(), null);
		}
		return switch (c.intent()) {
			case DOC_QUESTION -> AgentResponse.fromDocuments(documents.ask(projectId, q), c, false, List.of(), null);
			case OUT_OF_SCOPE -> AgentResponse.refused(q, null, RefusalReason.OUT_OF_SCOPE, Route.NONE, c, List.of(),
					"This question isn't about the project's documents or tracker.");
			case LIVE_STATUS -> liveStatus(projectId, q, c);
			case TRACEABILITY -> traceability(projectId, q, c);
		};
	}

	// ---- LIVE_STATUS: the model picks among read-only tools ----

	private AgentResponse liveStatus(long projectId, String q, IntentClassification c) {
		TrackerTools tools = new TrackerTools(tracker);
		String answer = generate(chat.prompt().system(AgentPrompts.LIVE_SYSTEM).user(liveUserMessage(q, c)).tools(tools));

		if (tools.trackerWasUnavailable() || answer.startsWith(AgentPrompts.TRACKER_UNAVAILABLE)) {
			return documentsBecauseTrackerIsDown(projectId, q, c, tools);
		}
		if (answer.startsWith(AgentPrompts.NOTHING_IN_TRACKER)) {
			return AgentResponse.refused(q, AgentPrompts.NOTHING_IN_TRACKER, RefusalReason.NOT_IN_TRACKER,
					Route.TRACKER, c, tools.calls(), null);
		}
		List<String> refs = trackerRefs(answer);
		List<String> problems = TrackerAnswerCheck.problems(answer, tools.ticketsSeen(), tools.testRunsSeen());
		if (refs.isEmpty() || !problems.isEmpty()) {
			log.warn("Withheld tracker answer ({}): {}", refs.isEmpty() ? "no ticket cited" : problems, answer);
			return AgentResponse.refused(q, null, RefusalReason.UNVERIFIED_TRACKER_DATA, Route.TRACKER, c,
					tools.calls(), "The answer couldn't be verified against the tracker data, so it was withheld.");
		}
		return new AgentResponse(q, answer, true, null, List.of(), Route.TRACKER, c.intent(), c.confidence(), false,
				tools.calls(), refs, null);
	}

	private static String liveUserMessage(String q, IntentClassification c) {
		StringBuilder sb = new StringBuilder("Question: ").append(q).append('\n');
		if (!c.ticketIds().isEmpty()) {
			sb.append("Ticket keys in the question: ").append(String.join(", ", c.ticketIds())).append('\n');
		}
		if (!c.requirementIds().isEmpty()) {
			sb.append("Requirement IDs in the question: ").append(String.join(", ", c.requirementIds())).append('\n');
		}
		return sb.append("For \"this sprint\" use the sprint value 'current'.").toString();
	}

	// ---- TRACEABILITY: SpecLens plans the tracker calls ----

	private AgentResponse traceability(long projectId, String q, IntentClassification c) {
		List<RetrievedChunk> sources = documents.retrieveSources(projectId, q);
		List<String> requirements = RequirementFinder.find(c.requirementIds(), q, sources);
		if (requirements.isEmpty()) {
			return AgentResponse.fromDocuments(documents.ask(projectId, q), c, false, List.of(),
					"No requirement ID could be identified to look up in the tracker, so this answer uses the documents only.");
		}

		TrackerTools tools = new TrackerTools(tracker);
		Map<String, List<Ticket>> tickets = new LinkedHashMap<>();
		Map<String, List<TestRun>> runs = new LinkedHashMap<>();
		for (String id : requirements) {
			tickets.put(id, tools.getTicketsForRequirement(id).tickets());
			if (tools.trackerWasUnavailable()) {
				return documentsBecauseTrackerIsDown(projectId, q, c, tools);
			}
			runs.put(id, tools.getUatResults(id).testRuns());
		}

		String userMessage = documents.documentContext(projectId, q, sources)
				+ AgentPrompts.trackerBlock(tickets, runs) + "\nQuestion: " + q;
		log.debug("Traceability prompt:\n{}", userMessage);
		String answer = generate(chat.prompt().system(AgentPrompts.TRACE_SYSTEM).user(userMessage));

		if (AskService.isRefusal(answer)) {
			return AgentResponse.refused(q, null, RefusalReason.NOT_IN_SOURCES, Route.DOCUMENTS_AND_TRACKER, c,
					tools.calls(), null);
		}
		List<Citation> citations = AskService.citationsFor(answer, sources);
		List<String> refs = trackerRefs(answer);
		List<String> problems = TrackerAnswerCheck.problems(answer, tools.ticketsSeen(), tools.testRunsSeen());
		if ((citations.isEmpty() && refs.isEmpty()) || !problems.isEmpty()) {
			log.warn("Withheld traceability answer ({}): {}", problems.isEmpty() ? "nothing cited" : problems, answer);
			return AgentResponse.refused(q, null,
					problems.isEmpty() ? RefusalReason.UNGROUNDED_ANSWER : RefusalReason.UNVERIFIED_TRACKER_DATA,
					Route.DOCUMENTS_AND_TRACKER, c, tools.calls(),
					"The answer couldn't be verified against the documents and tracker data, so it was withheld.");
		}
		return new AgentResponse(q, answer, true, null, citations, Route.DOCUMENTS_AND_TRACKER, c.intent(),
				c.confidence(), false, tools.calls(), refs, null);
	}

	// ---- shared ----

	private AgentResponse documentsBecauseTrackerIsDown(long projectId, String q, IntentClassification c,
			TrackerTools tools) {
		log.warn("Tracker unavailable; answering from documents only (project {})", projectId);
		return AgentResponse.fromDocuments(documents.ask(projectId, q), c, false, tools.calls(),
				AgentPrompts.TRACKER_UNAVAILABLE + " This answer uses the documents only.");
	}

	private static String generate(ChatClient.ChatClientRequestSpec request) {
		String answer;
		try {
			answer = request.call().content();
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException("Answer generation failed; please try again", ex);
		}
		if (answer == null || answer.isBlank()) {
			throw new AiUnavailableException("The model returned an empty answer", null);
		}
		return answer.strip();
	}

	/** Ticket keys and test ids the answer cites, in order of first mention. */
	private static List<String> trackerRefs(String answer) {
		List<String> refs = new java.util.ArrayList<>(TrackerAnswerCheck.find(TrackerAnswerCheck.TICKET, answer));
		refs.addAll(TrackerAnswerCheck.find(TrackerAnswerCheck.TEST, answer));
		return refs;
	}

}
