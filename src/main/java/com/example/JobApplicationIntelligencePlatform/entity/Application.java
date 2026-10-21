package com.example.JobApplicationIntelligencePlatform.entity;

import com.example.JobApplicationIntelligencePlatform.types.AnalysisStatus;
import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "applications")
@Data
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String rawJd;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JdAnalysis extractedData;

    @Column(columnDefinition = "vector(768)")
    private PGvector jdEmbedding;
}