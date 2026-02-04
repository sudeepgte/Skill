package com.skillhub.controller;

import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import com.skillhub.service.CategoryService;
import com.skillhub.repository.CourseRepository;
import com.skillhub.model.Category;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping("/")
    public String index(Model model, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String query) {
        model.addAttribute("categories", categoryService.getAllCategories());

        if (query != null && !query.isEmpty()) {
            model.addAttribute("courses", courseService.searchCourses(query));
            model.addAttribute("searchQuery", query);
        } else if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("courses", courseRepository.findByCategory(category));
            model.addAttribute("activeCategory", categoryId);
        } else {
            model.addAttribute("courses", courseService.getAllCourses());
        }
        return "index";
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String query) {
        String email = principal.getName();
        model.addAttribute("user", userService.findByEmail(email));
        model.addAttribute("categories", categoryService.getAllCategories());

        if (query != null && !query.isEmpty()) {
            model.addAttribute("courses", courseService.searchCourses(query));
            model.addAttribute("searchQuery", query);
        } else if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("courses", courseRepository.findByCategory(category));
            model.addAttribute("activeCategory", categoryId);
        } else {
            model.addAttribute("courses", courseService.getAllCourses());
        }
        return "home";
    }
}
