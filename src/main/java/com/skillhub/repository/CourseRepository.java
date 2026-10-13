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

    List<Course> findByIsPublishedTrue();

    List<Course> findByStatus(CourseStatus status);

    long countByStatus(CourseStatus status);

    List<Course> findByCategoryName(String name);

    @Query("SELECT c FROM Course c WHERE (c.status = :status OR (c.status IS NULL AND c.isPublished = true)) AND LOWER(c.category.name) = LOWER(:categoryName)")
    List<Course> findPublishedByCategoryName(@Param("status") CourseStatus status, @Param("categoryName") String categoryName);

    @Query("SELECT c FROM Course c WHERE (c.status = :status OR (c.status IS NULL AND c.isPublished = true))")
    List<Course> findPublishedCourses(@Param("status") CourseStatus status);
}
