package com.skillhub.service;

import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.LectureProgressRepository;
import com.skillhub.repository.LectureRepository;
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

        if (course.getStatus() != CourseStatus.PUBLISHED || !course.isPublished()) {
            throw new BusinessException("Cannot enroll in an unpublished course");
        }

        if (isEnrolled(user, course)) {
            return enrollmentRepository.findByStudentAndCourse(user, course).get();
        }

        Enrollment enrollment = new Enrollment(user, course);
        try {
            return enrollmentRepository.saveAndFlush(enrollment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return enrollmentRepository.findByStudentAndCourse(user, course).orElseThrow(() -> e);
        }
    }

    @Transactional
    public void markLectureAsCompleted(User user, Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new RuntimeException("Lecture not found"));

        // Find existing progress or create new
        LectureProgress progress = lectureProgressRepository.findByUserAndLecture(user, lecture)
                .orElse(null);

        if (progress == null) {
            progress = new LectureProgress(user, lecture);
            progress.setCompleted(true);
            progress.setCompletedAt(java.time.LocalDateTime.now());
            lectureProgressRepository.saveAndFlush(progress);
        } else if (!progress.isCompleted()) {
            progress.setCompleted(true);
            progress.setCompletedAt(java.time.LocalDateTime.now());
            lectureProgressRepository.saveAndFlush(progress);
        }

        // Always update last played and recompute course progress
        updateLastPlayed(user, lecture.getSection().getCourse().getId(), lectureId);
        updateCourseProgress(user, lecture.getSection().getCourse());
    }

    @Transactional
    public void updateLastPlayed(User user, Long courseId, Long lectureId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("Not enrolled"));

        enrollment.setLastPlayedLectureId(lectureId);
        enrollmentRepository.save(enrollment);
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

        long totalLectures = lectureProgressRepository.countTotalLecturesByCourse(course.getId());

        if (totalLectures == 0)
            return;

        long completedLectures = lectureProgressRepository
                .countCompletedLecturesByCourse(user.getId(), course.getId());

        // Standardized clean division for discrete completion
        double percentage = (double) completedLectures / totalLectures * 100.0;
        
        // If all lectures are done, ensure it's exactly 100
        if (completedLectures >= totalLectures) {
            percentage = 100.0;
            // Bonus: Unlock certificate automatically at 100%
            if (!enrollment.isExamPassed()) {
                enrollment.setExamPassed(true);
                enrollment.setCertificateDate(java.time.LocalDateTime.now());
            }
        }

        enrollment.setProgressPercentage(percentage);
        enrollmentRepository.save(enrollment);
    }

    /** Updates enrollment progress percentage directly (for partial video watch tracking). */
    @Transactional
    public void updateVideoProgress(User user, Long courseId, double lecturePct) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        Enrollment enrollment = enrollmentRepository.findByStudentAndCourse(user, course)
                .orElseThrow(() -> new RuntimeException("Not enrolled"));
        
        long totalLectures = lectureProgressRepository.countTotalLecturesByCourse(courseId);
        if (totalLectures <= 0) return;

        long completedCount = lectureProgressRepository.countCompletedLecturesByCourse(user.getId(), courseId);
        
        // Properly weighted calculation: 
        // (Full Completed * 100 + Current Lecture Partial %) / Total Lectures
        // We cap current lecture partial at 99% to distinguish from fully completed discrete state
        double safeLecturePct = Math.min(99.0, lecturePct);
        double calculatedTotalPct = ((completedCount * 100.0) + safeLecturePct) / totalLectures;
        
        // Only update if the new calculated total is higher (or if it's very close to next whole number)
        if (calculatedTotalPct > enrollment.getProgressPercentage()) {
            enrollment.setProgressPercentage(Math.min(99.9, calculatedTotalPct)); // Total cap at 99.9 for granular
            enrollmentRepository.save(enrollment);
        }
    }
}
