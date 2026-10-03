package dev.akshita.speclens.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GlossaryExtractorTest {

	private final GlossaryExtractor extractor = new GlossaryExtractor();

	@Test
	void findsDefinitionsWrittenAsLongFormThenAbbreviation() {
		Map<String, String> glossary = extractor.extract(List.of(new PageText(1,
				"Drivers shall capture an electronic proof of delivery (e-POD) in the app. "
						+ "Six users will run user acceptance testing (UAT) in December.")));

		assertThat(glossary)
				.containsEntry("e-POD", "electronic proof of delivery")
				.containsEntry("UAT", "user acceptance testing");
	}

	@Test
	void picksTheShortestMatchingPhrase() {
		Map<String, String> glossary = extractor.extract(List.of(new PageText(1,
				"Invoices shall be raised in Indian Rupees (INR) with GST.")));

		assertThat(glossary).containsEntry("INR", "Indian Rupees");
	}

	@Test
	void ignoresParenthesesThatAreNotAbbreviations() {
		Map<String, String> glossary = extractor.extract(List.of(new PageText(1,
				"Brightline Digital Services (\"Brightline\") will deliver. Fee is INR 4,800,000 (excl). "
						+ "See section (b) and milestone (M1) for details. Attendees: Sandeep Kulkarni (Northwind).")));

		assertThat(glossary).isEmpty();
	}

	@Test
	void firstDefinitionWinsAcrossPages() {
		Map<String, String> glossary = extractor.extract(List.of(
				new PageText(1, "the statement of work (SOW) is signed"),
				new PageText(2, "another document mentions a sample of work (SOW) later")));

		assertThat(glossary).containsExactly(Map.entry("SOW", "statement of work"));
	}

}
