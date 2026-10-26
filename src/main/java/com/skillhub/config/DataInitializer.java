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
            PasswordEncoder passwordEncoder,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return args -> {

            // Remove mock vendors/instructors and their references from database completely
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
            
            String[] emails = {"vyshu@gmail.com", "nchakore467@gmail.com", "ikram@gmail.com"};
            for (String email : emails) {
                jdbcTemplate.update("DELETE FROM chat_messages WHERE sender_id IN (SELECT id FROM users WHERE email = ?) OR recipient_id IN (SELECT id FROM users WHERE email = ?)", email, email);
                jdbcTemplate.update("DELETE FROM enrollments WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM reviews WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM course_answers WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR question_id IN (SELECT id FROM course_questions WHERE course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?)))", email, email);
                jdbcTemplate.update("DELETE FROM course_questions WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM exam_attempts WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM exam_questions WHERE course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email);
                jdbcTemplate.update("DELETE FROM lecture_progress WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR lecture_id IN (SELECT l.id FROM lectures l JOIN course_sections s ON l.section_id = s.id JOIN courses c ON s.course_id = c.id WHERE c.instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM lectures WHERE section_id IN (SELECT s.id FROM course_sections s JOIN courses c ON s.course_id = c.id WHERE c.instructor_id IN (SELECT id FROM users WHERE email = ?))", email);
                jdbcTemplate.update("DELETE FROM course_sections WHERE course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email);
                jdbcTemplate.update("DELETE FROM lesson_notes WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
                jdbcTemplate.update("DELETE FROM password_reset_token WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
                jdbcTemplate.update("DELETE FROM payments WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM reports WHERE reporter_id IN (SELECT id FROM users WHERE email = ?)", email);
                jdbcTemplate.update("DELETE FROM user_resumes WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
                jdbcTemplate.update("DELETE FROM wishlist WHERE user_id IN (SELECT id FROM users WHERE email = ?) OR course_id IN (SELECT id FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?))", email, email);
                jdbcTemplate.update("DELETE FROM creations WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
                jdbcTemplate.update("DELETE FROM courses WHERE instructor_id IN (SELECT id FROM users WHERE email = ?)", email);
                jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
            }
            
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");
            try {
                jdbcTemplate.execute("ALTER TABLE professional_exam_attempts DROP INDEX UKo976hbu37ifoet5pt7w8fbrcb;");
            } catch (Exception ignored) {}

            // Admin
            User admin = userRepository.findByEmail("admin@skillhub.com");
            if (admin == null) {
                admin = newUser("Admin User", "admin@skillhub.com", "admin123", "ADMIN", passwordEncoder);
                userRepository.save(admin);
            }

            // Verify and reset yogu@123 password
            User yogu = userRepository.findByEmail("yogu@123");
            if (yogu != null) {
                boolean match = passwordEncoder.matches("yogu@123", yogu.getPassword());
                System.out.println("DEBUG: yogu@123 password match = " + match);
                if (!match) {
                    System.out.println("DEBUG: Resetting yogu@123 password to yogu@123");
                    yogu.setPassword(passwordEncoder.encode("yogu@123"));
                    userRepository.save(yogu);
                }
                yogu.setApproved(true);
                yogu.setBlocked(false);
                userRepository.save(yogu);
            } else {
                System.out.println("DEBUG: yogu@123 not found in DB! Seeding...");
                yogu = newUser("Yogita", "yogu@123", "yogu@123", "INSTRUCTOR", passwordEncoder);
                yogu.setApproved(true);
                userRepository.save(yogu);
            }

            // Verify and reset neha@123 password
            User neha = userRepository.findByEmail("neha@123");
            if (neha != null) {
                boolean match = passwordEncoder.matches("neha@123", neha.getPassword());
                System.out.println("DEBUG: neha@123 password match = " + match);
                if (!match) {
                    System.out.println("DEBUG: Resetting neha@123 password to neha@123");
                    neha.setPassword(passwordEncoder.encode("neha@123"));
                    userRepository.save(neha);
                }
                neha.setApproved(true);
                neha.setBlocked(false);
                userRepository.save(neha);
            } else {
                System.out.println("DEBUG: neha@123 not found in DB! Seeding...");
                neha = newUser("Neha", "neha@123", "neha@123", "STUDENT", passwordEncoder);
                neha.setApproved(true);
                userRepository.save(neha);
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

            // 4. Polish Course ID 2 (Digital Marketing Mastery) Content
            courseRepository.findById(2L).ifPresent(course -> {
                course.setLearningObjectives(
                    "Build a comprehensive digital marketing strategy from scratch for any business.\n" +
                    "Master Search Engine Optimization (SEO) to naturally rank #1 on Google and Bing.\n" +
                    "Launch high-conversion ad campaigns across Facebook, Instagram, and Google Ads.\n" +
                    "Growth-hack your social media presence to build a massive, engaged following.\n" +
                    "Leverage Google Analytics to make data-driven decisions and scale your results.\n" +
                    "Gain hands-on mastery over pro tools like SEMrush, Mailchimp, and Canva."
                );
                course.setRequirements(
                    "Absolutely no prior marketing knowledge required – we start from zero! " +
                    "A computer or smartphone with an internet connection. " +
                    "A passion for growing businesses and learning the latest digital tools. " +
                    "Perfect for entrepreneurs, freelancers, or career-switchers."
                );
                courseRepository.save(course);
            });
            // BUG-020: Fix any corrupted course levels in database
            try {
                jdbcTemplate.update("UPDATE courses SET level = 'Intermediate' WHERE level = 'DM Sansmediate'");
            } catch (Exception e) {
                // Ignore if table or column doesn't exist yet
            }

            // Clean up hardcoded seeded professional exam so only admin-added exams exist
            try {
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
                String seededCode = "SH-DEV-CERT-01";
                jdbcTemplate.update("DELETE FROM professional_exam_attempt_questions WHERE attempt_id IN (SELECT id FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_answers WHERE attempt_id IN (SELECT id FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_coding_submissions WHERE attempt_id IN (SELECT id FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_security_events WHERE attempt_id IN (SELECT id FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_skill_results WHERE result_id IN (SELECT id FROM professional_exam_results WHERE attempt_id IN (SELECT id FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?))))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_results WHERE attempt_id IN (SELECT id FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_certificates WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_attempts WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_applications WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_questions WHERE section_id IN (SELECT id FROM professional_exam_sections WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_sections WHERE exam_version_id IN (SELECT id FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?))", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_versions WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exam_blueprints WHERE exam_id IN (SELECT id FROM professional_exams WHERE exam_code = ?)", seededCode);
                jdbcTemplate.update("DELETE FROM professional_exams WHERE exam_code = ?", seededCode);
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");
            try {
                jdbcTemplate.execute("ALTER TABLE professional_exam_attempts DROP INDEX UKo976hbu37ifoet5pt7w8fbrcb;");
            } catch (Exception ignored) {}
            } catch (Exception e) {
                // Ignore if tables do not exist yet
            }
        };
    }

    @Bean
    public CommandLineRunner seedProfessionalExamAttempt() {
        return args -> {
            // Hardcoded seeded exams have been removed so only admin-added exams exist for students.
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
