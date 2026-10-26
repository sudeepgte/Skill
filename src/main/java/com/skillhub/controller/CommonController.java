package com.skillhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CommonController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.CourseService courseService;

    @GetMapping("/courses/{category}")
    public String browseCategory(@org.springframework.web.bind.annotation.PathVariable("category") String category,
            org.springframework.ui.Model model) {
        model.addAttribute("categoryName", category);
        model.addAttribute("courses", courseService.getCoursesByCategory(category));
        return "common/category_courses";
    }

    @GetMapping("/courses")
    public String browseAllCourses(org.springframework.ui.Model model) {
        model.addAttribute("categoryName", "All");
        model.addAttribute("courses", courseService.getPublishedCourses());
        return "common/category_courses";
    }



    @GetMapping("/pricing")
    public String pricing(org.springframework.ui.Model model, java.security.Principal principal) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "common/pricing";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.repository.VideoResourceRepository videoResourceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.CategoryService categoryService;

    @GetMapping("/library")
    public String videoLibrary(@org.springframework.web.bind.annotation.RequestParam(value = "categoryId", required = false) Long categoryId,
            org.springframework.ui.Model model) {

        if (categoryId != null) {
            com.skillhub.model.Category cat = categoryService.getCategoryById(categoryId);
            model.addAttribute("activeCategory", cat);
            model.addAttribute("videos", videoResourceRepository.findByCategory(cat));
        } else {
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        }

        model.addAttribute("categories", categoryService.getAllCategories());
        return "common/video_library";
    }

    @GetMapping("/user/edit-profile")
    public String editProfile() {
        return "redirect:/profile?tab=profile";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.UserService userService;

    @GetMapping("/instructors")
    public String listAllInstructors(org.springframework.ui.Model model) {
        java.util.List<com.skillhub.model.User> instructors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()) && u.isApproved() && !u.isBlocked())
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("instructors", instructors);
        return "common/instructors_list";
    }

    @GetMapping("/instructor/{id}")
    public String viewInstructorDetails(@org.springframework.web.bind.annotation.PathVariable("id") Long id, org.springframework.ui.Model model) {
        com.skillhub.model.User instructor = userService.getUserById(id);
        if (instructor == null || !"INSTRUCTOR".equals(instructor.getRole()) || !instructor.isApproved() || instructor.isBlocked()) {
            return "redirect:/";
        }
        model.addAttribute("instructor", instructor);
        java.util.List<com.skillhub.model.Course> pubCourses = courseService.getCoursesByInstructorAndStatus(instructor.getEmail(), com.skillhub.model.CourseStatus.PUBLISHED);
        if (pubCourses != null) {
            pubCourses.removeIf(c -> c.getStatus() != com.skillhub.model.CourseStatus.PUBLISHED || !c.isPublished());
        }
        model.addAttribute("courses", pubCourses != null ? pubCourses : java.util.List.of());
        return "common/instructor_details";
    }
}
