package com.skillhub.controller;

import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import com.skillhub.service.CategoryService;
import com.skillhub.model.Category;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PageController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private com.skillhub.repository.VideoResourceRepository videoResourceRepository;

    @ModelAttribute
    public void forceSessionAndCsrf(HttpServletRequest request) {
        // Force session creation and set a dummy attribute to ensure headers are sent early
        request.getSession(true).setAttribute("init", true);
        // Eagerly resolve CSRF token
        org.springframework.security.web.csrf.CsrfToken token = (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (token != null) {
            token.getToken(); // Forces generation/loading in the controller phase
        }
    }

    @GetMapping("/")
    public String index(Model model, Principal principal, HttpServletRequest request,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String query) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());

        // Add videos to the model for the home page
        model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());

        if (query != null && !query.isEmpty()) {
            model.addAttribute("courses", courseService.searchCourses(query));
            model.addAttribute("searchQuery", query);
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        } else if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("courses", courseService.getCoursesByCategory(category));
            model.addAttribute("activeCategory", category.getName()); // Changed from ID to Name
            model.addAttribute("selectedCategoryId", categoryId); // Keep ID for highlighting tabs
            model.addAttribute("videos", videoResourceRepository.findByCategoryOrderByUploadDateDesc(category));
        } else {
            model.addAttribute("courses", courseService.getPublishedCourses());
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        }
        return "main_index";
    }

    @GetMapping("/user/home")
    public String home(Model model, Principal principal, HttpServletRequest request,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String query) {
        if (principal == null) {
            return "redirect:/login";
        }
        String email = principal.getName();
        com.skillhub.model.User user = userService.findByEmail(email);
        model.addAttribute("user", user);
        model.addAttribute("categories", categoryService.getAllCategories());

        // CERTIFICATE LOGIC
        // Find latest earned certificate
        com.skillhub.model.Enrollment latestCert = null;
        // Find a course ready for exam (100% progress but no exam passed)
        com.skillhub.model.Enrollment pendingCert = null;

        if (user.getEnrollments() != null) {
            for (com.skillhub.model.Enrollment e : user.getEnrollments()) {
                if (e.isExamPassed()) {
                    if (latestCert == null || e.getCertificateDate().isAfter(latestCert.getCertificateDate())) {
                        latestCert = e;
                    }
                } else if (pendingCert == null && e.getProgressPercentage() >= 100.0) {
                    pendingCert = e;
                }
            }
        }
        model.addAttribute("latestCert", latestCert);
        model.addAttribute("pendingCert", pendingCert);

        if (query != null && !query.isEmpty()) {
            model.addAttribute("courses", courseService.searchCourses(query));
            model.addAttribute("searchQuery", query);
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        } else if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            if (category != null) {
                model.addAttribute("courses", courseService.getCoursesByCategory(category));
                model.addAttribute("activeCategory", category.getName()); // Changed from ID to Name
                model.addAttribute("selectedCategoryId", categoryId); // Keep ID for highlighting tabs
                model.addAttribute("videos", videoResourceRepository.findByCategoryOrderByUploadDateDesc(category));
            } else {
                model.addAttribute("courses", courseService.getPublishedCourses());
                model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
            }
        } else {
            model.addAttribute("courses", courseService.getPublishedCourses());
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        }
        return "main_index";
    }

    @GetMapping("/about")
    public String about(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "contact";
    }

    @GetMapping("/terms")
    public String terms(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "privacy";
    }

    @GetMapping("/blog")
    public String blog(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "blog";
    }
}
