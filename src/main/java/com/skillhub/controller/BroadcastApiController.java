package com.skillhub.controller;

import com.skillhub.model.Notification;
import com.skillhub.model.User;
import com.skillhub.repository.NotificationRepository;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/broadcast")
public class BroadcastApiController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserService userService;

    @GetMapping("/active")
    public ResponseEntity<?> getActiveBroadcast(Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("active", false);
            return ResponseEntity.ok(response);
        }

        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            response.put("active", false);
            return ResponseEntity.ok(response);
        }

        Optional<Notification> opt = notificationRepository.findFirstByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(user, "BROADCAST");
        if (opt.isPresent()) {
            Notification n = opt.get();
            response.put("active", true);
            response.put("id", n.getId());
            response.put("title", n.getTitle());
            response.put("message", n.getMessage());
            response.put("type", n.getType());
            response.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            return ResponseEntity.ok(response);
        }

        response.put("active", false);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/dismiss")
    public ResponseEntity<?> dismissBroadcast(@RequestParam(value = "id", required = false) Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        User user = userService.findByEmail(principal.getName());
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }

        if (id != null) {
            Optional<Notification> opt = notificationRepository.findById(id);
            if (opt.isPresent()) {
                Notification n = opt.get();
                if (n.getUser() != null && n.getUser().getId().equals(user.getId())) {
                    n.setRead(true);
                    notificationRepository.save(n);
                }
            }
        }

        // Also ensure all unread broadcasts for this user are marked read so they do not repeat on reload or next login
        List<Notification> unread = notificationRepository.findByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(user, "BROADCAST");
        for (Notification n : unread) {
            n.setRead(true);
            notificationRepository.save(n);
        }

        return ResponseEntity.ok(Map.of("success", true));
    }
}
