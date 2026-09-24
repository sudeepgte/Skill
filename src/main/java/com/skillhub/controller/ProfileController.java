package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.service.UserService;
import com.skillhub.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.repository.EnrollmentRepository enrollmentRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.skillhub.repository.CategoryRepository categoryRepository;

    @Autowired
    private com.skillhub.repository.LectureProgressRepository lectureProgressRepository;

    @Autowired
    private com.skillhub.repository.CreationRepository creationRepository;

    @Autowired
    private com.skillhub.repository.NoteRepository noteRepository;

    @Autowired
    private com.skillhub.repository.PaymentRepository paymentRepository;

    @GetMapping("/profile")
    public String showProfile(@RequestParam(value = "tab", required = false, defaultValue = "documents") String tab,
            Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        User user = userService.findByEmail(principal.getName());
        // No redirect for courses tab to allow internal view

        model.addAttribute("user", user);

        // Fetch enrollments
        // Fetch enrollments
        List<com.skillhub.model.Enrollment> enrollments = enrollmentRepository.findByStudent(user);
        for (com.skillhub.model.Enrollment enrollment : enrollments) {
            long total = lectureProgressRepository.countTotalLecturesByCourse(enrollment.getCourse().getId());
            if (total > 0) {
                long completed = lectureProgressRepository.countCompletedLecturesByCourse(enrollment.getStudent().getId(), enrollment.getCourse().getId());
                double percentage = Math.min(100.0, (double) completed / total * 100.0);
                enrollment.setProgressPercentage(percentage);
            }
        }
        model.addAttribute("enrollments", enrollments);

        // --- Calculate Category Expertise (Skill Radar) ---
        List<com.skillhub.model.Category> allCategories = categoryRepository.findAll();
        java.util.Map<String, Double> expertiseMap = new java.util.HashMap<>();

        for (com.skillhub.model.Category cat : allCategories) {
            // Count completed lectures in this category for this user
            long completed = lectureProgressRepository.countCompletedLecturesByCategory(user.getId(), cat.getId());
            long total = cat.getCourses().stream()
                    .flatMap(c -> c.getSections().stream())
                    .flatMap(s -> s.getLectures().stream())
                    .count();

            double percent = (total > 0) ? (completed * 100.0 / total) : 0.0;
            // Cap at 100
            if (percent > 100)
                percent = 100.0;
            expertiseMap.put(cat.getName(), percent);
        }
        model.addAttribute("expertiseMap", expertiseMap);

        // --- Fetch Creations (Portfolio) ---
        List<com.skillhub.model.Creation> creations = creationRepository.findByUserOrderByCreatedAtDesc(user);
        model.addAttribute("creations", creations);

        // Fetch certificates
        List<com.skillhub.model.Enrollment> certificates = enrollments.stream()
                .filter(com.skillhub.model.Enrollment::isExamPassed)
                .collect(Collectors.toList());
        model.addAttribute("certificates", certificates);
        
        // Fetch Notes
        List<com.skillhub.model.Note> notes = noteRepository.findByUserOrderByUpdatedAtDesc(user);
        model.addAttribute("notes", notes);

        // Fetch successful payments (receipts)
        List<com.skillhub.model.Payment> payments = paymentRepository.findByUserAndStatusOrderByPaymentDateDesc(user, com.skillhub.model.PaymentStatus.SUCCESS);
        model.addAttribute("payments", payments);

        model.addAttribute("activeTab", tab);

        return "profile";
    }

    @PostMapping("/profile/creations/add")
    public String addCreation(@RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("image") MultipartFile image,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null)
            return "redirect:/login";
        User user = userService.findByEmail(principal.getName());

        if (image != null && !image.isEmpty()) {
            // Server-side validation for alphabets, numbers, and common punctuation
            String projectRegex = "^[A-Za-z0-9 .!?',()-]+$";
            if (!title.matches(projectRegex)) {
                redirectAttributes.addFlashAttribute("error", "Title can only contain alphabets, numbers, and common punctuation.");
                return "redirect:/profile?tab=creations";
            }
            if (description != null && !description.isEmpty() && !description.matches(projectRegex)) {
                redirectAttributes.addFlashAttribute("error", "Description can only contain alphabets, numbers, and common punctuation.");
                return "redirect:/profile?tab=creations";
            }

            try {
                String imagePath = fileStorageService.store(image);
                com.skillhub.model.Creation creation = new com.skillhub.model.Creation();
                creation.setTitle(title);
                creation.setDescription(description);
                creation.setImageUrl(imagePath);
                creation.setUser(user);
                creationRepository.save(creation);
                redirectAttributes.addFlashAttribute("success", "Creation added to portfolio!");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            }
        }
        return "redirect:/profile?tab=creations";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "skills", required = false) String skills,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "headline", required = false) String headline,
            @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            return "redirect:/login";
        }

        // Validate Phone Number
        if (phone != null && !phone.isEmpty()) {
            if (!phone.matches("^[0-9]{10}$")) {
                redirectAttributes.addFlashAttribute("error", "Phone number must be exactly 10 digits.");
                return "redirect:/profile?tab=profile";
            }
        }

        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setSkills(skills);
        user.setBio(bio);
        user.setHeadline(headline);

        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                logger.info("Uploading profile picture for user: {}", user.getEmail());
                String imagePath = fileStorageService.store(profilePicture);
                user.setProfileImageUrl(imagePath);
                logger.info("Profile picture updated: {}", imagePath);
            } catch (Exception e) {
                logger.error("Upload error for user {}: {}", user.getEmail(), e.getMessage());
                redirectAttributes.addFlashAttribute("error", "Failed to upload image: " + e.getMessage());
            }
        }

        userService.updateUser(user);

        if (!principal.getName().equals(email)) {
            redirectAttributes.addFlashAttribute("success", "Profile updated. Please log in with your new email.");
            return "redirect:/logout";
        }

        redirectAttributes.addFlashAttribute("success", "Profile updated successfully.");
        return "redirect:/profile?tab=profile";
    }

    @PostMapping("/profile/upload-photo")
    public String uploadPhotoOnly(@RequestParam("profilePicture") MultipartFile profilePicture,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null)
            return "redirect:/login";

        User user = userService.findByEmail(principal.getName());
        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                String imagePath = fileStorageService.store(profilePicture);
                user.setProfileImageUrl(imagePath);
                userService.updateUser(user);
                redirectAttributes.addFlashAttribute("success", "Photo updated!");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Failed to upload: " + e.getMessage());
            }
        }
        return "redirect:/profile?tab=profile";
    }

    @GetMapping("/change-password")
    public String showChangePassword(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";
        model.addAttribute("user", userService.findByEmail(principal.getName()));
        return "change_password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null)
            return "redirect:/login";

        User user = userService.findByEmail(principal.getName());

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Incorrect current password.");
            return "redirect:/change-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/change-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateUser(user);

        redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
        return "redirect:/profile";
    }

    @PostMapping("/profile/upload-documents")
    public String uploadDocuments(
            @RequestParam(value = "resume", required = false) MultipartFile resume,
            @RequestParam(value = "tenthCert", required = false) MultipartFile tenthCert,
            @RequestParam(value = "puCert", required = false) MultipartFile puCert,
            @RequestParam(value = "ugCert", required = false) MultipartFile ugCert,
            @RequestParam(value = "pgCert", required = false) MultipartFile pgCert,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null)
            return "redirect:/login";

        User user = userService.findByEmail(principal.getName());

        try {
            boolean updated = false;
            if (resume != null && !resume.isEmpty()) {
                user.setResumeUrl(fileStorageService.store(resume));
                updated = true;
            }
            if (tenthCert != null && !tenthCert.isEmpty()) {
                user.setTenthCertUrl(fileStorageService.store(tenthCert));
                updated = true;
            }
            if (puCert != null && !puCert.isEmpty()) {
                user.setPuCertUrl(fileStorageService.store(puCert));
                updated = true;
            }
            if (ugCert != null && !ugCert.isEmpty()) {
                user.setUgCertUrl(fileStorageService.store(ugCert));
                updated = true;
            }
            if (pgCert != null && !pgCert.isEmpty()) {
                user.setPgCertUrl(fileStorageService.store(pgCert));
                updated = true;
            }

            if (updated) {
                userService.updateUser(user);
                redirectAttributes.addFlashAttribute("success", "Documents updated successfully!");
            } else {
                redirectAttributes.addFlashAttribute("info", "No new documents selected.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Document upload failed: " + e.getMessage());
        }

        return "redirect:/profile?tab=documents";
    }

    @PostMapping("/profile/delete-document")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> deleteDocument(@RequestParam("type") String type, Principal principal) {
        if (principal == null) return org.springframework.http.ResponseEntity.status(401).build();

        User user = userService.findByEmail(principal.getName());
        boolean deleted = false;
        java.util.Map<String, Object> response = new java.util.HashMap<>();

        switch (type) {
            case "resume": fileStorageService.delete(user.getResumeUrl()); user.setResumeUrl(null); deleted = true; break;
            case "tenthCert": fileStorageService.delete(user.getTenthCertUrl()); user.setTenthCertUrl(null); deleted = true; break;
            case "puCert": fileStorageService.delete(user.getPuCertUrl()); user.setPuCertUrl(null); deleted = true; break;
            case "ugCert": fileStorageService.delete(user.getUgCertUrl()); user.setUgCertUrl(null); deleted = true; break;
            case "pgCert": fileStorageService.delete(user.getPgCertUrl()); user.setPgCertUrl(null); deleted = true; break;
        }

        if (deleted) {
            userService.updateUser(user);
            response.put("success", true);
            response.put("message", "Document deleted successfully!");
            return org.springframework.http.ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Unknown type: " + type);
            return org.springframework.http.ResponseEntity.badRequest().body(response);
        }
    }
}
