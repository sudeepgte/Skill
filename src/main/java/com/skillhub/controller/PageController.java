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

    @Autowired
    private com.skillhub.service.SectionService sectionService;

    @Autowired
    private com.skillhub.repository.CourseRepository courseRepository;

    @GetMapping("/")
    public String index(Model model,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sectionId) {
        model.addAttribute("sections", sectionService.getEnabledSections());
        if (sectionId != null) {
            com.skillhub.model.Section section = sectionService.getSectionById(sectionId);
            model.addAttribute("courses", courseRepository.findBySection(section));
            model.addAttribute("activeSection", sectionId);
        } else {
            model.addAttribute("courses", courseService.getAllCourses());
        }
        return "index";
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sectionId) {
        String email = principal.getName();
        model.addAttribute("user", userService.findByEmail(email));
        model.addAttribute("sections", sectionService.getEnabledSections());

        if (sectionId != null) {
            com.skillhub.model.Section section = sectionService.getSectionById(sectionId);
            model.addAttribute("courses", courseRepository.findBySection(section));
            model.addAttribute("activeSection", sectionId);
        } else {
            model.addAttribute("courses", courseService.getAllCourses());
        }
        return "home";
    }
}
