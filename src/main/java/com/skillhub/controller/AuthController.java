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

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("isAdmin", false);
        return "register";
    }

    @GetMapping("/register/admin")
    public String registerAdmin(Model model) {
        User user = new User();
        user.setRole("ADMIN");
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", true);
        return "register";
    }

    @PostMapping("/saveUser")
    public String saveUser(@ModelAttribute("user") User user) {
        // In a real app, check if email exists
        if (userService.findByEmail(user.getEmail()) != null) {
            return "redirect:/register?error";
        }
        userService.saveUser(user);
        return "redirect:/login?success";
    }
}
