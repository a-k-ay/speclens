package dev.akshita.speclens;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import dev.akshita.speclens.agent.Intent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Stand-in for Gemini chat in tests (CI has no API key).
 *
 * - Classifier prompts (system message mentions the intent classifier) get the classification
 *   a test chose with {@link #classifyAs}; by default DOC_QUESTION with confidence 0.9.
 * - Answer prompts get the reply a test chose with {@link #reply}.
 * - With {@link #callTools}, the fake first calls the given tools through the real Spring AI
 *   tool callbacks attached to the prompt, exactly as Gemini's tool calling would.
 */
public class FakeChatModel implements ChatModel {

	/** One scripted tool call: the tool name and its JSON arguments. */
	public record ToolStep(String tool, String jsonArguments) {
	}

	private static final String DEFAULT_REPLY = "Not found in the uploaded documents.";

	private volatile Function<String, String> responder = userMessage -> DEFAULT_REPLY;
	private volatile String classification;
	private volatile List<ToolStep> toolSteps = List.of();
	private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
	private final List<String> toolResults = new CopyOnWriteArrayList<>();

	public FakeChatModel() {
		reset();
	}

	@Override
	public ChatOptions getOptions() {
		// Tool-calling options make ChatClient attach @Tool callbacks to the prompt.
		return ToolCallingChatOptions.builder().build();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		prompts.add(prompt);
		String reply;
		if (isClassifier(prompt)) {
			reply = classification;
		}
		else {
			runScriptedToolCalls(prompt);
			reply = responder.apply(prompt.getUserMessage().getText());
		}
		return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
	}

	private void runScriptedToolCalls(Prompt prompt) {
		if (toolSteps.isEmpty() || !(prompt.getOptions() instanceof ToolCallingChatOptions options)
				|| options.getToolCallbacks() == null) {
			return;
		}
		for (ToolStep step : toolSteps) {
			ToolCallback callback = options.getToolCallbacks().stream()
					.filter(c -> c.getToolDefinition().name().equals(step.tool())).findFirst()
					.orElseThrow(() -> new IllegalStateException("No tool named " + step.tool() + " on the prompt"));
			toolResults.add(callback.call(step.jsonArguments()));
		}
	}

	private static boolean isClassifier(Prompt prompt) {
		var system = prompt.getSystemMessage();
		return system != null && system.getText() != null && system.getText().contains("intent classifier");
	}

	// ---- test controls ----

	public void reply(String text) {
		this.responder = userMessage -> text;
	}

	public void reply(Function<String, String> responder) {
		this.responder = responder;
	}

	public void classifyAs(Intent intent, double confidence, List<String> requirementIds, List<String> ticketIds) {
		this.classification = """
				{"intent": "%s", "confidence": %s, "requirementIds": %s, "ticketIds": %s, "reason": "test"}"""
				.formatted(intent, confidence, json(requirementIds), json(ticketIds));
	}

	/** A raw classifier reply, e.g. something that isn't JSON at all. */
	public void classifyRaw(String reply) {
		this.classification = reply;
	}

	public void callTools(ToolStep... steps) {
		this.toolSteps = List.of(steps);
	}

	/** Every prompt, classifier prompts included. */
	public List<Prompt> prompts() {
		return prompts;
	}

	/** Prompts that asked for an answer (not the intent classifier). */
	public List<Prompt> answerPrompts() {
		return prompts.stream().filter(p -> !isClassifier(p)).toList();
	}

	public List<String> toolResults() {
		return toolResults;
	}

	public void reset() {
		prompts.clear();
		toolResults.clear();
		toolSteps = List.of();
		reply(DEFAULT_REPLY);
		classifyAs(Intent.DOC_QUESTION, 0.9, List.of(), List.of());
	}

	private static String json(List<String> values) {
		List<String> quoted = new ArrayList<>();
		values.forEach(v -> quoted.add("\"" + v + "\""));
		return "[" + String.join(", ", quoted) + "]";
	}

}
