package com.skillhub.repository;

import com.skillhub.model.LectureProgress;
import com.skillhub.model.User;
import com.skillhub.model.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {

        Optional<LectureProgress> findByUserAndLecture(User user, Lecture lecture);

        List<LectureProgress> findByUser(User user);

        /**
         * Count completed lectures for a specific course by user (from LectureProgress
         * records).
         */
        @Query("SELECT COUNT(lp) FROM LectureProgress lp " +
                        "WHERE lp.user.id = :userId " +
                        "AND lp.lecture.section.course.id = :courseId " +
                        "AND lp.isCompleted = true")
        long countCompletedLecturesByCourse(@Param("userId") Long userId,
                        @Param("courseId") Long courseId);

        /** Count TOTAL lectures in a course directly via JPQL — avoids lazy-loading. */
        @Query("SELECT COUNT(l) FROM Lecture l WHERE l.section.course.id = :courseId")
        long countTotalLecturesByCourse(@Param("courseId") Long courseId);

        @Query("SELECT COUNT(lp) FROM LectureProgress lp " +
                        "WHERE lp.user.id = :userId " +
                        "AND lp.lecture.section.course.category.id = :categoryId " +
                        "AND lp.isCompleted = true")
        long countCompletedLecturesByCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

        @Query("SELECT DATE(lp.completedAt) as date, COUNT(lp) as count FROM LectureProgress lp " +
                        "WHERE lp.user.id = :userId AND lp.completedAt IS NOT NULL " +
                        "GROUP BY DATE(lp.completedAt) ORDER BY DATE(lp.completedAt) DESC")
        List<Object[]> findActivityData(@Param("userId") Long userId);
}
