package com.skillhub.repository;

import com.skillhub.model.ExamAttempt;
import com.skillhub.model.User;
import com.skillhub.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    List<ExamAttempt> findByUserAndCourse(User user, Course course);

    Optional<ExamAttempt> findTopByUserAndCourseOrderByScoreDesc(User user, Course course);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM ExamAttempt a WHERE a.course.instructor.email = :email")
    List<ExamAttempt> findByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);
}
