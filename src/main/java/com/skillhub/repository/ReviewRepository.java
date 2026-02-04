package com.skillhub.repository;

import com.skillhub.model.Review;
import com.skillhub.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByCourse(Course course, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.course.id = :courseId")
    Optional<Double> getAverageRatingByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.course.id = :courseId")
    Integer countByCourseId(@Param("courseId") Long courseId);
}
