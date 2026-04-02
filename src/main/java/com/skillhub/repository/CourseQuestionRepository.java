package com.skillhub.repository;

import com.skillhub.model.Course;
import com.skillhub.model.CourseQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseQuestionRepository extends JpaRepository<CourseQuestion, Long> {
    List<CourseQuestion> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<CourseQuestion> findByCourseAndTitleContainingIgnoreCase(Course course, String keyword);
}
