package dev.akshita.speclens.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * speclens.demo.* settings.
 *
 * @param seed when true, load the sample documents into the demo project at startup
 * @param projectName name of the demo project (the UI selects it by default)
 * @param documentsDir folder with the fictional sample PDF/DOCX files
 */
@ConfigurationProperties("speclens.demo")
public record DemoProperties(boolean seed, String projectName, String documentsDir) {
}
