package com.skillhub.service;

import com.skillhub.model.Course;
import com.skillhub.model.CourseSection;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
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
            section.setPosition(course.getSections().size() + 1);
            courseSectionRepository.save(section);
        }
    }

    public void addLectureToSection(Long sectionId, com.skillhub.model.Lecture lecture) {
        CourseSection section = courseSectionRepository.findById(sectionId).orElse(null);
        if (section != null) {
            lecture.setSection(section);
            // Auto position
            lecture.setPosition(section.getLectures().size() + 1);
            lectureRepository.save(lecture);
        }
    }
}
