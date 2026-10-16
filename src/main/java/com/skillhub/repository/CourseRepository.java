package com.skillhub.repository;

import com.skillhub.model.Course;
import com.skillhub.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByCategory(Category category);

    List<Course> findByInstructorEmail(String email);

    List<Course> findByTitleContainingIgnoreCase(String title);

    Course findTopByOrderByViewCountDesc();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(c.viewCount) FROM Course c WHERE c.instructor.email = :email")
    Long sumViewsByInstructorEmail(@org.springframework.data.repository.query.Param("email") String email);

    List<Course> findByIsPublishedTrue();

    List<Course> findByCategoryName(String name);
}
