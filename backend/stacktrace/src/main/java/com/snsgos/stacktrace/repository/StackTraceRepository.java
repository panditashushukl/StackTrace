package com.snsgos.stacktrace.repository;

import com.snsgos.stacktrace.entity.StackTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StackTraceRepository extends JpaRepository<StackTrace, UUID> {

    @Query(value = """
            SELECT * FROM stack_trace_analyses
            WHERE (:language = '' OR LOWER(detected_language) = LOWER(:language))
              AND (:keyword = '' OR raw_stack_trace ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR error_type ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR detected_language ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR CAST(analysis_result AS TEXT) ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM stack_trace_analyses
            WHERE (:language = '' OR LOWER(detected_language) = LOWER(:language))
              AND (:keyword = '' OR raw_stack_trace ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR error_type ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR detected_language ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR CAST(analysis_result AS TEXT) ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
            """,
            nativeQuery = true)
    Page<StackTrace> searchAllHistory(@Param("keyword") String keyword,
                                      @Param("language") String language,
                                      Pageable pageable);

    @Query(value = """
            SELECT * FROM stack_trace_analyses
            WHERE user_id = :userId
              AND (:language = '' OR LOWER(detected_language) = LOWER(:language))
              AND (:keyword = '' OR raw_stack_trace ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR error_type ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR detected_language ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR CAST(analysis_result AS TEXT) ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM stack_trace_analyses
            WHERE user_id = :userId
              AND (:language = '' OR LOWER(detected_language) = LOWER(:language))
              AND (:keyword = '' OR raw_stack_trace ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR error_type ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR detected_language ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR CAST(analysis_result AS TEXT) ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
            """,
            nativeQuery = true)
    Page<StackTrace> searchUserHistory(@Param("userId") UUID userId,
                                       @Param("keyword") String keyword,
                                       @Param("language") String language,
                                       Pageable pageable);

    @Query(value = """
            SELECT * FROM stack_trace_analyses
            WHERE session_id = :sessionId
              AND user_id IS NULL
              AND (:language = '' OR LOWER(detected_language) = LOWER(:language))
              AND (:keyword = '' OR raw_stack_trace ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR error_type ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR detected_language ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR CAST(analysis_result AS TEXT) ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM stack_trace_analyses
            WHERE session_id = :sessionId
              AND user_id IS NULL
              AND (:language = '' OR LOWER(detected_language) = LOWER(:language))
              AND (:keyword = '' OR raw_stack_trace ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR error_type ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR detected_language ILIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                   OR CAST(analysis_result AS TEXT) ILIKE CONCAT('%', :keyword, '%') ESCAPE '!')
            """,
            nativeQuery = true)
    Page<StackTrace> searchSessionHistory(@Param("sessionId") String sessionId,
                                          @Param("keyword") String keyword,
                                          @Param("language") String language,
                                          Pageable pageable);
}
