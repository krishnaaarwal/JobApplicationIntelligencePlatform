package com.example.JobApplicationIntelligencePlatform.service;

import com.example.JobApplicationIntelligencePlatform.entity.Application;
import com.example.JobApplicationIntelligencePlatform.entity.JdAnalysis;
import com.example.JobApplicationIntelligencePlatform.repository.ApplicationRepository;
import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@Service
@Slf4j
public class ApplicationAnalysisService {

    private final ApplicationRepository applicationRepository;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ApplicationAnalysisService(ApplicationRepository applicationRepository,
                                      ChatClient.Builder chatClientBuilder,
                                      EmbeddingModel embeddingModel,
                                      StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Async
    public void analyzeJobDescription(UUID applicationId) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();

        try {
            // 1. Normalize (Unicode safe) and Hash
            String normalizedJd = app.getRawJd().replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
            String cacheKey = "jd_analysis:" + generateHash(normalizedJd);

            String cachedJson = null;
            try {
                cachedJson = redisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                log.warn("Redis GET failed, proceeding to LLM as cache MISS", e);
            }

            JdAnalysis analysis;

            if (cachedJson != null) {
                log.info("Cache HIT for Application: {}", applicationId);
                analysis = objectMapper.readValue(cachedJson, JdAnalysis.class);
            } else {
                log.info("Cache MISS. Calling LLM for Application: {}", applicationId);
                var outputConverter = new BeanOutputConverter<>(JdAnalysis.class);

                String promptText = """
                    Analyze the following Job Description.
                    Extract the core requirements, daily responsibilities, technical stack, and experience level.
                    If any field is missing, infer it or leave the array empty.
                    
                    {format}
                    
                    Job Description:
                    {jd}
                    """;

                PromptTemplate promptTemplate = new PromptTemplate(promptText);
                promptTemplate.add("jd", app.getRawJd());
                promptTemplate.add("format", outputConverter.getFormat());

                String response = chatClient.prompt(promptTemplate.create()).call().content();
                analysis = outputConverter.convert(response);

                try {
                    redisTemplate.opsForValue().set(
                            cacheKey,
                            objectMapper.writeValueAsString(analysis),
                            Duration.ofDays(7)
                    );
                } catch (Exception e) {
                    log.warn("Redis SET failed, skipping cache storage", e);
                }
            }

            // 2. Generate Vector
            String textToEmbed = String.join(", ", analysis.coreRequirements()) +
                    " " + String.join(", ", analysis.techStack());

            float[] vector = embeddingModel.embed(textToEmbed);

            // 3. Update DB
            app.setJdEmbedding(vector);
            app.setExtractedData(analysis);
            app.setStatus(AnalysisStatus.COMPLETED);
            applicationRepository.save(app);
            log.info("Successfully analyzed and indexed Application: {}", applicationId);

        } catch (Exception e) {
            log.error("AI Analysis failed for application: {}", applicationId, e);
            app.setStatus(AnalysisStatus.FAILED);
            applicationRepository.save(app);
        }
    }

    public Double calculateMatchScore(UUID applicationId, String userSkills) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        if (app.getJdEmbedding() == null) {
            throw new IllegalStateException("Job description has not been analyzed yet.");
        }

        float[] userVector = embeddingModel.embed(userSkills);
        Double score = applicationRepository.calculateMatchScore(applicationId, Arrays.toString(userVector));

        // Return rounded to 2 decimal places
        return score != null ? Math.round(score * 100.0) / 100.0 : 0.0;
    }

    public Flux<String> generateInterviewPrepStream(UUID applicationId, String userSkills) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();

        if (app.getExtractedData() == null) {
            throw new IllegalStateException("Job description has not been analyzed yet.");
        }

        String promptText = """
            You are an expert technical interviewer.
            
            Job Requirements:
            {requirements}
            
            Candidate's Current Skills:
            {skills}
            
            Task: Compare the candidate's skills against the job requirements. 
            Identify the gaps (what the candidate is missing), and generate 3 tough 
            technical interview questions specifically targeting those weak areas.
            """;

        PromptTemplate promptTemplate = new PromptTemplate(promptText);
        promptTemplate.add("requirements", String.join(", ", app.getExtractedData().coreRequirements()));
        promptTemplate.add("skills", userSkills);

        return chatClient.prompt(promptTemplate.create())
                .stream()
                .content();
    }

    private String generateHash(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public EmbeddingModel getEmbeddingModel() { return this.embeddingModel; }
}