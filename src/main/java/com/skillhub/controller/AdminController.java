package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.repository.*;
import com.skillhub.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private VideoResourceRepository videoResourceRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Analytics
        long totalUsers = userService.countUsers();
        long totalCourses = courseService.countCourses();
        long totalVideos = lectureRepository.count();
        long activeUsersToday = userService.countActiveUsers24h();

        // Most watched course & Total Views
        List<Course> allCourses = courseService.getAllCourses();
        long totalViews = 0;
        for (Course c : allCourses) {
            if (c.getViewCount() != null) {
                totalViews += c.getViewCount();
            }
        }
        Course mostWatched = courseRepository.findTopByOrderByViewCountDesc();

        // Pending user reports
        long pendingReports = reportRepository.findByStatus(Report.ReportStatus.PENDING).size();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalCourses", totalCourses);
        model.addAttribute("totalVideos", totalVideos);
        model.addAttribute("activeUsersToday", activeUsersToday);
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("mostWatchedCourse", mostWatched);
        model.addAttribute("pendingReports", pendingReports);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        // EXCLUDE INSTRUCTORS FROM THE STANDARD USERS TAB
        List<User> studentsAndAdmins = userService.getAllUsers().stream()
                .filter(u -> !"INSTRUCTOR".equals(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("users", studentsAndAdmins);
        return "admin/users";
    }

    @GetMapping("/vendors")
    public String listVendors(Model model) {
        // ONLY INSTRUCTORS FOR THE VENDORS TAB
        List<User> vendors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("users", vendors); // Keeping 'users' attribute name for easy template reuse
        return "admin/vendors";
    }

    @PostMapping("/users/block")
    public String blockUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.blockUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/unblock")
    public String unblockUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.unblockUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/approve")
    public String approveUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.approveUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam("id") Long id, HttpServletRequest request) {
        userService.deleteUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @GetMapping("/courses")
    public String listCourses(Model model) {
        model.addAttribute("courses", courseService.getAllCourses());
        return "admin/courses";
    }

    @PostMapping("/courses/delete")
    public String deleteCourse(@RequestParam("id") Long id) {
        courseService.deleteCourse(id);
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/publish")
    public String publishCourse(@RequestParam("id") Long id) {
        courseService.publishCourse(id);
        return "redirect:/admin/courses";
    }


    @GetMapping("/courses/create")
    public String createDraftAndRedirect(Principal principal) {
        User instructor = userService.findByEmail(principal.getName());

        Course course = new Course();
        course.setTitle("Untitled Course");
        course.setInstructor(instructor);
        course.setPublished(true);
        course.setViewCount(0L);
        course.setPrice(0.0); // Default to avoid null errors

        courseService.saveCourse(course);

        return "redirect:/admin/courses/edit?id=" + course.getId();
    }

    @GetMapping("/courses/edit")
    public String editCourse(@RequestParam("id") Long id, Model model) {
        Course course = courseService.getCourseById(id);
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/course_form";
    }

    @PostMapping("/courses/save")
    public String saveCourse(
            @ModelAttribute("course") Course course,
            @RequestParam(name = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            Principal principal) {

        if (course.getId() != null) {
            // Update existing course
            Course existing = courseService.getCourseById(course.getId());
            if (existing != null) {
                existing.setTitle(course.getTitle());
                existing.setSubtitle(course.getSubtitle());
                existing.setCategory(course.getCategory());
                existing.setSubCategory(course.getSubCategory());
                existing.setPrice(course.getPrice());
                existing.setLevel(course.getLevel());
                existing.setLanguage(course.getLanguage());
                existing.setImageUrl(course.getImageUrl());
                existing.setLongDescription(course.getLongDescription());
                existing.setLearningObjectives(course.getLearningObjectives());
                existing.setRequirements(course.getRequirements());

                // Handle Video Update
                if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
                    existing.setVideoUrl(youtubeUrl);
                } else if (videoFile != null && !videoFile.isEmpty()) {
                    String path = fileStorageService.store(videoFile);
                    existing.setVideoUrl(path);
                }

                // Preserve instructor, published status, view count, etc.
                courseService.saveCourse(existing);
            }
        } else {
            // Create new course
            User instructor = userService.findByEmail(principal.getName());
            course.setInstructor(instructor);
            course.setPublished(true); // Always published

            // Handle Video Upload
            if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
                course.setVideoUrl(youtubeUrl);
            } else if (videoFile != null && !videoFile.isEmpty()) {
                String path = fileStorageService.store(videoFile);
                course.setVideoUrl(path);
            }

            courseService.saveCourse(course);
        }
        return "redirect:/admin/courses/edit?id=" + course.getId();
    }

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getRootCategories());
        model.addAttribute("allCategories", categoryService.getAllCategories());
        model.addAttribute("newCategory", new Category());
        return "admin/categories";
    }

    @PostMapping("/categories/add")
    public String addCategory(
            @ModelAttribute Category category,
            RedirectAttributes redirectAttributes) {
        if (category.getIcon() == null || category.getIcon().isEmpty()) {
            category.setIcon("fas fa-folder");
        }
        categoryService.saveCategory(category);
        redirectAttributes.addFlashAttribute("success", "Category created successfully!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/edit")
    public String editCategory(
            @RequestParam("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("icon") String icon,
            RedirectAttributes redirectAttributes) {
        Category category = categoryService.getCategoryById(id);
        if (category != null) {
            category.setName(name);
            category.setIcon(icon);
            categoryService.saveCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully!");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/delete")
    public String deleteCategory(@RequestParam("id") Long id,
                               RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete category. It might be linked to courses.");
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/upload";
    }

    @PostMapping("/upload")
    public String uploadVideo(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "title") String title,
            @RequestParam(name = "categoryId") Long categoryId,
            @RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            RedirectAttributes redirectAttributes) {

        if ((file == null || file.isEmpty()) && (youtubeUrl == null || youtubeUrl.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please provide a video file OR a YouTube URL.");
            return "redirect:/admin/upload";
        }

        VideoResource video = new VideoResource();
        video.setTitle(title);
        video.setCategory(categoryService.getCategoryById(categoryId));
        video.setUploadDate(LocalDateTime.now());

        if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
            video.setFilePath(youtubeUrl);
            video.setDescription("YouTube Video");
        } else {
            String path = fileStorageService.store(file);
            video.setFilePath(path);
            video.setDescription("Uploaded Video");
        }

        videoResourceRepository.save(video);

        redirectAttributes.addFlashAttribute("success",
                "Video resource added successfully! You can find it in the Video Library.");
        return "redirect:/library";
    }

    @GetMapping("/broadcast")
    public String showBroadcastForm() {
        return "admin/broadcast";
    }

    @PostMapping("/broadcast")
    public String sendBroadcast(@RequestParam("subject") String subject,
            @RequestParam("message") String message,
            RedirectAttributes redirectAttributes) {

        // Mock email sending
        System.out.println("BROADCAST EMAIL: Subject=" + subject + ", Message=" + message);

        // Send System Notification to ALL users (Effective Broadcast)
        List<User> allUsers = userService.getAllUsers();
        for (User user : allUsers) {
            notificationService.createAndSend(user, subject, message, "INFO");
        }

        redirectAttributes.addFlashAttribute("success", "Broadcast sent to " + allUsers.size() + " users!");
        return "redirect:/admin/broadcast";
    }

    @GetMapping("/profile")
    public String adminProfile(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("admin", userService.findByEmail(principal.getName()));
        }
        return "admin/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User admin = userService.findByEmail(principal.getName());

        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Incorrect current password.");
            return "redirect:/admin/profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/admin/profile";
        }

        admin.setPassword(passwordEncoder.encode(newPassword));
        userService.updateUser(admin);

        redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        return "redirect:/admin/profile";
    }

    @GetMapping("/reports")
    public String listReports(Model model) {
        model.addAttribute("reports", reportRepository.findAll());
        return "admin/reports";
    }

    @PostMapping("/reports/status")
    public String updateReportStatus(@RequestParam("id") Long id, 
                                   @RequestParam("status") Report.ReportStatus status) {
        reportRepository.findById(id).ifPresent(report -> {
            report.setStatus(status);
            reportRepository.save(report);
        });
        return "redirect:/admin/reports";
    }

    @GetMapping("/exams")
    public String listExams(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("courses", courseService.getAllCourses());
        return "admin/exams";
    }

    @GetMapping("/courses/{id}/questions")
    public String manageExamQuestions(@PathVariable("id") Long id, Model model, Principal principal) {
        Course course = courseService.getCourseById(id);
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("course", course);
        model.addAttribute("questions", course.getExamQuestions());
        return "admin/manage_questions";
    }

    @PostMapping("/courses/{id}/questions/add")
    public String addExamQuestion(@PathVariable("id") Long id,
            @RequestParam("questionText") String questionText,
            @RequestParam("optionA") String optionA,
            @RequestParam("optionB") String optionB,
            @RequestParam("optionC") String optionC,
            @RequestParam("optionD") String optionD,
            @RequestParam("correctOption") String correctOption) {
        Course course = courseService.getCourseById(id);
        if (course != null) {
            ExamQuestion q = new ExamQuestion(course, questionText, optionA,
                    optionB, optionC, optionD, correctOption);
            course.getExamQuestions().add(q);
            courseService.saveCourse(course);
        }
        return "redirect:/admin/courses/" + id + "/questions";
    }

    @PostMapping("/courses/{id}/questions/delete/{qid}")
    public String deleteExamQuestion(@PathVariable("id") Long id,
            @PathVariable("qid") Long qid) {
        Course course = courseService.getCourseById(id);
        if (course != null) {
            course.getExamQuestions().removeIf(q -> q.getId().equals(qid));
            courseService.saveCourse(course);
        }
        return "redirect:/admin/courses/" + id + "/questions";
    }
}
