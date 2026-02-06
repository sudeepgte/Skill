package com.skillhub.controller;

import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private com.skillhub.repository.LectureRepository lectureRepository;

    @Autowired
    private com.skillhub.repository.ReportRepository reportRepository;

    @Autowired
    private com.skillhub.repository.CourseRepository courseRepository;

    @Autowired
    private com.skillhub.service.CategoryService categoryService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Analytics
        long totalUsers = userService.countUsers();
        long totalCourses = courseService.countCourses();
        long totalVideos = lectureRepository.count();
        long activeUsersToday = userService.countActiveUsers24h();

        // Most watched course
        com.skillhub.model.Course mostWatched = courseRepository.findTopByOrderByViewCountDesc();

        // Pending user reports
        long pendingReports = reportRepository.findByStatus(com.skillhub.model.Report.ReportStatus.PENDING).size();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalCourses", totalCourses);
        model.addAttribute("totalVideos", totalVideos);
        model.addAttribute("activeUsersToday", activeUsersToday);
        model.addAttribute("mostWatchedCourse", mostWatched);
        model.addAttribute("pendingReports", pendingReports);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "admin/users";
    }

    @PostMapping("/users/block")
    public String blockUser(@org.springframework.web.bind.annotation.RequestParam Long id) {
        userService.blockUser(id);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/unblock")
    public String unblockUser(@org.springframework.web.bind.annotation.RequestParam Long id) {
        userService.unblockUser(id);
        return "redirect:/admin/users";
    }

    @GetMapping("/courses")
    public String listCourses(Model model) {
        model.addAttribute("courses", courseService.getAllCourses());
        return "admin/courses";
    }

    @PostMapping("/courses/delete")
    public String deleteCourse(Long id) {
        courseService.deleteCourse(id);
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/publish")
    public String publishCourse(Long id) {
        courseService.publishCourse(id);
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/unpublish")
    public String unpublishCourse(Long id) {
        courseService.unpublishCourse(id);
        return "redirect:/admin/courses";
    }

    @GetMapping("/courses/create")
    public String showCreateCourseForm(Model model) {
        model.addAttribute("course", new com.skillhub.model.Course());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/course_form";
    }

    @PostMapping("/courses/save")
    public String saveCourse(
            @org.springframework.web.bind.annotation.ModelAttribute("course") com.skillhub.model.Course course,
            java.security.Principal principal) {
        // Set instructor to current logged in admin
        com.skillhub.model.User instructor = userService.findByEmail(principal.getName());
        course.setInstructor(instructor);
        course.setPublished(false); // Default to draft

        courseService.saveCourse(course);
        return "redirect:/admin/courses";
    }

    @Autowired
    private com.skillhub.service.FileStorageService fileStorageService;

    @Autowired
    private com.skillhub.repository.VideoResourceRepository videoResourceRepository;

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getRootCategories());
        model.addAttribute("allCategories", categoryService.getAllCategories());
        model.addAttribute("newCategory", new com.skillhub.model.Category());
        return "admin/categories";
    }

    @PostMapping("/categories/add")
    public String addCategory(
            @org.springframework.web.bind.annotation.ModelAttribute com.skillhub.model.Category category,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (category.getIcon() == null || category.getIcon().isEmpty()) {
            category.setIcon("fas fa-folder");
        }
        categoryService.saveCategory(category);
        redirectAttributes.addFlashAttribute("success", "Category created successfully!");
        return "redirect:/admin/categories";
    }

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/upload";
    }

    @PostMapping("/upload")
    public String uploadVideo(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam("title") String title,
            @org.springframework.web.bind.annotation.RequestParam("categoryId") Long categoryId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        String filePath = fileStorageService.store(file);

        com.skillhub.model.VideoResource video = new com.skillhub.model.VideoResource();
        video.setTitle(title);
        video.setFilePath(filePath);
        video.setCategory(categoryService.getCategoryById(categoryId));
        video.setUploadDate(java.time.LocalDateTime.now());

        videoResourceRepository.save(video);

        redirectAttributes.addFlashAttribute("success", "Video uploaded and categorized successfully!");
        return "redirect:/admin/upload";
    }

    @GetMapping("/broadcast")
    public String showBroadcastForm() {
        return "admin/broadcast";
    }

    @Autowired
    private com.skillhub.service.NotificationService notificationService;

    @PostMapping("/broadcast")
    public String sendBroadcast(@org.springframework.web.bind.annotation.RequestParam String subject,
            @org.springframework.web.bind.annotation.RequestParam String message,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        // Mock email sending
        System.out.println("BROADCAST EMAIL: Subject=" + subject + ", Message=" + message);

        // Send System Notification to ALL users (Effective Broadcast)
        java.util.List<com.skillhub.model.User> allUsers = userService.getAllUsers();
        for (com.skillhub.model.User user : allUsers) {
            notificationService.createNotification(user, subject, message, "INFO");
        }

        redirectAttributes.addFlashAttribute("success", "Broadcast sent to " + allUsers.size() + " users!");
        return "redirect:/admin/broadcast";
    }
}
