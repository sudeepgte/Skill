package com.skillhub.repository;

import com.skillhub.model.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureRepository extends JpaRepository<Lecture, Long> {

    @Query("SELECT COUNT(l) FROM Lecture l WHERE l.section.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT SUM(l.durationSeconds) FROM Lecture l WHERE l.section.course.id = :courseId")
    Long sumDurationByCourseId(@Param("courseId") Long courseId);
}
