package com.skillhub.config;

import com.skillhub.model.Category;
import com.skillhub.model.Course;
import com.skillhub.model.Review;
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
            PasswordEncoder passwordEncoder) {
        return args -> {

            // 1. Create Users
            if (userRepository.count() == 0) {
                // Admin
                User admin = newUser("Admin User", "admin@skillhub.com", "admin123", "ADMIN", passwordEncoder);
                userRepository.save(admin);

                // Instructors
                User angela = newUser("Dr. Angela Yu", "angela@skillhub.com", "password", "INSTRUCTOR",
                        passwordEncoder);
                angela.setHeadline("Developer and Lead Instructor");
                userRepository.save(angela);

                User jose = newUser("Jose Portilla", "jose@skillhub.com", "password", "INSTRUCTOR", passwordEncoder);
                jose.setHeadline("Head of Data Science, Pierian Data");
                userRepository.save(jose);

                // Student
                User student = newUser("Jane Smith", "student@skillhub.com", "password", "STUDENT", passwordEncoder);
                userRepository.save(student);
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
                categoryRepository.saveAll(categories);
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
                deleteCategory(categoryRepository, "Personal Development"); // Removing this as it was part of the old
                                                                            // set
            }

            // 3. Create Demo Courses
            if (courseRepository.count() == 0) {
                User angela = userRepository.findByEmail("angela@skillhub.com");
                User jose = userRepository.findByEmail("jose@skillhub.com");

                // Note: We renamed categories above, so look up by NEW names
                Category dev = categoryRepository.findByName("Development and IT");
                if (dev == null)
                    dev = categoryRepository.findByName("Development"); // Fallback

                Category business = categoryRepository.findByName("Business Marketing");
                if (business == null)
                    business = categoryRepository.findByName("Business"); // Fallback

                // Helper to safely get a category or default to first one if missing to avoid
                // internal errors during init
                // Ideally handle this better, but for demo data init:
                Category finance = categoryRepository.findByName("Finance & Accounting");
                // Note: Finance was deleted in previous block if logic ran, so this might be
                // null.
                // Let's use "Business Marketing" for Finance course or recreate check.
                // Actually, the user's logic DELETED "Finance & Accounting".
                // So we should put the finance course in Business Marketing or skip it.
                // Let's put it in Business Marketing.
                if (finance == null)
                    finance = business;

                if (dev != null && business != null) {
                    // Course 1: Web Dev
                    createCourse(courseRepository, angela, dev,
                            "The Complete 2024 Web Development Bootcamp",
                            "Become a Full-Stack Web Developer with just ONE course. HTML, CSS, Javascript, Node, React, MongoDB, Web3 and DApps",
                            499.0, "https://img-c.udemycdn.com/course/480x270/1565838_e54e_18.jpg",
                            true);

                    // Course 2: Python
                    createCourse(courseRepository, jose, dev,
                            "The Complete Python Bootcamp From Zero to Hero in Python",
                            "Learn Python like a Professional! Start from the basics and go all the way to creating your own applications and games",
                            599.0, "https://img-c.udemycdn.com/course/480x270/567828_67d0.jpg",
                            true);

                    // Course 3: Machine Learning
                    createCourse(courseRepository, jose, dev,
                            "Machine Learning A-Z™: AI, Python & R + ChatGPT Prize [2024]",
                            "Learn to create Machine Learning Algorithms in Python and R from two Data Science experts. Code templates included.",
                            699.0, "https://img-c.udemycdn.com/course/480x270/950390_270f_3.jpg",
                            true);

                    // Course 4: Excel
                    createCourse(courseRepository, angela, business,
                            "Microsoft Excel - Excel from Beginner to Advanced",
                            "Excel with this A-Z Microsoft Excel Course. Microsoft Excel 2010, 2013, 2016, Excel 2019 and Office 365",
                            399.0, "https://img-c.udemycdn.com/course/480x270/793796_0e89_2.jpg",
                            true);

                    // Course 5: Finance
                    createCourse(courseRepository, jose, finance,
                            "The Complete Financial Analyst Course 2024",
                            "Excel, Accounting, Financial Analysis, Financial Modeling, Valuation, Business Analysis, Python and more!",
                            449.0, "https://img-c.udemycdn.com/course/480x270/648826_f0e5_4.jpg",
                            true);
                }
            }

            // 4. Create Random Reviews
            if (reviewRepository.count() == 0) {
                List<Course> courses = courseRepository.findAll();
                User student = userRepository.findByEmail("student@skillhub.com");

                if (student != null) {
                    for (Course c : courses) {
                        createReview(reviewRepository, c, student, 5, "Amazing course!");
                        createReview(reviewRepository, c, student, 4, "Great content but fast paced.");
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

    private void createCourse(CourseRepository repo, User instructor, Category category,
            String title, String subtitle, Double price, String img, boolean published) {
        Course c = new Course();
        c.setTitle(title);
        c.setSubtitle(subtitle);
        c.setDescription(subtitle); // Use subtitle as desc for now
        c.setPrice(price);
        c.setInstructor(instructor);
        c.setCategory(category);
        c.setImageUrl(img);
        c.setLanguage("English");
        c.setLevel("All Levels");
        c.setPublished(published);
        c.setLastUpdated("02/2026");
        repo.save(c);
    }

    private void createReview(ReviewRepository repo, Course course, User user, Integer rating, String comment) {
        Review r = new Review();
        r.setCourse(course);
        r.setUser(user);
        r.setRating(rating);
        r.setComment(comment); // Fixed: setContent -> setComment
        repo.save(r);
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
                // Ignore if in use or other error, just best effort cleanup
            }
        }
    }
}
