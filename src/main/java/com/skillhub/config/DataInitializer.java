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
        };
    }

    @Bean
    public CommandLineRunner seedProfessionalExamAttempt(
            UserRepository userRepository,
            com.skillhub.repository.ProfessionalExamRepository examRepository,
            com.skillhub.repository.ProfessionalExamVersionRepository versionRepository,
            com.skillhub.repository.ProfessionalExamSectionRepository sectionRepository,
            com.skillhub.repository.ProfessionalExamQuestionRepository questionRepository,
            com.skillhub.repository.ProfessionalExamApplicationRepository applicationRepository,
            com.skillhub.repository.ProfessionalExamAttemptRepository attemptRepository,
            com.skillhub.repository.ProfessionalExamAttemptQuestionRepository attemptQuestionRepository) {
        return args -> {
            try {
                if (attemptRepository.count() > 0) {
                    return;
                }

                User neha = userRepository.findByEmail("neha@123");
                if (neha == null) return;

                com.skillhub.model.ProfessionalExam exam = examRepository.findByExamCode("SH-DEV-CERT-01").orElse(null);
                if (exam == null) {
                    exam = new com.skillhub.model.ProfessionalExam();
                    exam.setExamCode("SH-DEV-CERT-01");
                    exam.setTitle("Full Stack Software Engineering Certification");
                    exam.setDescription("Comprehensive examination testing core algorithms, database optimization, coding proficiency, and system design.");
                    exam.setTrack(com.skillhub.model.enums.ExamTrack.DEVELOPMENT);
                    exam.setLevel(com.skillhub.model.enums.ExamDifficulty.INTERMEDIATE);
                    exam.setDurationMinutes(60);
                    exam.setTotalMarks(30);
                    exam.setPassingMarks(15);
                    exam.setNegativeMarking(true);
                    exam.setNegativeMarkValue(new java.math.BigDecimal("0.5"));
                    exam.setMaximumAttempts(3);
                    exam.setApplicationRequired(false);
                    exam.setActive(true);
                    exam = examRepository.save(exam);
                }

                com.skillhub.model.ProfessionalExamVersion version = versionRepository.findByProfessionalExamId(exam.getId()).stream().findFirst().orElse(null);
                if (version == null) {
                    version = new com.skillhub.model.ProfessionalExamVersion();
                    version.setProfessionalExam(exam);
                    version.setVersionNumber("1.0");
                    version.setVersionName("Standard 2026");
                    version.setTotalMarks(30);
                    version.setPassingMarks(15);
                    version.setDurationMinutes(60);
                    version.setActive(true);
                    version.setPublished(true);
                    version = versionRepository.save(version);
                }

                com.skillhub.model.ProfessionalExamSection section = sectionRepository.findByExamVersionId(version.getId()).stream().findFirst().orElse(null);
                if (section == null) {
                    section = new com.skillhub.model.ProfessionalExamSection();
                    section.setExamVersion(version);
                    section.setName("Core Engineering & Problem Solving");
                    section.setDisplayOrder(1);
                    section.setActive(true);
                    section = sectionRepository.save(section);
                }

                if (questionRepository.findByExamSectionExamVersionId(version.getId()).isEmpty()) {
                    com.skillhub.model.ProfessionalExamQuestion q1 = new com.skillhub.model.ProfessionalExamQuestion();
                    q1.setExamSection(section);
                    q1.setQuestionText("Which SOLID principle states that high-level modules should not depend on low-level modules, but both should depend on abstractions?");
                    q1.setQuestionType(com.skillhub.model.enums.QuestionType.SINGLE_CHOICE);
                    q1.setDifficulty(com.skillhub.model.enums.ExamDifficulty.INTERMEDIATE);
                    q1.setMarks(5);
                    q1.setNegativeMarks(new java.math.BigDecimal("0.5"));
                    q1.setOptions(java.util.List.of("Single Responsibility Principle", "Open/Closed Principle", "Interface Segregation Principle", "Dependency Inversion Principle"));
                    q1.setCorrectAnswer("Dependency Inversion Principle");
                    q1.setActive(true);
                    questionRepository.save(q1);

                    com.skillhub.model.ProfessionalExamQuestion q2 = new com.skillhub.model.ProfessionalExamQuestion();
                    q2.setExamSection(section);
                    q2.setQuestionText("Write a method in Java that takes a string and returns true if it is a valid palindrome, ignoring casing and non-alphanumeric characters.");
                    q2.setQuestionType(com.skillhub.model.enums.QuestionType.CODING);
                    q2.setDifficulty(com.skillhub.model.enums.ExamDifficulty.INTERMEDIATE);
                    q2.setMarks(10);
                    q2.setNegativeMarks(java.math.BigDecimal.ZERO);
                    q2.setCodeLanguage("java");
                    q2.setCodeStarterTemplate("public class Solution {\n    public static boolean isPalindrome(String s) {\n        // Write your solution here\n        return true;\n    }\n}");
                    q2.setActive(true);
                    questionRepository.save(q2);

                    com.skillhub.model.ProfessionalExamQuestion q3 = new com.skillhub.model.ProfessionalExamQuestion();
                    q3.setExamSection(section);
                    q3.setQuestionText("In relational database management systems, which isolation level provides complete protection against dirty reads, non-repeatable reads, and phantom reads?");
                    q3.setQuestionType(com.skillhub.model.enums.QuestionType.SINGLE_CHOICE);
                    q3.setDifficulty(com.skillhub.model.enums.ExamDifficulty.ADVANCED);
                    q3.setMarks(5);
                    q3.setNegativeMarks(new java.math.BigDecimal("0.5"));
                    q3.setOptions(java.util.List.of("Read Uncommitted", "Read Committed", "Repeatable Read", "Serializable"));
                    q3.setCorrectAnswer("Serializable");
                    q3.setActive(true);
                    questionRepository.save(q3);

                    com.skillhub.model.ProfessionalExamQuestion q4 = new com.skillhub.model.ProfessionalExamQuestion();
                    q4.setExamSection(section);
                    q4.setQuestionText("Explain how you would design an idempotent payment processing API to prevent duplicate charges when network timeouts occur between the client and payment gateway.");
                    q4.setQuestionType(com.skillhub.model.enums.QuestionType.SCENARIO);
                    q4.setDifficulty(com.skillhub.model.enums.ExamDifficulty.ADVANCED);
                    q4.setMarks(10);
                    q4.setNegativeMarks(java.math.BigDecimal.ZERO);
                    q4.setActive(true);
                    questionRepository.save(q4);
                }

                com.skillhub.model.ProfessionalExamApplication app = applicationRepository.findByUserId(neha.getId()).stream().findFirst().orElse(null);
                if (app == null) {
                    app = new com.skillhub.model.ProfessionalExamApplication();
                    app.setApplicationNumber("APP-CERT-001");
                    app.setUser(neha);
                    app.setEmail(neha.getEmail());
                    app.setProfessionalExam(exam);
                    app.setExamVersion(version);
                    app.setStatus(com.skillhub.model.enums.ApplicationStatus.APPROVED);
                    app.setPaymentStatus(com.skillhub.model.PaymentStatus.SUCCESS);
                    app.setApplicationFee(java.math.BigDecimal.ZERO);
                    app = applicationRepository.save(app);
                }

                com.skillhub.model.ProfessionalExamAttempt attempt = new com.skillhub.model.ProfessionalExamAttempt();
                attempt.setApplication(app);
                attempt.setStudent(neha);
                attempt.setExamVersion(version);
                attempt.setStartedAt(java.time.LocalDateTime.now());
                attempt.setStatus(com.skillhub.model.enums.AttemptStatus.IN_PROGRESS);
                attempt.setCurrentQuestion(1L);
                attempt = attemptRepository.save(attempt);

                java.util.List<com.skillhub.model.ProfessionalExamQuestion> qs = questionRepository.findByExamSectionExamVersionId(version.getId());
                int num = 1;
                for (com.skillhub.model.ProfessionalExamQuestion q : qs) {
                    com.skillhub.model.ProfessionalExamAttemptQuestion aq = new com.skillhub.model.ProfessionalExamAttemptQuestion(attempt, q, num, num);
                    attemptQuestionRepository.save(aq);
                    num++;
                }
            } catch (Exception ex) {
                // Ignore seed errors on dynamic environments
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
