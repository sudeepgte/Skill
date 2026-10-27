package com.skillhub.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import com.skillhub.model.Notification;
import com.skillhub.model.User;
import com.skillhub.repository.NotificationRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.EmailService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class ContactController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @RequestMapping(value = "/submit-contact-form", method = {RequestMethod.POST})
    public Object handleContactSubmit(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam("message") String message,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        // 1. Create In-App Notification for Admin (only name, email, and message)
        try {
            User adminUser = userRepository.findByEmail("admin@skillhub.com");
            if (adminUser != null) {
                Notification notif = new Notification();
                notif.setUser(adminUser);
                notif.setTitle("Contact Message from " + name);
                String fullMessage = "Name: " + name + "\nEmail: " + email + "\n\nMessage:\n" + message;
                notif.setMessage(fullMessage);
                notif.setType("CONTACT");
                notificationRepository.save(notif);
            }
        } catch (Exception ex) {
            System.err.println("Could not create admin in-app notification: " + ex.getMessage());
        }

        // 2. Dispatch Confirmation Email to Sender (Only Name, Email, and Message)
        CompletableFuture.runAsync(() -> {
            try {
                String userSubject = "SkillHub — Thank you for contacting us, " + name + "!";
                String userBody = "Hello " + name + ",\n\n"
                        + "Thank you for getting in touch with SkillHub! We have received your message.\n\n"
                        + "--------------------------------------------------\n"
                        + "Name: " + name + "\n"
                        + "Email: " + email + "\n"
                        + "Message:\n" + message + "\n"
                        + "--------------------------------------------------\n\n"
                        + "Our support team will review your message and reply to " + email + " shortly.\n\n"
                        + "Best regards,\n"
                        + "The SkillHub Team\n"
                        + "support@skillhub.com | https://skillhub.com";

                emailService.sendSimpleMessage(email, userSubject, userBody);
                System.out.println("Contact confirmation email dispatched to: " + email);
            } catch (Exception ex) {
                System.err.println("Failed to send contact confirmation email to " + email + ": " + ex.getMessage());
            }

            // 3. Dispatch Notification Email to Admin Team (Only Name, Email, and Message)
            try {
                String adminSubject = "New Contact Message from " + name;
                String adminBody = "A new contact message has been received on SkillHub:\n\n"
                        + "Name: " + name + "\n"
                        + "Email: " + email + "\n\n"
                        + "Message:\n" + message;

                emailService.sendSimpleMessage("nehakore2121@gmail.com", adminSubject, adminBody);
            } catch (Exception ex) {
                System.err.println("Failed to send admin alert email: " + ex.getMessage());
            }
        });

        // 4. Return response: AJAX JSON or standard redirect
        String acceptHeader = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith) || (acceptHeader != null && acceptHeader.contains("application/json"))) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Your message has been sent successfully! A confirmation email has been dispatched to " + email + "."
            ));
        }

        redirectAttributes.addFlashAttribute("successMessage", "Your message has been sent successfully! We've dispatched a confirmation email to " + email + ".");

        String referer = request.getHeader("Referer");
        if (referer != null && (referer.contains("/contact") || referer.endsWith("/contact/"))) {
            return "redirect:/contact?success=true";
        }
        return "redirect:/?success=contact#contact";
    }
}
