package com.example.JobApplicationIntelligencePlatform.service;

import com.example.JobApplicationIntelligencePlatform.entity.Application;
import com.example.JobApplicationIntelligencePlatform.entity.JdAnalysis;
import com.example.JobApplicationIntelligencePlatform.repository.ApplicationRepository;
import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationAnalysisService {
    private final ApplicationRepository applicationRepository;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    @Async
    public void analyzeJobDescription(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId).orElseThrow();

        try{
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
            promptTemplate.add("jd", application.getRawJd());
            promptTemplate.add("format", outputConverter.getFormat());

            String response = chatClient.prompt(promptTemplate.create()).call().content();

            JdAnalysis analysis = outputConverter.convert(response);

            String textToEmbed = String.join(", ", analysis.coreRequirements()) +
                    " " +
                    String.join(", ", analysis.techStack());

            float[] vector = embeddingModel.embed(textToEmbed);

            application.setJdEmbedding(new PGvector(vector));
            application.setExtractedData(analysis);
            application.setStatus(AnalysisStatus.COMPLETED);
            applicationRepository.save(application);
        }catch (Exception e) {
            log.error("AI Analysis failed for application: {}", applicationId, e);
            application.setStatus(AnalysisStatus.FAILED);
            applicationRepository.save(application);
        }
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

}
