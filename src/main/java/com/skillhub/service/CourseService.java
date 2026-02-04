package com.skillhub.service;

import com.skillhub.model.Course;
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

    public long countCourses() {
        return courseRepository.count();
    }

    public List<Course> getCoursesByInstructor(String email) {
        List<Course> courses = courseRepository.findByInstructorEmail(email);
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

    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
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
}
