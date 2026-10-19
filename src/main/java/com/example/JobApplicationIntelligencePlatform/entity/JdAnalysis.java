package com.example.JobApplicationIntelligencePlatform.entity;

import java.util.List;

public record JdAnalysis(
        List<String> coreRequirements,
        List<String> responsibilities,
        List<String> techStack,
        String minimumExperienceLevel
) {}