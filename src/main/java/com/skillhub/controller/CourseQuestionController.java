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
import com.skillhub.service.EmailService;

import java.security.Principal;

@Controller
@RequestMapping("/course/questions")
public class CourseQuestionController {

    @Autowired
    private CourseQuestionRepository questionRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @PostMapping
    public String addQuestion(@RequestParam("courseId") Long courseId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
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

            // Notify instructor via email
            try {
                if (course.getInstructor() != null) {
                    String subject = "New Question: " + course.getTitle();
                    String text = "Hello " + course.getInstructor().getName() + ",\n\n" +
                            "A student has asked a question on your course \"" + course.getTitle() + "\":\n\n" +
                            "Title: " + title + "\n" +
                            "Content: " + content + "\n\n" +
                            "Please log in to SkillHub to answer it.\n\n" +
                            "Best regards,\nThe SkillHub Team";
                    emailService.sendSimpleMessage(course.getInstructor().getEmail(), subject, text);
                }
            } catch (Exception e) {
                System.err.println("Email notification failed: " + e.getMessage());
            }

            redirectAttributes.addFlashAttribute("success", "Your question has been posted!");
        }

        return "redirect:/course/" + courseId + "?qa=true";
    }
}
