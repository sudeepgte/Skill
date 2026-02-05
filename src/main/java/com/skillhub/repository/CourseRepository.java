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

    List<Course> findByIsPublishedTrue();
}
