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

    @Autowired
    private com.skillhub.repository.ProfessionalExamAttemptRepository attemptRepository;

    @Autowired
    private com.skillhub.repository.ProfessionalExamSecurityEventRepository securityEventRepo;

    @Autowired
    private com.skillhub.repository.ProfessionalExamResultRepository resultRepo;

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir:uploads}")
    private String uploadDir;

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

    @PostMapping(value = "/{id}/recording", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadRecording(
            @PathVariable("id") Long id,
            @RequestParam("recording") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "warningsCount", defaultValue = "0") int warningsCount,
            @RequestParam(value = "duration", required = false) Long duration,
            Authentication auth) {
        User user = getAuthenticatedUser(auth);
        com.skillhub.model.ProfessionalExamAttempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new com.skillhub.exception.ResourceNotFoundException("Attempt not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && !attempt.getStudent().getId().equals(user.getId())) {
            throw new com.skillhub.exception.BusinessException("FORBIDDEN");
        }

        if (file != null && !file.isEmpty()) {
            try {
                java.nio.file.Path rootDir = java.nio.file.Paths.get(uploadDir, "exam-recordings").toAbsolutePath().normalize();
                java.nio.file.Files.createDirectories(rootDir);

                String ext = ".webm";
                String originalFilename = file.getOriginalFilename();
                if (originalFilename != null && originalFilename.toLowerCase().endsWith(".mp4")) {
                    ext = ".mp4";
                }
                String fileName = "recording_attempt_" + id + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + ext;
                java.nio.file.Path targetPath = rootDir.resolve(fileName);
                java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                String recordingUrl = "/uploads/exam-recordings/" + fileName;
                attempt.setRecordingUrl(recordingUrl);
                attempt.setProctoringWarnings(warningsCount);
                if (duration != null && duration > 0) {
                    attempt.setRecordingDurationSeconds(duration);
                }
                attemptRepository.save(attempt);

                resultRepo.findByAttemptId(id).ifPresent(r -> {
                    if (warningsCount > 0) {
                        r.setRiskScore(Math.min(100, r.getRiskScore() + (warningsCount * 5)));
                        resultRepo.save(r);
                    }
                });

                com.skillhub.model.ProfessionalExamSecurityEvent event = new com.skillhub.model.ProfessionalExamSecurityEvent();
                event.setAttempt(attempt);
                event.setEventType("PROCTORING_RECORDING_UPLOADED");
                event.setMetadata("Duration: " + (duration != null ? duration : 0) + "s, Warnings: " + warningsCount + ", File: " + fileName);
                event.setEventTimestamp(java.time.LocalDateTime.now());
                securityEventRepo.save(event);

                java.util.Map<String, Object> resp = new java.util.HashMap<>();
                resp.put("status", "SUCCESS");
                resp.put("recordingUrl", recordingUrl);
                return ResponseEntity.ok(resp);
            } catch (Exception e) {
                java.util.Map<String, Object> err = new java.util.HashMap<>();
                err.put("status", "ERROR");
                err.put("message", e.getMessage());
                return ResponseEntity.internalServerError().body(err);
            }
        }
        return ResponseEntity.badRequest().body("File is empty");
    }

    @PostMapping("/{id}/security-events")
    public ResponseEntity<?> logSecurityEvent(
            @PathVariable("id") Long id,
            @RequestBody java.util.Map<String, Object> payload,
            jakarta.servlet.http.HttpServletRequest request,
            Authentication auth) {
        User user = getAuthenticatedUser(auth);
        com.skillhub.model.ProfessionalExamAttempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new com.skillhub.exception.ResourceNotFoundException("Attempt not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && !attempt.getStudent().getId().equals(user.getId())) {
            throw new com.skillhub.exception.BusinessException("FORBIDDEN");
        }

        String eventType = (String) payload.getOrDefault("eventType", "FACE_NOT_DETECTED");
        String metadata = (String) payload.getOrDefault("metadata", "Candidate out of camera frame");

        com.skillhub.model.ProfessionalExamSecurityEvent event = new com.skillhub.model.ProfessionalExamSecurityEvent();
        event.setAttempt(attempt);
        event.setEventType(eventType);
        event.setMetadata(metadata);
        event.setEventTimestamp(java.time.LocalDateTime.now());
        event.setIpAddress(request.getRemoteAddr());
        event.setUserAgent(request.getHeader("User-Agent"));
        securityEventRepo.save(event);

        if ("FACE_NOT_DETECTED".equalsIgnoreCase(eventType) 
                || "FACE_ABSENT".equalsIgnoreCase(eventType)
                || "UNAUTHORIZED_FACE_DETECTED".equalsIgnoreCase(eventType)
                || "FACE_MISMATCH".equalsIgnoreCase(eventType)
                || "MULTIPLE_FACES_DETECTED".equalsIgnoreCase(eventType)
                || "EXAM_AUTO_TERMINATED".equalsIgnoreCase(eventType)) {
            int currentWarn = attempt.getProctoringWarnings() != null ? attempt.getProctoringWarnings() : 0;
            attempt.setProctoringWarnings(currentWarn + 1);
            if ("EXAM_AUTO_TERMINATED".equalsIgnoreCase(eventType) || (currentWarn + 1) >= 3) {
                attempt.setRiskScore(100);
            } else {
                attempt.setRiskScore(Math.min(100, attempt.getRiskScore() + 20));
            }
            attemptRepository.save(attempt);
        }

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("status", "LOGGED");
        resp.put("warningsCount", attempt.getProctoringWarnings());
        return ResponseEntity.ok(resp);
    }
}
