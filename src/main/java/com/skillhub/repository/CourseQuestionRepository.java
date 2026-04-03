package com.skillhub.repository;

import com.skillhub.model.Course;
import com.skillhub.model.CourseQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseQuestionRepository extends JpaRepository<CourseQuestion, Long> {
    List<CourseQuestion> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    @org.springframework.data.jpa.repository.Query("SELECT q FROM CourseQuestion q WHERE q.course.instructor.email = :email ORDER BY q.createdAt DESC")
    List<CourseQuestion> findByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);

    List<CourseQuestion> findByCourseAndTitleContainingIgnoreCase(Course course, String keyword);
}
