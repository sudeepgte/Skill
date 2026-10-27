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

    @Query("SELECT r.course.id, AVG(r.rating), COUNT(r) FROM Review r WHERE r.course.id IN :courseIds GROUP BY r.course.id")
    java.util.List<Object[]> getRatingStatsForCourseIds(@Param("courseIds") java.util.Collection<Long> courseIds);

    void deleteByCourse(Course course);

    @Query("SELECT r FROM Review r WHERE r.course.instructor.email = :email ORDER BY r.createdAt DESC")
    java.util.List<Review> findByInstructorEmail(@Param("email") String email);

    java.util.List<Review> findByCourseIdOrderByCreatedAtDesc(Long courseId);
}
