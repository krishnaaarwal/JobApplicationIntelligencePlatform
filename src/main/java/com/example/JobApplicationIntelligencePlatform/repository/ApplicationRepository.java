package com.example.JobApplicationIntelligencePlatform.repository;

import com.example.JobApplicationIntelligencePlatform.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
}
