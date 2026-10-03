package dev.akshita.speclens.ask;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

	private final AskService askService;

	public AskController(AskService askService) {
		this.askService = askService;
	}

	@PostMapping("/api/projects/{projectId}/ask")
	public AskResponse ask(@PathVariable long projectId, @Valid @RequestBody AskRequest request) {
		return askService.ask(projectId, request.question());
	}

}
