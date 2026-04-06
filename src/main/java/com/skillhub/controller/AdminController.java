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
        // EXCLUDE INSTRUCTORS FROM THE STANDARD USERS TAB
        java.util.List<com.skillhub.model.User> studentsAndAdmins = userService.getAllUsers().stream()
                .filter(u -> !"INSTRUCTOR".equals(u.getRole()))
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("users", studentsAndAdmins);
        return "admin/users";
    }

    @GetMapping("/vendors")
    public String listVendors(Model model) {
        // ONLY INSTRUCTORS FOR THE VENDORS TAB
        java.util.List<com.skillhub.model.User> vendors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()))
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("users", vendors); // Keeping 'users' attribute name for easy template reuse
        return "admin/vendors";
    }

    @PostMapping("/users/block")
    public String blockUser(@org.springframework.web.bind.annotation.RequestParam("id") Long id, jakarta.servlet.http.HttpServletRequest request) {
        userService.blockUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/unblock")
    public String unblockUser(@org.springframework.web.bind.annotation.RequestParam("id") Long id, jakarta.servlet.http.HttpServletRequest request) {
        userService.unblockUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/approve")
    public String approveUser(@org.springframework.web.bind.annotation.RequestParam("id") Long id, jakarta.servlet.http.HttpServletRequest request) {
        userService.approveUser(id);
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/admin/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@org.springframework.web.bind.annotation.RequestParam("id") Long id, jakarta.servlet.http.HttpServletRequest request) {
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
    public String deleteCourse(@org.springframework.web.bind.annotation.RequestParam("id") Long id) {
        courseService.deleteCourse(id);
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/publish")
    public String publishCourse(@org.springframework.web.bind.annotation.RequestParam("id") Long id) {
        courseService.publishCourse(id);
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/unpublish")
    public String unpublishCourse(@org.springframework.web.bind.annotation.RequestParam("id") Long id) {
        courseService.unpublishCourse(id);
        return "redirect:/admin/courses";
    }




    @GetMapping("/courses/create")
    public String createDraftAndRedirect(java.security.Principal principal) {
        com.skillhub.model.User instructor = userService.findByEmail(principal.getName());

        com.skillhub.model.Course course = new com.skillhub.model.Course();
        course.setTitle("Untitled Course");
        course.setInstructor(instructor);
        course.setPublished(false);
        course.setViewCount(0L);
        course.setPrice(0.0); // Default to avoid null errors

        courseService.saveCourse(course);

        return "redirect:/admin/courses/edit?id=" + course.getId();
    }

    @GetMapping("/courses/edit")
    public String editCourse(@org.springframework.web.bind.annotation.RequestParam("id") Long id, Model model) {
        com.skillhub.model.Course course = courseService.getCourseById(id);
        model.addAttribute("course", course);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/course_form";
    }

    @PostMapping("/courses/save")
    public String saveCourse(
            @org.springframework.web.bind.annotation.ModelAttribute("course") com.skillhub.model.Course course,
            @org.springframework.web.bind.annotation.RequestParam(name = "videoFile", required = false) org.springframework.web.multipart.MultipartFile videoFile,
            @org.springframework.web.bind.annotation.RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            java.security.Principal principal) {

        if (course.getId() != null) {
            // Update existing course
            com.skillhub.model.Course existing = courseService.getCourseById(course.getId());
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
            com.skillhub.model.User instructor = userService.findByEmail(principal.getName());
            course.setInstructor(instructor);
            course.setPublished(false); // Default to draft

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

    @PostMapping("/categories/edit")
    public String editCategory(
            @org.springframework.web.bind.annotation.RequestParam("id") Long id,
            @org.springframework.web.bind.annotation.RequestParam("name") String name,
            @org.springframework.web.bind.annotation.RequestParam("icon") String icon,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        com.skillhub.model.Category category = categoryService.getCategoryById(id);
        if (category != null) {
            category.setName(name);
            category.setIcon(icon);
            categoryService.saveCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully!");
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/delete")
    public String deleteCategory(@org.springframework.web.bind.annotation.RequestParam("id") Long id,
                               org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
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
            @org.springframework.web.bind.annotation.RequestParam(name = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(name = "title") String title,
            @org.springframework.web.bind.annotation.RequestParam(name = "categoryId") Long categoryId,
            @org.springframework.web.bind.annotation.RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        if ((file == null || file.isEmpty()) && (youtubeUrl == null || youtubeUrl.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please provide a video file OR a YouTube URL.");
            return "redirect:/admin/upload";
        }

        com.skillhub.model.VideoResource video = new com.skillhub.model.VideoResource();
        video.setTitle(title);
        video.setCategory(categoryService.getCategoryById(categoryId));
        video.setUploadDate(java.time.LocalDateTime.now());

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

    @Autowired
    private com.skillhub.service.NotificationService notificationService;

    @PostMapping("/broadcast")
    public String sendBroadcast(@org.springframework.web.bind.annotation.RequestParam("subject") String subject,
            @org.springframework.web.bind.annotation.RequestParam("message") String message,
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

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    public String adminProfile(Model model, java.security.Principal principal) {
        if (principal != null) {
            model.addAttribute("admin", userService.findByEmail(principal.getName()));
        }
        return "admin/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@org.springframework.web.bind.annotation.RequestParam("oldPassword") String oldPassword,
            @org.springframework.web.bind.annotation.RequestParam("newPassword") String newPassword,
            @org.springframework.web.bind.annotation.RequestParam("confirmPassword") String confirmPassword,
            java.security.Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        com.skillhub.model.User admin = userService.findByEmail(principal.getName());

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
    public String updateReportStatus(@org.springframework.web.bind.annotation.RequestParam("id") Long id, 
                                   @org.springframework.web.bind.annotation.RequestParam("status") com.skillhub.model.Report.ReportStatus status) {
        reportRepository.findById(id).ifPresent(report -> {
            report.setStatus(status);
            reportRepository.save(report);
        });
        return "redirect:/admin/reports";
    }
}
