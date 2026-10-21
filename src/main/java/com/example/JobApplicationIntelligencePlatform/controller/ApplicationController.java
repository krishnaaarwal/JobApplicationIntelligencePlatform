package com.example.JobApplicationIntelligencePlatform.controller;

import com.example.JobApplicationIntelligencePlatform.dto.ApplicationRequest;
import com.example.JobApplicationIntelligencePlatform.entity.Application;
import com.example.JobApplicationIntelligencePlatform.repository.ApplicationRepository;
import com.example.JobApplicationIntelligencePlatform.service.ApplicationAnalysisService;
import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository repository;
    private final ApplicationAnalysisService analysisService;

    @PostMapping
    public ResponseEntity<Map<String, UUID>> submitApplication(@RequestBody ApplicationRequest request) {
        // 1. Save initial PENDING state
        Application app = new Application();
        app.setCompanyName(request.getCompanyName());
        app.setRawJd(request.getRawJd());
        app.setStatus(AnalysisStatus.PENDING);
        app = repository.save(app);

        analysisService.analyzeJobDescription(app.getId());

        return ResponseEntity.accepted().body(Map.of("applicationId", app.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Application> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(repository.findById(id).orElseThrow());
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<List<Application>> getSimilarJobs(@PathVariable UUID id) {
        Application targetApp = repository.findById(id).orElseThrow();

        if (targetApp.getJdEmbedding() == null) {
            return ResponseEntity.badRequest().build();
        }

        String vectorString = targetApp.getJdEmbedding().toString();

        //Execute the cosine similarity search
        List<Application> similarApps = repository.findSimilarApplications(id, vectorString);

        return ResponseEntity.ok(similarApps);
    }

    @GetMapping(value = "/{id}/interview-prep", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamInterviewPrep(
            @PathVariable UUID id,
            @RequestParam String skills) {

        return analysisService.generateInterviewPrepStream(id, skills);
    }
}