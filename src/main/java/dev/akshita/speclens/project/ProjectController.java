package dev.akshita.speclens.project;

import java.util.List;

import jakarta.validation.Valid;

import dev.akshita.speclens.ingest.IngestProperties;
import dev.akshita.speclens.ingest.ReadOnlyModeException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
	private final IngestProperties properties;

	public ProjectController(ProjectRepository projects, IngestProperties properties) {
		this.projects = projects;
		this.properties = properties;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Project create(@Valid @RequestBody CreateProjectRequest request) {
		requireEditable();
		return projects.create(request.name().strip(), request.description());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		requireEditable();
		if (!projects.delete(id)) {
			throw new ProjectNotFoundException(id);
		}
	}

	@GetMapping
	public List<Project> list() {
		return projects.findAll();
	}

	@GetMapping("/{id}")
	public Project get(@PathVariable long id) {
		return projects.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
	}

	/** The public demo is read-only (speclens.upload.enabled=false). */
	private void requireEditable() {
		if (!properties.upload().enabled()) {
			throw new ReadOnlyModeException();
		}
	}

}
