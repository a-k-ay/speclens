package dev.akshita.speclens.web;

import dev.akshita.speclens.demo.DemoProperties;
import dev.akshita.speclens.ingest.IngestProperties;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the browser UI which features are switched on, so it can hide what isn't. */
@RestController
public class ConfigController {

	/** editingEnabled covers uploads, new projects and deletions (false on the public demo). */
	public record UiConfig(boolean editingEnabled, int maxPages, int maxFileMb, String demoProjectName) {
	}

	private final IngestProperties ingest;
	private final DemoProperties demo;

	public ConfigController(IngestProperties ingest, DemoProperties demo) {
		this.ingest = ingest;
		this.demo = demo;
	}

	@GetMapping("/api/config")
	public UiConfig config() {
		return new UiConfig(ingest.upload().enabled(), ingest.upload().maxPages(), 10, demo.projectName());
	}

}
