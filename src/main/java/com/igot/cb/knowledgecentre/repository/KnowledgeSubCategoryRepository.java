package com.igot.cb.knowledgecentre.repository;

import com.igot.cb.knowledgecentre.entity.KnowledgeSubCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeSubCategoryRepository extends JpaRepository<KnowledgeSubCategoryEntity, String> {

    @Query(value = """
        SELECT * FROM knowledge_sub_category
        WHERE sub_category_data->>'title' = :title
        AND sub_category_data->>'categoryId' = :categoryId
        """, nativeQuery = true)
    Optional<KnowledgeSubCategoryEntity> findByTitleAndCategoryId(
            @Param("title") String title,
            @Param("categoryId") String categoryId
    );

    @Query(value = """
        SELECT * FROM knowledge_sub_category
        WHERE sub_category_data->>'title' = :title
        AND sub_category_data->>'categoryId' = :categoryId
        AND sub_category_id != :id
        """, nativeQuery = true)
    Optional<KnowledgeSubCategoryEntity> findByTitleAndCategoryIdAndIdNot(
            @Param("title") String title,
            @Param("categoryId") String categoryId,
            @Param("id") String id
    );
}
