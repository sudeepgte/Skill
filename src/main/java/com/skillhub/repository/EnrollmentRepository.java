package com.skillhub.repository;

import com.skillhub.model.Enrollment;
import com.skillhub.model.User;
import com.skillhub.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudent(User student);

    Optional<Enrollment> findByStudentAndCourse(User student, Course course);

    long countByEnrolledAtAfter(java.time.LocalDateTime date);
    
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Enrollment e WHERE e.course.instructor.email = :email ORDER BY e.enrolledAt DESC")
    List<Enrollment> findByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);
}
