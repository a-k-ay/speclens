package dev.akshita.speclens.project;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectRepository projects;

	public ProjectController(ProjectRepository projects) {
		this.projects = projects;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Project create(@Valid @RequestBody CreateProjectRequest request) {
		return projects.create(request.name().strip(), request.description());
	}

	@GetMapping
	public List<Project> list() {
		return projects.findAll();
	}

	@GetMapping("/{id}")
	public Project get(@PathVariable long id) {
		return projects.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
	}

}
