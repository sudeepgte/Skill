package com.skillhub.repository;

import com.skillhub.model.Course;
import com.skillhub.model.CourseExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseExamRepository extends JpaRepository<CourseExam, Long> {
    List<CourseExam> findByCourse(Course course);

    List<CourseExam> findByCourseOrderByUpdatedAtDesc(Course course);

    @Query("SELECT e FROM CourseExam e WHERE e.course.instructor.email = :email ORDER BY e.updatedAt DESC")
    List<CourseExam> findByInstructorEmail(@Param("email") String email);

    @Query("SELECT e FROM CourseExam e WHERE e.course.id = :courseId ORDER BY e.updatedAt DESC")
    List<CourseExam> findByCourseId(@Param("courseId") Long courseId);
}
