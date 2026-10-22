package com.example.JobApplicationIntelligencePlatform;

import com.example.JobApplicationIntelligencePlatform.entity.Application;
import com.example.JobApplicationIntelligencePlatform.repository.ApplicationRepository;
import com.example.JobApplicationIntelligencePlatform.service.ApplicationAnalysisService;
import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=test")
@Testcontainers
class ApplicationAnalysisServiceTest {

	static final String JSON = """
        {"coreRequirements":["Java","Spring Boot"],"responsibilities":["Build APIs"],
         "techStack":["Java","Spring Boot"],"minimumExperienceLevel":"3+ years"}""";

	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
			.withInitScript("init-vector.sql");

	@Container @ServiceConnection(name = "redis")
	static GenericContainer<?> redis =
			new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	@Autowired ApplicationAnalysisService analysisService;
	@Autowired ApplicationRepository repository;
	@Autowired RedisTemplate<String, String> redisTemplate;

	@MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) ChatClient.Builder chatClientBuilder;
	@MockitoBean EmbeddingModel embeddingModel;

	@Test
	void cacheMissThenHit() {
		AtomicInteger llmCalls = new AtomicInteger();
		Mockito.when(chatClientBuilder.build().prompt(any(Prompt.class)).call().content())
				.thenAnswer(inv -> { llmCalls.incrementAndGet(); return JSON; });
		Mockito.when(embeddingModel.embed(anyString())).thenReturn(new float[768]);

		awaitCompleted(submit("Java Spring Boot backend role."));          // MISS
		awaitCompleted(submit("java, SPRING boot -- backend role!!"));     // same after normalizing -> HIT

		assertThat(llmCalls).hasValue(1);
		assertThat(redisTemplate.keys("jd_analysis:*")).hasSize(1);
	}

	private UUID submit(String jd) {
		Application a = new Application();
		a.setRawJd(jd);
		a.setStatus(AnalysisStatus.PENDING);
		UUID id = repository.save(a).getId();
		analysisService.analyzeJobDescription(id);
		return id;
	}

	private void awaitCompleted(UUID id) {
		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(repository.findById(id).orElseThrow().getStatus())
						.isEqualTo(AnalysisStatus.COMPLETED));
	}
}