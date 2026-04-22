package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.service.NotificationService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @GetMapping("/notifications")
    public String viewNotifications(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userService.findByEmail(userDetails.getUsername());
            model.addAttribute("notifications", notificationService.getUserNotifications(user));
            // Mark all as read when viewing page
            notificationService.markAllAsRead(user);
        }
        return "common/notifications";
    }

    @PostMapping("/notifications/mark-read")
    public String markRead(@RequestParam("id") Long id,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Referer", required = false) String referer) {
        notificationService.markAsRead(id);
        return "redirect:" + (referer != null ? referer : "/notifications");
    }
}
