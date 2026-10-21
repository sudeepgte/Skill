package com.skillhub.controller;

import com.skillhub.model.Category;
import com.skillhub.model.User;
import com.skillhub.service.CategoryService;
import com.skillhub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        // Force session creation to avoid "Cannot create a session after the response
        // has been committed"
        // during Thymeleaf rendering of CSRF token
        request.getSession(true);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "login-page";
    }

    @GetMapping("/login_test")
    public String loginTest() {
        return "login_test";
    }

    @GetMapping("/register")
    public String register(Model model, HttpServletRequest request) {
        request.getSession(true);
        model.addAttribute("user", new User());
        model.addAttribute("isAdmin", false);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "register";
    }

    @GetMapping("/register/admin")
    public String registerAdmin(Model model, HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream().noneMatch(a -> "ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()))) {
            return "redirect:/login";
        }
        request.getSession(true);
        User user = new User();
        user.setRole("ADMIN");
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", true);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "register";
    }

    @PostMapping("/saveUser")
    public String saveUser(@ModelAttribute("user") User user,
                           @RequestParam(value = "role", defaultValue = "STUDENT") String role,
                           @RequestParam(value = "categoryName", required = false) String categoryName,
                           @RequestParam(value = "newCategoryName", required = false) String newCategoryName,
                           Authentication authentication) {
        try {
            // Check if email exists
            if (userService.findByEmail(user.getEmail()) != null) {
                return "redirect:/register?error=exists";
            }
            
            // Restrict ADMIN role assignment to authenticated admins only
            if ("ADMIN".equalsIgnoreCase(role)) {
                if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream().noneMatch(a -> "ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()))) {
                    return "redirect:/login";
                }
            }

            user.setRole(role);
            
            // Handling Vendor specific selections
            if ("INSTRUCTOR".equals(role)) {
                user.setApproved(true); // Auto-approved for immediate access as requested
                if ("Other".equals(categoryName) && newCategoryName != null && !newCategoryName.isBlank()) {
                    Category category = new Category(newCategoryName, "fas fa-star text-warning");
                    categoryService.saveCategory(category);
                    user.setSkills(newCategoryName);
                } else if (categoryName != null && !categoryName.isEmpty()) {
                    user.setSkills(categoryName);
                }
            }
            
            userService.saveUser(user);
            return "redirect:/login?success";
        } catch (Exception e) {
            return "redirect:/register?error=generic";
        }
    }
}
