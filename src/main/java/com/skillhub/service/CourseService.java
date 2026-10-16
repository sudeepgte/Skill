package com.skillhub.service;

import com.skillhub.model.Category;
import com.skillhub.model.Course;
import com.skillhub.model.CourseSection;
import com.skillhub.model.CourseStatus;
import com.skillhub.model.Lecture;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.CourseSectionRepository;
import com.skillhub.repository.LectureRepository;
import com.skillhub.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private LectureRepository lectureRepository;

    public List<Course> getAllCourses() {
        List<Course> courses = courseRepository.findAll();
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getPublishedCourses() {
        List<Course> courses = courseRepository.findPublishedCourses(CourseStatus.PUBLISHED);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getPendingReviewCourses() {
        List<Course> courses = courseRepository.findByStatus(CourseStatus.PENDING_REVIEW);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getCoursesByStatus(CourseStatus status) {
        List<Course> courses = courseRepository.findByStatus(status);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public long countPendingCourses() {
        return courseRepository.countByStatus(CourseStatus.PENDING_REVIEW);
    }

    public long countCourses() {
        return courseRepository.count();
    }

    public Long sumViewsByInstructorEmail(String email) { return courseRepository.sumViewsByInstructorEmail(email); }

    public List<Course> getCoursesByInstructor(String email) {
        List<Course> courses = courseRepository.findByInstructorEmail(email);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getCoursesByInstructorAndStatus(String email, CourseStatus status) {
        List<Course> courses = courseRepository.findByInstructorEmailAndStatus(email, status);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getCoursesByCategory(String categoryName) {
        List<Course> courses = courseRepository.findPublishedByCategoryName(CourseStatus.PUBLISHED, categoryName);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getCoursesByCategory(Category category) {
        if (category == null) return List.of();
        return getCoursesByCategory(category.getName());
    }

    public Course getCourseById(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            populateCourseRatings(course);
            // Initialize sections and lectures
            if (course.getSections() != null) {
                course.getSections().size();
                course.getSections().forEach(section -> {
                    if (section.getLectures() != null) {
                        section.getLectures().size();
                    }
                });
            }
            if (course.getResources() != null) {
                course.getResources().size();
            }
        }
        return course;
    }

    public void saveCourse(Course course) {
        courseRepository.save(course);
    }

    public void createCourse(Course course, String instructorEmail) {
        course.setStatus(CourseStatus.DRAFT);
        course.setPublished(false);
        course.setViewCount(0L);
        courseRepository.save(course);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            entityManager.createQuery("DELETE FROM Review e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM Enrollment e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM ExamAttempt e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM ExamQuestion e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM CourseQuestion e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM Note e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM Payment e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM Report e WHERE e.reportedCourse.id = :id").setParameter("id", id).executeUpdate();
            entityManager.createQuery("DELETE FROM Wishlist e WHERE e.course.id = :id").setParameter("id", id).executeUpdate();
            courseRepository.deleteById(id);
            return;
        }
        // Fallback for empty block
        courseRepository.deleteById(id);
    }

    public void publishCourse(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            course.setStatus(CourseStatus.PUBLISHED);
            course.setPublished(true);
            course.setReviewedAt(LocalDateTime.now());
            course.setRejectionReason(null);
            courseRepository.save(course);
        }
    }

    public void approveCourse(Long id) {
        publishCourse(id);
    }

    public void rejectCourse(Long id, String reason) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            course.setStatus(CourseStatus.REJECTED);
            course.setPublished(false);
            course.setReviewedAt(LocalDateTime.now());
            course.setRejectionReason(reason);
            courseRepository.save(course);
        }
    }

    public void submitCourseForReview(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            course.setStatus(CourseStatus.PENDING_REVIEW);
            course.setPublished(false);
            course.setSubmittedAt(LocalDateTime.now());
            course.setRejectionReason(null);
            courseRepository.save(course);
        }
    }

    public void unpublishCourse(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            course.setStatus(CourseStatus.ARCHIVED);
            course.setPublished(false);
            courseRepository.save(course);
        }
    }

    public List<Course> searchCourses(String query) {
        List<Course> courses = courseRepository.findByTitleContainingIgnoreCase(query);
        courses.removeIf(c -> c.getStatus() != CourseStatus.PUBLISHED);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    private void populateCourseRatings(Course course) {
        Double avgRating = reviewRepository.getAverageRatingByCourseId(course.getId()).orElse(0.0);
        Integer reviewCount = reviewRepository.countByCourseId(course.getId());

        // Round to 1 decimal place
        avgRating = Math.round(avgRating * 10.0) / 10.0;

        course.setAverageRating(avgRating);
        course.setReviewCount(reviewCount);
    }

    public void addSectionToCourse(Long courseId, String title) {
        Course course = getCourseById(courseId);
        if (course != null) {
            CourseSection section = new CourseSection();
            section.setTitle(title);
            section.setCourse(course);
            if (course.getSections() != null) {
                section.setPosition(course.getSections().size() + 1);
            } else {
                section.setPosition(1);
            }
            courseSectionRepository.save(section);
        }
    }

    public void addLectureToSection(Long sectionId, Lecture lecture) {
        CourseSection section = courseSectionRepository.findById(sectionId).orElse(null);
        if (section != null) {
            lecture.setSection(section);
            if (section.getLectures() != null) {
                lecture.setPosition(section.getLectures().size() + 1);
            } else {
                lecture.setPosition(1);
            }
            lectureRepository.save(lecture);
        }
    }

    public void updateCourseStats(Long courseId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course != null) {
            long totalLectures = lectureRepository.countByCourseId(courseId);
            Long totalSecondsObj = lectureRepository.sumDurationByCourseId(courseId);
            long totalSeconds = (totalSecondsObj != null) ? totalSecondsObj : 0;

            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            if (totalSeconds > 0 && totalSeconds < 60) {
                minutes = 1;
            }

            String timeString = "";
            if (hours > 0) {
                timeString += hours + "h ";
            }
            timeString += minutes + "m";

            // Format: "X lectures Ã¢â‚¬Â¢ Yh Zm"
            String finalContentLength = totalLectures + " lectures Ã¢â‚¬Â¢ " + timeString.trim();

            String finalContentLength1 = totalLectures + " lectures • " + timeString.trim();
            course.setContentLength(finalContentLength1);
            courseRepository.save(course);
        }
    }

    public void generateSampleCurriculum(Long courseId) {
        Course course = getCourseById(courseId);
        if (course == null)
            return;

        // Add Section 1
        CourseSection s1 = new CourseSection();
        s1.setTitle("Fundamentals of Skill Discovery");
        s1.setCourse(course);
        s1.setPosition(1);
        courseSectionRepository.save(s1);

        // Lesson 1: Video (YouTube)
        Lecture l1 = new Lecture();
        l1.setTitle("Core Concepts & Mindset");
        l1.setSection(s1);
        l1.setPosition(1);
        l1.setVideoUrl("https://www.youtube.com/watch?v=aqS_607XF_U");
        l1.setDurationSeconds(600);
        l1.setContent("Welcome to your first lesson! In this article, we dive deep into the mindset required for mastering new skills. Consistency is key.");
        lectureRepository.save(l1);

        // Lesson 2: Reading Material (Article Only)
        Lecture l2 = new Lecture();
        l2.setTitle("Essential Tools & Setup");
        l2.setSection(s1);
        l2.setPosition(2);
        l2.setVideoUrl(null);
        l2.setDurationSeconds(300);
        l2.setContent("<h2>The Checklist</h2><ul><li>Patience</li><li>Correct Environment</li><li>Quality Tools</li></ul><p>Use the Audio Mode to listen to this while you gather your tools!</p>");
        lectureRepository.save(l2);

        updateCourseStats(courseId);
    }
}
