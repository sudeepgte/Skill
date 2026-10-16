package com.skillhub.controller;

import com.skillhub.dto.*;
import com.skillhub.model.User;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.ProfessionalExamAttemptService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/professional-exams/attempts")
public class ProfessionalExamAttemptRestController {

    @Autowired
    private ProfessionalExamAttemptService attemptService;
    
    @Autowired
    private UserRepository userRepository;

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new com.skillhub.exception.BusinessException("UNAUTHORIZED");
        }
        return userRepository.findByEmail(authentication.getName());
    }

    @PostMapping("/start")
    public ResponseEntity<ExamAttemptOverviewResponse> startExam(@RequestBody ExamStartRequest request, Authentication auth) {
        return ResponseEntity.ok(attemptService.startExam(request, getAuthenticatedUser(auth)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamAttemptOverviewResponse> getAttemptOverview(@PathVariable("id") Long id, Authentication auth) {
        return ResponseEntity.ok(attemptService.getAttemptOverview(id, getAuthenticatedUser(auth)));
    }

    @GetMapping("/{id}/questions/{number}")
    public ResponseEntity<ExamQuestionResponse> getQuestion(@PathVariable("id") Long id, @PathVariable("number") int number, Authentication auth) {
        return ResponseEntity.ok(attemptService.getQuestion(id, number, getAuthenticatedUser(auth)));
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<AnswerSubmitResponse> saveAnswer(@PathVariable("id") Long id, 
                                                          @RequestParam("questionNumber") int questionNumber, 
                                                          @Valid @RequestBody AnswerSubmitRequest req, 
                                                          Authentication auth) {
        return ResponseEntity.ok(attemptService.saveAnswer(id, questionNumber, req, getAuthenticatedUser(auth)));
    }

    @PutMapping("/{id}/questions/{number}/review")
    public ResponseEntity<Void> markForReview(@PathVariable("id") Long id, 
                                             @PathVariable("number") int number, 
                                             @RequestParam("review") boolean review, 
                                             Authentication auth) {
        attemptService.markForReview(id, number, review, getAuthenticatedUser(auth));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/palette")
    public ResponseEntity<QuestionPaletteResponse> getPalette(@PathVariable("id") Long id, Authentication auth) {
        return ResponseEntity.ok(attemptService.getPalette(id, getAuthenticatedUser(auth)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submitExam(@PathVariable("id") Long id, Authentication auth) {
        attemptService.submitExam(id, getAuthenticatedUser(auth));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/execute-code")
    public ResponseEntity<CodeExecutionResult> executeCode(@PathVariable("id") Long id, 
                                                          @Valid @RequestBody CodingExecutionRequest req, 
                                                          Authentication auth) {
        return ResponseEntity.ok(attemptService.executeCode(id, req, getAuthenticatedUser(auth)));
    }
}
