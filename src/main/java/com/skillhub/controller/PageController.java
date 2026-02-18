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

    @GetMapping("/")
    public String index(Model model, Principal principal, @RequestParam(required = false) Long categoryId,
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
            model.addAttribute("activeCategory", categoryId);
            model.addAttribute("videos", videoResourceRepository.findByCategoryOrderByUploadDateDesc(category));
        } else {
            model.addAttribute("courses", courseService.getPublishedCourses());
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        }
        return "main_index";
    }

    @GetMapping({ "/home", "/home/user" })
    public String home(Model model, Principal principal, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String query) {
        String email = principal.getName();
        model.addAttribute("user", userService.findByEmail(email));
        model.addAttribute("categories", categoryService.getAllCategories());

        if (query != null && !query.isEmpty()) {
            model.addAttribute("courses", courseService.searchCourses(query));
            model.addAttribute("searchQuery", query);
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        } else if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            model.addAttribute("courses", courseService.getCoursesByCategory(category));
            model.addAttribute("activeCategory", categoryId);
            model.addAttribute("videos", videoResourceRepository.findByCategoryOrderByUploadDateDesc(category));
        } else {
            model.addAttribute("courses", courseService.getPublishedCourses());
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        }
        return "main_index";
    }

    @GetMapping("/about")
    public String about(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "contact";
    }

    @GetMapping("/terms")
    public String terms(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByEmail(principal.getName()));
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "privacy";
    }
}
