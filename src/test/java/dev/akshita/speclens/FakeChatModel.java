package dev.akshita.speclens;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Stand-in for Gemini chat in tests. Tests choose the reply (fixed text or a function of
 * the user message) and can inspect every prompt that was sent.
 */
public class FakeChatModel implements ChatModel {

	private volatile Function<String, String> responder = userMessage -> "Not found in the uploaded documents.";
	private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

	@Override
	public ChatResponse call(Prompt prompt) {
		prompts.add(prompt);
		String reply = responder.apply(prompt.getUserMessage().getText());
		return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
	}

	public void reply(String text) {
		this.responder = userMessage -> text;
	}

	public void reply(Function<String, String> responder) {
		this.responder = responder;
	}

	public List<Prompt> prompts() {
		return prompts;
	}

	public void reset() {
		prompts.clear();
		reply("Not found in the uploaded documents.");
	}

}
