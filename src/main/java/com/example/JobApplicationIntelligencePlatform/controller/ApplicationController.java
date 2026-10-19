package com.example.JobApplicationIntelligencePlatform.controller;

import com.example.JobApplicationIntelligencePlatform.dto.ApplicationRequest;
import com.example.JobApplicationIntelligencePlatform.entity.Application;
import com.example.JobApplicationIntelligencePlatform.repository.ApplicationRepository;
import com.example.JobApplicationIntelligencePlatform.service.ApplicationAnalysisService;
import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}