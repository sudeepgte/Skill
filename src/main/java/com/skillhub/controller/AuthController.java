package com.skillhub.controller;

import com.skillhub.model.Category;
import com.skillhub.model.User;
import com.skillhub.service.CategoryService;
import com.skillhub.service.UserService;
import com.skillhub.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
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

    @Autowired
    private FileStorageService fileStorageService;

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
                           @RequestParam(value = "aadharCardFile", required = false) MultipartFile aadharCardFile,
                           @RequestParam(value = "proofDocFile", required = false) MultipartFile proofDocFile,
                           Authentication authentication) {
        try {
            // Server-side validation & sanitization
            if (user.getName() == null || user.getName().trim().length() < 3) {
                return "redirect:/register?error=invalid_name";
            }
            if (user.getEmail() == null || !user.getEmail().trim().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,63}$")) {
                return "redirect:/register?error=invalid_email";
            }
            if (user.getPassword() == null || user.getPassword().length() < 8) {
                return "redirect:/register?error=invalid_password";
            }

            user.setName(user.getName().trim());
            user.setEmail(user.getEmail().trim().toLowerCase());

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
            
            // Handling Vendor/Instructor specific selections & document requirements
            if ("INSTRUCTOR".equalsIgnoreCase(role)) {
                // Must provide Aadhar card photo & proof document
                if (aadharCardFile == null || aadharCardFile.isEmpty() || proofDocFile == null || proofDocFile.isEmpty()) {
                    return "redirect:/register?error=missing_documents";
                }

                String aadharUrl = fileStorageService.store(aadharCardFile);
                String proofUrl = fileStorageService.store(proofDocFile);
                user.setAadharCardUrl(aadharUrl);
                user.setProofDocUrl(proofUrl);

                // Instructor accounts require manual approval by the administrator
                user.setApproved(false);

                if ("Other".equals(categoryName) && newCategoryName != null && !newCategoryName.isBlank()) {
                    Category category = new Category(newCategoryName.trim(), "fas fa-star text-warning");
                    categoryService.saveCategory(category);
                    user.setSkills(newCategoryName.trim());
                } else if (categoryName != null && !categoryName.isEmpty()) {
                    user.setSkills(categoryName.trim());
                }

                userService.saveUser(user);
                return "redirect:/login?pending_approval=true";
            } else {
                // Students and Admins auto-approved
                user.setApproved(true);
                userService.saveUser(user);
                return "redirect:/login?success";
            }
        } catch (Exception e) {
            return "redirect:/register?error=generic";
        }
    }
}