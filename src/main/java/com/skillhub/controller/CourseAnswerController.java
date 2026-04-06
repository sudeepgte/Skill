package com.skillhub.controller;

import com.skillhub.model.CourseAnswer;
import com.skillhub.model.CourseQuestion;
import com.skillhub.model.User;
import com.skillhub.repository.CourseAnswerRepository;
import com.skillhub.repository.CourseQuestionRepository;
import com.skillhub.service.UserService;
import com.skillhub.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/course/answers")
public class CourseAnswerController {

    @Autowired
    private CourseAnswerRepository answerRepository;

    @Autowired
    private CourseQuestionRepository questionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @PostMapping
    public String addAnswer(@RequestParam("questionId") Long questionId,
            @RequestParam("content") String content,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        CourseQuestion question = questionRepository.findById(questionId).orElse(null);

        if (user != null && question != null) {
            CourseAnswer answer = new CourseAnswer();
            answer.setQuestion(question);
            answer.setUser(user);
            answer.setContent(content);

            answerRepository.save(answer);

            // Notify student via email
            try {
                if (question.getUser() != null) {
                    String subject = "New Answer to your Question: " + question.getTitle();
                    String text = "Hello " + question.getUser().getName() + ",\n\n" +
                            "The instructor has answered your question on \"" + question.getCourse().getTitle()
                            + "\":\n\n" +
                            "Question: " + question.getTitle() + "\n" +
                            "Answer: " + content + "\n\n" +
                            "Best regards,\nThe SkillHub Team";
                    emailService.sendSimpleMessage(question.getUser().getEmail(), subject, text);
                }
            } catch (Exception e) {
                System.err.println("Email notification failed: " + e.getMessage());
            }

            redirectAttributes.addFlashAttribute("success", "Your answer has been posted!");
            return "redirect:/course/" + question.getCourse().getId() + "?qa=true";
        }

        return "redirect:/";
    }
}
