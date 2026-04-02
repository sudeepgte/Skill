package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.CourseQuestion;
import com.skillhub.model.User;
import com.skillhub.repository.CourseQuestionRepository;
import com.skillhub.service.CourseService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/api/course/questions")
public class CourseQuestionController {

    @Autowired
    private CourseQuestionRepository questionRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @PostMapping
    public String addQuestion(@RequestParam Long courseId,
            @RequestParam String title,
            @RequestParam String content,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(courseId);

        if (course != null && user != null) {
            CourseQuestion question = new CourseQuestion();
            question.setCourse(course);
            question.setUser(user);
            question.setTitle(title);
            question.setContent(content);

            questionRepository.save(question);
            redirectAttributes.addFlashAttribute("success", "Your question has been posted!");
        }

        return "redirect:/course/" + courseId + "?qa=true";
    }
}
