package com.skillhub.controller;

import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("courses", courseService.getAllCourses());
        return "index";
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        String email = principal.getName();
        model.addAttribute("user", userService.findByEmail(email));
        model.addAttribute("courses", courseService.getAllCourses());
        return "home";
    }
}
