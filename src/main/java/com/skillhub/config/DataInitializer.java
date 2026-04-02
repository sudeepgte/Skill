package com.skillhub.config;

import com.skillhub.model.Category;

import com.skillhub.model.User;
import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.ReviewRepository;
import com.skillhub.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(UserRepository userRepository,
            CategoryRepository categoryRepository,
            CourseRepository courseRepository,
            ReviewRepository reviewRepository,
            com.skillhub.repository.EnrollmentRepository enrollmentRepository,
            com.skillhub.repository.NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {

            // 1. Create or Update Users
            // Force vyshu@gmail.com to be an INSTRUCTOR for testing
            User vendor = userRepository.findByEmail("vyshu@gmail.com");
            if (vendor == null) {
                vendor = newUser("Vyshu Vendor", "vyshu@gmail.com", "vyshu123", "INSTRUCTOR", passwordEncoder);
                userRepository.save(vendor);
            } else {
                vendor.setRole("INSTRUCTOR"); // Force update for existing user
                userRepository.save(vendor);
            }

            if (userRepository.count() == 1) { // If only vendor was found/added
                // Admin
                User admin = newUser("Admin User", "admin@skillhub.com", "admin123", "ADMIN", passwordEncoder);
                userRepository.save(java.util.Objects.requireNonNull(admin));
            }

            // 2. Create Categories
            if (categoryRepository.count() == 0) {
                List<Category> categories = Arrays.asList(
                        new Category("Development and IT", "fas fa-code"),
                        new Category("Business Marketing", "fas fa-chart-line"),
                        new Category("Office Productivity and Design", "fas fa-file-excel"),
                        new Category("Music", "fas fa-music"),
                        new Category("Health Fitness and Yoga", "fas fa-heartbeat"),
                        new Category("Art, Craft and Painting", "fas fa-paint-brush"),
                        new Category("Kids Learning and Speaking Course", "fas fa-child"),
                        new Category("Language", "fas fa-language"),
                        new Category("Photography", "fas fa-camera"),
                        new Category("Other", "fas fa-ellipsis-h"));
                categoryRepository.saveAll(java.util.Objects.requireNonNull(categories));
            }
            // 3. Mock Data for "Students" Tab (Enrollments)
            if (enrollmentRepository.count() == 0) {
                // Find vendor and a course
                User vendorInstructor = userRepository.findByEmail("vyshu@gmail.com");
                if (vendorInstructor != null) {
                    List<com.skillhub.model.Course> courses = courseRepository.findByInstructorEmail(vendorInstructor.getEmail());
                    if (courses != null && !courses.isEmpty()) {
                        com.skillhub.model.Course sampleCourse = courses.get(0);
                        
                        // Create a mock student
                        User mockStudent = userRepository.findByEmail("student@skillhub.com");
                        if (mockStudent == null) {
                            mockStudent = newUser("Mock Student", "student@skillhub.com", "student123", "STUDENT", passwordEncoder);
                            userRepository.save(mockStudent);
                        }
                        
                        // Create enrollment
                        com.skillhub.model.Enrollment enrollment = new com.skillhub.model.Enrollment(mockStudent, sampleCourse);
                        enrollment.setProgressPercentage(45.0); // Pre-fill some progress
                        
                        // Update course stat
                        sampleCourse.getEnrollments().add(enrollment);
                        enrollmentRepository.save(enrollment);
                    }
                }
            }
        };
    }

    private User newUser(String name, String email, String password, String role, PasswordEncoder encoder) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setRole(role);
        return user;
    }

    private void updateCategoryName(CategoryRepository repo, String oldName, String newName) {
        Category cat = repo.findByName(oldName);
        if (cat != null) {
            cat.setName(newName);
            repo.save(cat);
        }
    }

    private void deleteCategory(CategoryRepository repo, String name) {
        Category cat = repo.findByName(name);
        if (cat != null) {
            try {
                repo.delete(cat);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
