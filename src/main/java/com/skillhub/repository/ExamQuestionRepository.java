package com.skillhub.repository;

import com.skillhub.model.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findByCourseId(Long courseId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ExamQuestion eq WHERE eq.course.id = :courseId")
    void deleteByCourseId(@Param("courseId") Long courseId);
}
