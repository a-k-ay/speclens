package dev.akshita.speclens.agent;

import jakarta.validation.Valid;

import dev.akshita.speclens.ask.AskRequest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/projects/{id}/ask: every question goes through intent routing (AgentService). */
@RestController
public class AgentController {

	private final AgentService agent;

	public AgentController(AgentService agent) {
		this.agent = agent;
	}

	@PostMapping("/api/projects/{projectId}/ask")
	public AgentResponse ask(@PathVariable long projectId, @Valid @RequestBody AskRequest request) {
		return agent.ask(projectId, request.question());
	}

}
