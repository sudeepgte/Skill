package com.skillhub.controller;

import com.skillhub.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @GetMapping("/{id}")
    public String getCourseDetails(@PathVariable Long id, Model model) {
        model.addAttribute("course", courseService.getCourseById(id).orElse(null));
        return "course-details";
    }
}
