package com.skillhub.controller;

import org.springframework.beans.factory.annotation.Autowired;
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

@Controller
public class ContactController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @RequestMapping(value = "/submit-contact-form", method = {RequestMethod.POST})
    public String handleContactSubmit(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "subject", required = false, defaultValue = "General Inquiry") String subject,
            @RequestParam(value = "phone", required = false, defaultValue = "") String phone,
            @RequestParam("message") String message,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        // Find admin user
        User adminUser = userRepository.findByEmail("admin@skillhub.com");
        if (adminUser != null) {
            Notification notif = new Notification();
            notif.setUser(adminUser);
            notif.setTitle("Contact: " + subject + " from " + name);
            String fullMessage = "Email: " + email + "\nPhone: " + phone + "\n\nMessage:\n" + message;
            notif.setMessage(fullMessage);
            notif.setType("CONTACT");
            notificationRepository.save(notif);
        }

        redirectAttributes.addFlashAttribute("successMessage", "Your message has been sent successfully!");
        
        String referer = request.getHeader("Referer");
        if (referer != null && (referer.endsWith("/") || referer.contains("/?"))) {
            return "redirect:/?success=contact#contact";
        }
        return "redirect:/contact";
    }
}
