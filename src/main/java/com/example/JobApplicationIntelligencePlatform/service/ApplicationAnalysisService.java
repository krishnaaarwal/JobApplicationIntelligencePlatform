package com.example.JobApplicationIntelligencePlatform.service;

import com.example.JobApplicationIntelligencePlatform.entity.Application;
import com.example.JobApplicationIntelligencePlatform.entity.JdAnalysis;
import com.example.JobApplicationIntelligencePlatform.repository.ApplicationRepository;
import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationAnalysisService {
    private final ApplicationRepository applicationRepository;
    private final ChatClient chatClient;

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

            application.setExtractedData(analysis);
            application.setStatus(AnalysisStatus.COMPLETED);
            applicationRepository.save(application);
        }catch (Exception e) {
            log.error("AI Analysis failed for application: {}", applicationId, e);
            application.setStatus(AnalysisStatus.FAILED);
            applicationRepository.save(application);
        }
    }

}
