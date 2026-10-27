package com.skillhub.repository;

import com.skillhub.model.Course;
import com.skillhub.model.Category;
import com.skillhub.model.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByCategory(Category category);

    List<Course> findByInstructorEmail(String email);

    List<Course> findByInstructorEmailAndStatus(String email, CourseStatus status);

    List<Course> findByTitleContainingIgnoreCase(String title);

    Course findTopByOrderByViewCountDesc();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(c.viewCount) FROM Course c WHERE c.instructor.email = :email")
    Long sumViewsByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);

    List<Course> findByIsPublishedTrue();

    List<Course> findByStatus(CourseStatus status);

    long countByStatus(CourseStatus status);

    List<Course> findByCategoryName(String name);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.instructor WHERE c.status = :status AND c.isPublished = true AND LOWER(c.category.name) = LOWER(:categoryName) ORDER BY c.id DESC")
    List<Course> findPublishedByCategoryName(@Param("status") CourseStatus status, @Param("categoryName") String categoryName);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.instructor WHERE c.status = :status AND c.isPublished = true ORDER BY c.id DESC")
    List<Course> findPublishedCourses(@Param("status") CourseStatus status);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.instructor WHERE c.status = :status AND c.isPublished = true AND c.category = :category ORDER BY c.id DESC")
    List<Course> findPublishedByCategory(@Param("status") CourseStatus status, @Param("category") Category category);
    @Query("SELECT COUNT(c) FROM Course c WHERE c.instructor.id = :instructorId AND ((c.createdAt IS NOT NULL AND c.createdAt >= :startDate AND c.createdAt <= :endDate) OR (c.createdAt IS NULL AND c.submittedAt IS NOT NULL AND c.submittedAt >= :startDate AND c.submittedAt <= :endDate))")
    long countCoursesCreatedInPeriod(@Param("instructorId") Long instructorId, @Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT COUNT(c) FROM Course c WHERE c.instructor.id = :instructorId")
    long countAllCoursesByInstructorId(@Param("instructorId") Long instructorId);
}
