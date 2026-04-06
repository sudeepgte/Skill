package com.skillhub.controller;

import com.skillhub.model.*;
import com.skillhub.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
@RequestMapping("/user/placement")
public class PlacementController {

    @Autowired
    private PlacementService placementService;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    // --- DASHBOARD ---
    @GetMapping
    public String getPlacementDashboard(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        model.addAttribute("user", user);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "placement/dashboard";
    }

    // --- INTERVIEW QUESTIONS ---
    @GetMapping("/interview")
    public String getInterviewQuestions(@RequestParam(name = "category", required = false) String category, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        List<InterviewQuestion> questions;
        if (category != null && !category.isEmpty()) {
            questions = placementService.getInterviewQuestionsByCategory(category);
        } else {
            questions = placementService.getAllInterviewQuestions();
        }
        model.addAttribute("questions", questions);
        model.addAttribute("category", category);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("user", userService.findByEmail(principal.getName()));
        return "placement/interview_questions";
    }

    // --- APTITUDE TESTS ---
    @GetMapping("/aptitude")
    public String getAptitudeTests(@RequestParam(name = "category", required = false) String category, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        List<AptitudeQuestion> questions;
        if (category != null && !category.isEmpty()) {
            questions = placementService.getAptitudeQuestionsByCategory(category);
        } else {
            questions = placementService.getRandomAptitudeQuestions(10); // Default 10 random questions
        }
        model.addAttribute("questions", questions);
        model.addAttribute("category", category);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("user", userService.findByEmail(principal.getName()));
        return "placement/aptitude_tests";
    }

    @PostMapping("/aptitude/submit")
    public String submitAptitude(@RequestParam Map<String, String> answers, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        int score = 0;
        int total = 0;
        
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            if (entry.getKey().startsWith("q_")) {
                total++;
                Long qId = Long.parseLong(entry.getKey().substring(2));
                // In a real app we'd fetch from service, but for simplicity:
                AptitudeQuestion q = placementService.getAllAptitudeQuestions().stream()
                        .filter(quest -> quest.getId().equals(qId)).findFirst().orElse(null);
                if (q != null && q.getCorrectOption().equalsIgnoreCase(entry.getValue())) {
                    score++;
                }
            }
        }
        
        model.addAttribute("score", score);
        model.addAttribute("total", total);
        model.addAttribute("user", userService.findByEmail(principal.getName()));
        model.addAttribute("percent", total > 0 ? (double) score / total * 100 : 0);
        return "placement/aptitude_result";
    }

    // --- RESUME BUILDER ---
    @GetMapping("/resume")
    public String getResumeBuilder(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        UserResume resume = placementService.getResumeByUser(user);
        model.addAttribute("resume", resume);
        model.addAttribute("user", user);
        return "placement/resume_builder";
    }

    @PostMapping("/resume/save")
    public String saveResume(@ModelAttribute UserResume resume, Principal principal) {
        if (principal == null) return "redirect:/login";
        User user = userService.findByEmail(principal.getName());
        resume.setUser(user);
        placementService.saveResume(resume);
        return "redirect:/user/placement/resume?success=true";
    }
}
