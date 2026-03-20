package com.igot.cb.knowledgecentre.repository;

import com.igot.cb.knowledgecentre.entity.KnowledgeCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategoryEntity, String> {

    @Query(value = "SELECT * FROM knowledge_category WHERE category_data->>'title' = :title", nativeQuery = true)
    Optional<KnowledgeCategoryEntity> findByTitle(@Param("title") String title);

    @Query(value = "SELECT * FROM knowledge_category WHERE category_data->>'title' = :title AND category_id != :id", nativeQuery = true)
    Optional<KnowledgeCategoryEntity> findByTitleAndIdNot(@Param("title") String title, @Param("id") String id);
}
