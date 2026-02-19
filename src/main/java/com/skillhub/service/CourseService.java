package com.skillhub.service;

import com.skillhub.model.Course;
import com.skillhub.model.CourseSection;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@org.springframework.transaction.annotation.Transactional
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    public List<Course> getAllCourses() {
        List<Course> courses = courseRepository.findAll();
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getPublishedCourses() {
        List<Course> courses = courseRepository.findByIsPublishedTrue();
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public long countCourses() {
        return courseRepository.count();
    }

    public List<Course> getCoursesByInstructor(String email) {
        List<Course> courses = courseRepository.findByInstructorEmail(email);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getCoursesByCategory(String categoryName) {
        List<Course> courses = courseRepository.findByCategoryName(categoryName);
        courses.forEach(this::populateCourseRatings);
        return courses;
    }

    public List<Course> getCoursesByCategory(com.skillhub.model.Category category) {
        List<Course> courses = courseRepository.findByCategory(category);
        courses.forEach(this::populateCourseRatings);
        return courses;
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
        }
        return course;
    }

    public void saveCourse(Course course) {
        courseRepository.save(course);
    }

    public void createCourse(Course course, String instructorEmail) {
        // Find instructor
        // For now, we assume the logged-in admin is the instructor
        // In a real multi-instructor app, this would be looked up
        // Assuming the repository for user exists and we can find by email
        // Logic to set instructor handled in Controller or here if we inject
        // UserRepository
        course.setPublished(false); // Default to draft
        course.setViewCount(0L);
        courseRepository.save(course);
    }

    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
    }

    public void publishCourse(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            course.setPublished(true);
            courseRepository.save(course);
        }
    }

    public void unpublishCourse(Long id) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course != null) {
            course.setPublished(false);
            courseRepository.save(course);
        }
    }

    public List<Course> searchCourses(String query) {
        List<Course> courses = courseRepository.findByTitleContainingIgnoreCase(query);
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

    @Autowired
    private com.skillhub.repository.CourseSectionRepository courseSectionRepository;

    @Autowired
    private com.skillhub.repository.LectureRepository lectureRepository;

    public void addSectionToCourse(Long courseId, String title) {
        Course course = getCourseById(courseId);
        if (course != null) {
            CourseSection section = new CourseSection();
            section.setTitle(title);
            section.setCourse(course);
            // Auto position at the end
            if (course.getSections() != null) {
                section.setPosition(course.getSections().size() + 1);
            } else {
                section.setPosition(1);
            }
            courseSectionRepository.save(section);
        }
    }

    public void addLectureToSection(Long sectionId, com.skillhub.model.Lecture lecture) {
        CourseSection section = courseSectionRepository.findById(sectionId).orElse(null);
        if (section != null) {
            lecture.setSection(section);
            // Auto position
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
            // Use fresh repository stats
            long totalLectures = lectureRepository.countByCourseId(courseId);
            Long totalSecondsObj = lectureRepository.sumDurationByCourseId(courseId);
            long totalSeconds = (totalSecondsObj != null) ? totalSecondsObj : 0;

            // Format Duration
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            // Handle edge case where e.g. 59s -> 0m
            if (totalSeconds > 0 && totalSeconds < 60) {
                minutes = 1;
            }

            String timeString = "";
            if (hours > 0) {
                timeString += hours + "h ";
            }
            timeString += minutes + "m";

            // Format: "X lectures • Yh Zm"
            String finalContentLength = totalLectures + " lectures • " + timeString.trim();

            course.setContentLength(finalContentLength);

            courseRepository.save(course);
        }
    }
}
