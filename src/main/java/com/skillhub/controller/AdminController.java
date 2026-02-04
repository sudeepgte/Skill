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

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Analytics
        long totalUsers = userService.countUsers(); // Need to implement these methods
        long totalCourses = courseService.countCourses();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalCourses", totalCourses);
        // Add more stats if needed

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "admin/users";
    }

    @GetMapping("/courses")
    public String listCourses(Model model) {
        model.addAttribute("courses", courseService.getAllCourses());
        return "admin/courses";
    }

    @PostMapping("/users/delete")
    public String deleteUser(Long id) {
        userService.deleteUser(id);
        return "redirect:/admin/users";
    }

    @PostMapping("/courses/delete")
    public String deleteCourse(Long id) {
        courseService.deleteCourse(id);
        return "redirect:/admin/courses";
    }
}
