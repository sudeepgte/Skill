package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.service.CategoryService categoryService;

    @GetMapping("/login")
    public String login(Model model, jakarta.servlet.http.HttpServletRequest request) {
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
    public String register(Model model, jakarta.servlet.http.HttpServletRequest request) {
        request.getSession(true);
        model.addAttribute("user", new User());
        model.addAttribute("isAdmin", false);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "register";
    }

    @GetMapping("/register/admin")
    public String registerAdmin(Model model, jakarta.servlet.http.HttpServletRequest request) {
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
                           @org.springframework.web.bind.annotation.RequestParam(value = "role", defaultValue = "STUDENT") String role,
                           @org.springframework.web.bind.annotation.RequestParam(value = "categoryName", required = false) String categoryName,
                           @org.springframework.web.bind.annotation.RequestParam(value = "newCategoryName", required = false) String newCategoryName) {
        try {
            // Check if email exists
            if (userService.findByEmail(user.getEmail()) != null) {
                return "redirect:/register?error=exists";
            }
            
            user.setRole(role);
            
            // Handling Vendor specific selections
            if ("INSTRUCTOR".equals(role)) {
                user.setApproved(false); // Vendor must be approved by admin before login
                if ("Other".equals(categoryName) && newCategoryName != null && !newCategoryName.isBlank()) {
                    com.skillhub.model.Category category = new com.skillhub.model.Category(newCategoryName, "fas fa-star text-warning");
                    categoryService.saveCategory(category);
                    user.setSkills(newCategoryName);
                } else if (categoryName != null && !categoryName.isEmpty()) {
                    user.setSkills(categoryName);
                }
            }
            
            userService.saveUser(user);
            if ("INSTRUCTOR".equals(role)) {
                return "redirect:/login?vendor_pending";
            }
            return "redirect:/login?success";
        } catch (Exception e) {
            return "redirect:/register?error=generic";
        }
    }
}
