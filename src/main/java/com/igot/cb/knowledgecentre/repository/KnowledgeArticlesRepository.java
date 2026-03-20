package com.igot.cb.knowledgecentre.repository;

import com.igot.cb.knowledgecentre.entity.KnowledgeArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeArticlesRepository extends JpaRepository<KnowledgeArticleEntity, String> {

    @Query(value = """
        SELECT * FROM knowledge_articles
        WHERE articles->>'title' = :title
        AND articles->>'subCategoryId' = :subCategoryId
        """, nativeQuery = true)
    Optional<KnowledgeArticleEntity> findByTitleAndSubCategoryId(
            @Param("title") String title,
            @Param("subCategoryId") String subCategoryId
    );

    @Query(value = """
        SELECT * FROM knowledge_articles
        WHERE articles->>'title' = :title
        AND articles->>'subCategoryId' = :subCategoryId
        AND article_id != :id
        """, nativeQuery = true)
    Optional<KnowledgeArticleEntity> findByTitleAndSubCategoryIdAndIdNot(
            @Param("title") String title,
            @Param("subCategoryId") String subCategoryId,
            @Param("id") String id
    );
}
