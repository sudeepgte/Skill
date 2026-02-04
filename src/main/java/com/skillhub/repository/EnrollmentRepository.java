package com.skillhub.repository;

import com.skillhub.model.Enrollment;
import com.skillhub.model.User;
import com.skillhub.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudent(User student);

    Optional<Enrollment> findByStudentAndCourse(User student, Course course);
}
