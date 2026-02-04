package com.skillhub.repository;

import com.skillhub.model.LectureProgress;
import com.skillhub.model.User;
import com.skillhub.model.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {
    Optional<LectureProgress> findByUserAndLecture(User user, Lecture lecture);

    List<LectureProgress> findByUser(User user);

    long countByUserAndLecture_Section_Course_IdAndIsCompletedTrue(User user, Long courseId);
}
