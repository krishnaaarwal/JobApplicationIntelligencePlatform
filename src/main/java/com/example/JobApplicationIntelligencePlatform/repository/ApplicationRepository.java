package com.example.JobApplicationIntelligencePlatform.repository;

import com.example.JobApplicationIntelligencePlatform.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    // <=> calculates cosine distance.
    // We cast the string back to a vector inside Postgres to avoid Hibernate type-mapping issues.
    @Query(value = """
        SELECT * FROM applications a 
        WHERE a.id != :targetId 
        AND a.jd_embedding IS NOT NULL
        ORDER BY a.jd_embedding <=> cast(:embeddingStr as vector) 
        LIMIT 5
        """, nativeQuery = true)
    List<Application> findSimilarApplications(
            @Param("targetId") UUID targetId,
            @Param("embeddingStr") String embeddingStr
    );

    // Returns a match score between 0.0 (no match) and 1.0 (perfect match)
    // 1 - cosine_distance = cosine_similarity
    @Query(value = """
        SELECT (1 - (jd_embedding <=> cast(:userVector as vector))) AS match_score 
        FROM applications WHERE id = :applicationId
        """, nativeQuery = true)
    Double calculateMatchScore(@Param("applicationId") UUID applicationId, @Param("userVector") String userVector);
}
