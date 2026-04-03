package com.skillhub.repository;

import com.skillhub.model.CourseAnswer;
import com.skillhub.model.CourseQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseAnswerRepository extends JpaRepository<CourseAnswer, Long> {
    List<CourseAnswer> findByQuestion(CourseQuestion question);
}
