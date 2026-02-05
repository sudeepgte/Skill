package com.skillhub.service;

import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.LectureProgressRepository;
import com.skillhub.repository.LectureRepository; // Assuming this exists or will be created/used
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureProgressRepository lectureProgressRepository;

    @Autowired
    private LectureRepository lectureRepository;

    public boolean isEnrolled(User user, Course course) {
        return enrollmentRepository.findByStudentAndCourse(user, course).isPresent();
    }

    @Transactional
    public Enrollment enrollUser(User user, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (isEnrolled(user, course)) {
            return enrollmentRepository.findByStudentAndCourse(user, course).get();
        }

        Enrollment enrollment = new Enrollment(user, course);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void markLectureAsCompleted(User user, Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new RuntimeException("Lecture not found"));

        // Find existing progress or create new
        LectureProgress progress = lectureProgressRepository.findByUserAndLecture(user, lecture)
                .orElse(new LectureProgress(user, lecture));

        if (!progress.isCompleted()) {
            progress.setCompleted(true);
            progress.setCompletedAt(java.time.LocalDateTime.now());
            lectureProgressRepository.save(progress);

            // Update Course Progress
            updateCourseProgress(user, lecture.getSection().getCourse());
        }
    }

    private void updateCourseProgress(User user, Course course) {
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("User not enrolled in this course"));

        // Calculate total lectures
        long totalLectures = course.getSections().stream()
                .mapToLong(section -> section.getLectures().size())
                .sum();

        if (totalLectures == 0)
            return;

        // Calculate completed lectures
        long completedLectures = lectureProgressRepository
                .countByUserAndLecture_Section_Course_IdAndIsCompletedTrue(user, course.getId());

        double percentage = (double) completedLectures / totalLectures * 100.0;
        enrollment.setProgressPercentage(percentage);
        enrollmentRepository.save(enrollment);
    }
}
