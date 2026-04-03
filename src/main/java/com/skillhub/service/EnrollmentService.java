package com.skillhub.service;

import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.LectureProgressRepository;
import com.skillhub.repository.LectureRepository; // Assuming this exists or will be created/used
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
            lectureProgressRepository.saveAndFlush(progress);

            // Update Course Progress
            updateCourseProgress(user, lecture.getSection().getCourse());
        }
    }

    @Transactional
    public void markLectureAsIncomplete(User user, Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new RuntimeException("Lecture not found"));

        LectureProgress progress = lectureProgressRepository.findByUserAndLecture(user, lecture)
                .orElse(null);

        if (progress != null && progress.isCompleted()) {
            progress.setCompleted(false);
            progress.setCompletedAt(null);
            lectureProgressRepository.save(progress);

            updateCourseProgress(user, lecture.getSection().getCourse());
        }
    }

    /** Marks ALL lectures in a course as complete for the user (DB sync). */
    @Transactional
    public void markAllLecturesComplete(User user, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("Not enrolled"));

        List<Lecture> lectures = lectureRepository.findByCourseId(courseId);
        for (Lecture lecture : lectures) {
            LectureProgress progress = lectureProgressRepository
                    .findByUserAndLecture(user, lecture)
                    .orElse(new LectureProgress(user, lecture));
            if (!progress.isCompleted()) {
                progress.setCompleted(true);
                progress.setCompletedAt(java.time.LocalDateTime.now());
                lectureProgressRepository.save(progress);
            }
        }
        
        lectureProgressRepository.flush();

        // Recompute percentage
        long total = lectures.size();
        if (total > 0) {
            long completed = lectureProgressRepository
                    .countCompletedLecturesByCourse(user.getId(), courseId);
            enrollment.setProgressPercentage(Math.min(100.0, (double) completed / total * 100.0));
            enrollmentRepository.save(enrollment);
        }
    }

    private void updateCourseProgress(User user, Course course) {
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("User not enrolled in this course"));

        // Use JPQL to count total lectures — avoids Hibernate lazy-loading returning
        // wrong count
        long totalLectures = lectureProgressRepository.countTotalLecturesByCourse(course.getId());

        if (totalLectures == 0)
            return;

        long completedLectures = lectureProgressRepository
                .countCompletedLecturesByCourse(user.getId(), course.getId());

        double percentage = Math.min(100.0, (double) completedLectures / totalLectures * 100.0);
        enrollment.setProgressPercentage(percentage);
        enrollmentRepository.save(enrollment);
    }

    /** Updates enrollment progress percentage directly (for partial video watch tracking). */
    @Transactional
    public void updateVideoProgress(User user, Long courseId, double percentage) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("Not enrolled"));
        // Only update if the new percentage is higher than stored
        if (percentage > enrollment.getProgressPercentage()) {
            enrollment.setProgressPercentage(Math.min(100.0, percentage));
            enrollmentRepository.save(enrollment);
        }
    }
}
