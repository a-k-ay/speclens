package dev.akshita.speclens.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import dev.akshita.speclens.FakeEmbeddingModel;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

	private final FakeEmbeddingModel model = new FakeEmbeddingModel();
	private final EmbeddingService service = new EmbeddingService(model);

	@Test
	void batchesAtMost100TextsPerCallAndKeepsOrder() {
		List<String> passages = IntStream.range(0, 250).mapToObj(i -> "passage number " + i).toList();

		List<float[]> vectors = service.embedPassages("brd.pdf", passages);

		assertThat(model.calls()).isEqualTo(3); // 100 + 100 + 50
		assertThat(vectors).hasSize(250);
		assertThat(vectors.get(137)).isEqualTo(FakeEmbeddingModel.vectorFor("passage number 137"));
	}

	@Test
	void usesGeminiAsymmetricRetrievalPrefixes() {
		assertThat(EmbeddingService.documentInput("sow.pdf", "Fixed fee"))
				.isEqualTo("title: sow.pdf | text: Fixed fee");
		assertThat(EmbeddingService.queryInput("What is the fee?"))
				.isEqualTo("task: question answering | query: What is the fee?");
	}

	@Test
	void formatsVectorForPgvector() {
		assertThat(Vectors.toPgVector(new float[] { 0.5f, -1f, 0f })).isEqualTo("[0.5,-1.0,0.0]");
	}

}
