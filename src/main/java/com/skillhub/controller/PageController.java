package com.skillhub.controller;

import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import com.skillhub.service.CategoryService;
import com.skillhub.model.Category;
import com.skillhub.model.User;
import com.skillhub.model.Enrollment;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

    @Autowired
    private com.skillhub.repository.WishlistRepository wishlistRepository;

    @ModelAttribute
    public void forceSessionAndCsrf(HttpServletRequest request) {
        // Force session creation and set a dummy attribute to ensure headers are sent
        // early
        request.getSession(true).setAttribute("init", true);
        // Eagerly resolve CSRF token
        org.springframework.security.web.csrf.CsrfToken token = (org.springframework.security.web.csrf.CsrfToken) request
                .getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        if (token != null) {
            token.getToken(); // Forces generation/loading in the controller phase
        }
    }

    @GetMapping("/")
    public String index(Model model, Principal principal, HttpServletRequest request,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "query", required = false) String query) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            User user = userService.findByEmail(principal.getName());
            model.addAttribute("user", user);
            if (user != null) {
                if (user.getEnrollments() != null) {
                    Set<Long> enrolledIds = user.getEnrollments().stream()
                            .map(e -> e.getCourse().getId())
                            .collect(Collectors.toSet());
                    model.addAttribute("enrolledCourseIds", enrolledIds);
                }
                // Add wishlist Ids
                java.util.Set<Long> wishlistIds = wishlistRepository.findByUser(user).stream()
                        .map(w -> w.getCourse().getId())
                        .collect(java.util.stream.Collectors.toSet());
                model.addAttribute("wishlistCourseIds", wishlistIds);

                // CERTIFICATE LOGIC
                // Find latest earned certificate
                com.skillhub.model.Enrollment latestCert = null;
                // Find a course ready for exam (100% progress but no exam passed)
                com.skillhub.model.Enrollment pendingCert = null;

                if (user.getEnrollments() != null) {
                    for (com.skillhub.model.Enrollment e : user.getEnrollments()) {
                        if (e.isExamPassed()) {
                            if (e.getCertificateDate() != null && (latestCert == null || (latestCert.getCertificateDate() != null && e.getCertificateDate().isAfter(latestCert.getCertificateDate())))) {
                                latestCert = e;
                            }
                        } else if (pendingCert == null && e.getProgressPercentage() >= 100.0) {
                            pendingCert = e;
                        }
                    }
                }
                model.addAttribute("latestCert", latestCert);
                model.addAttribute("pendingCert", pendingCert);
            }
        }
        model.addAttribute("categories", categoryService.getAllCategories());

        // Load approved and unblocked instructors
        List<User> instructors = userService.getAllUsers().stream()
                .filter(u -> "INSTRUCTOR".equals(u.getRole()) && u.isApproved() && !u.isBlocked())
                .collect(Collectors.toList());
        model.addAttribute("instructors", instructors);

        // Add videos to the model for the home page
        model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());

        if (query != null && !query.isEmpty()) {
            model.addAttribute("courses", courseService.searchCourses(query));
            model.addAttribute("searchedCategories", categoryService.searchCategories(query));
            model.addAttribute("searchQuery", query);
            
            // For Exams, we return courses matching the query as reachable exams
            model.addAttribute("exams", courseService.searchCourses(query));
            
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        } else if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            if (category != null) {
                model.addAttribute("courses", courseService.getCoursesByCategory(category));
                model.addAttribute("activeCategory", category.getName());
                model.addAttribute("selectedCategoryId", categoryId);
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

    @GetMapping("/user/home")
    public String home(Model model, Principal principal, HttpServletRequest request,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "query", required = false) String query) {
        if (principal == null || principal.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }
        String email = principal.getName();
        com.skillhub.model.User user = userService.findByEmail(email);

        if (user != null && "INSTRUCTOR".equalsIgnoreCase(user.getRole())) {
            return "redirect:/vendor/dashboard";
        }

        model.addAttribute("user", user);
        if (user != null && user.getEnrollments() != null) {
            Set<Long> enrolledIds = user.getEnrollments().stream()
                    .map(e -> e.getCourse().getId())
                    .collect(Collectors.toSet());
            model.addAttribute("enrolledCourseIds", enrolledIds);
        }
        // Add wishlist Ids
        java.util.Set<Long> wishlistIds = wishlistRepository.findByUser(user).stream()
                .map(w -> w.getCourse().getId())
                .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("wishlistCourseIds", wishlistIds);

        model.addAttribute("categories", categoryService.getAllCategories());

        // CERTIFICATE LOGIC
        // Find latest earned certificate
        com.skillhub.model.Enrollment latestCert = null;
        // Find a course ready for exam (100% progress but no exam passed)
        com.skillhub.model.Enrollment pendingCert = null;

        if (user.getEnrollments() != null) {
            for (com.skillhub.model.Enrollment e : user.getEnrollments()) {
                if (e.isExamPassed()) {
                    if (e.getCertificateDate() != null && (latestCert == null || (latestCert.getCertificateDate() != null && e.getCertificateDate().isAfter(latestCert.getCertificateDate())))) {
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
            model.addAttribute("searchedCategories", categoryService.searchCategories(query));
            model.addAttribute("exams", courseService.searchCourses(query));
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
        return "redirect:/";
    }

    @GetMapping("/about")
    public String about(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "about";
    }

    @GetMapping("/contact")
    public String showContactPage(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "contact";
    }

    @GetMapping("/terms")
    public String terms(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "privacy";
    }

    @GetMapping("/blog")
    public String blog(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "blog";
    }

    @GetMapping("/business")
    public String business(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "business";
    }

    @GetMapping("/faq")
    public String faq(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "faq";
    }

    @GetMapping("/help")
    public String help(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "help";
    }

    @GetMapping("/cookies")
    public String cookies(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "cookies";
    }

    @GetMapping("/accessibility")
    public String accessibility(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "accessibility";
    }

    @GetMapping("/teaching")
    public String teaching(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "teaching";
    }

    @GetMapping("/app")
    public String getApp(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null && !principal.getName().equals("anonymousUser")) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "app";
    }

    @org.springframework.web.bind.annotation.PostMapping(value = {"/api/newsletter/subscribe", "/newsletter/subscribe"})
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> subscribeNewsletter(
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, String> body,
            @RequestParam(name = "email", required = false) String emailParam) {
        String email = null;
        if (body != null && body.containsKey("email")) {
            email = body.get("email");
        } else if (emailParam != null) {
            email = emailParam;
        }

        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please provide a valid email address."
            ));
        }

        return org.springframework.http.ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Thank you for subscribing to the SkillHub newsletter!"
        ));
    }
}
