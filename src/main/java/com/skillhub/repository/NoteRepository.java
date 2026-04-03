package com.skillhub.repository;

import com.skillhub.model.Note;
import com.skillhub.model.User;
import com.skillhub.model.Lecture;
import com.skillhub.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    Optional<Note> findByUserAndLecture(User user, Lecture lecture);

    Optional<Note> findByUserAndCourseAndLectureIsNull(User user, Course course);
    
    java.util.List<Note> findByUserOrderByUpdatedAtDesc(User user);
}
