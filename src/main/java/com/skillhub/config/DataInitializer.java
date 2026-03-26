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

            // 1. Create Users
            if (userRepository.count() == 0) {
                // Admin
                User admin = newUser("Admin User", "admin@skillhub.com", "admin123", "ADMIN", passwordEncoder);
                userRepository.save(java.util.Objects.requireNonNull(admin));

                // Instructors (Keep users, but they won't have courses by default)
                User angela = newUser("Dr. Angela Yu", "angela@skillhub.com", "password", "INSTRUCTOR",
                        passwordEncoder);
                angela.setHeadline("Developer and Lead Instructor");
                userRepository.save(java.util.Objects.requireNonNull(angela));

                User jose = newUser("Jose Portilla", "jose@skillhub.com", "password", "INSTRUCTOR", passwordEncoder);
                jose.setHeadline("Head of Data Science, Pierian Data");
                userRepository.save(java.util.Objects.requireNonNull(jose));

                // Student
                User student = newUser("Jane Smith", "student@skillhub.com", "password", "STUDENT", passwordEncoder);
                userRepository.save(java.util.Objects.requireNonNull(student));
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
                        new Category("Photography", "fas fa-camera"));
                categoryRepository.saveAll(java.util.Objects.requireNonNull(categories));
            } else {
                // Rename legacy categories if they exist
                updateCategoryName(categoryRepository, "Development", "Development and IT");
                updateCategoryName(categoryRepository, "Business", "Business Marketing");
                updateCategoryName(categoryRepository, "Office Productivity", "Office Productivity and Design");
                updateCategoryName(categoryRepository, "Health & Fitness", "Health Fitness and Yoga");

                // Remove unwanted legacy categories
                deleteCategory(categoryRepository, "Finance & Accounting");
                deleteCategory(categoryRepository, "IT & Software");
                deleteCategory(categoryRepository, "Design");
                deleteCategory(categoryRepository, "Marketing");
                deleteCategory(categoryRepository, "Personal Development");
            }

            // 3. Create Sample Courses if none exist
            if (courseRepository.count() == 0) {
                User angela = userRepository.findByEmail("angela@skillhub.com");
                Category devCat = categoryRepository.findByName("Development and IT");

                com.skillhub.model.Course javaCourse = new com.skillhub.model.Course();
                javaCourse.setTitle("Complete Java Masterclass 2026");
                javaCourse.setSubtitle("Learn Java Programming with real projects");
                javaCourse.setInstructor(angela);
                javaCourse.setCategory(devCat);
                javaCourse.setPrice(49.99);
                javaCourse.setImageUrl("https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800");
                javaCourse.setPublished(true);
                courseRepository.save(javaCourse);

                com.skillhub.model.Course springCourse = new com.skillhub.model.Course();
                springCourse.setTitle("Spring Boot Microservices");
                springCourse.setSubtitle("Master the most popular Java framework");
                springCourse.setInstructor(angela);
                springCourse.setCategory(devCat);
                springCourse.setPrice(59.99);
                springCourse.setImageUrl("https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=800");
                springCourse.setPublished(true);
                courseRepository.save(springCourse);

                // 4. Enroll Student in one course
                User student = userRepository.findByEmail("student@skillhub.com");
                if (student != null) {
                    com.skillhub.model.Enrollment enrollment = new com.skillhub.model.Enrollment(student, javaCourse);
                    enrollment.setProgressPercentage(45.0); // Sample progress
                    enrollmentRepository.save(enrollment);

                    // 5. Add a notification
                    com.skillhub.model.Notification note = new com.skillhub.model.Notification();
                    note.setUser(student);
                    note.setTitle("Welcome");
                    note.setType("INFO");
                    note.setMessage("Welcome to SkillHub! You have been enrolled in the Java Masterclass.");
                    note.setCreatedAt(java.time.LocalDateTime.now());
                    notificationRepository.save(note);
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
