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

    long countByCourse(com.skillhub.model.Course course);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.instructor.email = :email")
    long countByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.instructor.email = :email AND e.isExamPassed = true")
    long countExamsPassedByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);

    long countByEnrolledAtAfter(java.time.LocalDateTime date);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM Enrollment e WHERE e.course.instructor.email = :email ORDER BY e.enrolledAt DESC")
    List<Enrollment> findByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT SUM(c.price) FROM enrollments e JOIN courses c ON e.course_id = c.id", nativeQuery = true)
    Double getTotalRevenue();

    @org.springframework.data.jpa.repository.Query(value = "SELECT MONTH(e.enrolled_at) as month, SUM(c.price) as revenue FROM enrollments e JOIN courses c ON e.course_id = c.id WHERE YEAR(e.enrolled_at) = :year GROUP BY month", nativeQuery = true)
    List<Object[]> getMonthlyRevenue(@org.springframework.data.repository.query.Param("year") int year);

    @org.springframework.data.jpa.repository.Query(value = "SELECT YEAR(e.enrolled_at) as year, SUM(c.price) as revenue FROM enrollments e JOIN courses c ON e.course_id = c.id GROUP BY year", nativeQuery = true)
    List<Object[]> getYearlyRevenue();
}
