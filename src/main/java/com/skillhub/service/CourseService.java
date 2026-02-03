package com.skillhub.service;

import com.skillhub.model.Course;
import com.skillhub.repository.CourseRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    public Optional<Course> getCourseById(Long id) {
        return courseRepository.findById(id);
    }

    @PostConstruct
    public void init() {
        if (courseRepository.count() == 0) {
            courseRepository.save(new Course("Complete Java Masterclass", "Learn Java from scratch to advanced.",
                    "Development", "John Doe", 19.99,
                    "https://images.unsplash.com/photo-1581447109200-bf2769116351?auto=format&fit=crop&w=400&q=80"));
            courseRepository.save(new Course("Python for Data Science", "Master Python for data analysis.",
                    "Data Science", "Jane Smith", 24.99,
                    "https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=400&q=80"));
            courseRepository.save(new Course("Web Design Bootcamp", "HTML, CSS, JS and more.", "Design", "Mike Johnson",
                    14.99,
                    "https://images.unsplash.com/photo-1581291518633-83b4ebd1d83e?auto=format&fit=crop&w=400&q=80"));
            courseRepository.save(new Course("Digital Marketing 101", "SEO, SEM and Social Media.", "Marketing",
                    "Sarah Connor", 12.99,
                    "https://images.unsplash.com/photo-1533750516457-a7f992034fec?auto=format&fit=crop&w=400&q=80"));
            courseRepository.save(new Course("React - The Complete Guide", "Dive into React.js ecosystem.",
                    "Development", "Maximilian", 29.99,
                    "https://images.unsplash.com/photo-1633356122544-f134324a6cee?auto=format&fit=crop&w=400&q=80"));
        }
    }
}
