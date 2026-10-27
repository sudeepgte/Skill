package com.skillhub.service;

import com.skillhub.model.Notification;
import com.skillhub.model.User;
import com.skillhub.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void createAndSend(User recipient, String title, String message, String type) {
        // 1. Save to Database
        Notification notification = new Notification(recipient, title, message, type);
        notification = notificationRepository.save(notification);

        // 2. Broadcast via WebSocket
        // Destination: /user/queue/notifications
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", notification.getId());
            payload.put("title", notification.getTitle());
            payload.put("message", notification.getMessage());
            payload.put("type", notification.getType());
            payload.put("createdAt", notification.getCreatedAt().toString());
            payload.put("isRead", notification.isRead());

            messagingTemplate.convertAndSendToUser(recipient.getEmail(), "/queue/notifications", payload);
        } catch (Exception e) {
            // Silently handle if user is not online or websocket fails
        }
    }
    
    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    public List<Notification> getUserNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        for (Notification n : unread) {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        }
    }

    public Optional<Notification> getActiveBroadcastForUser(User user) {
        return notificationRepository.findFirstByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(user, "BROADCAST");
    }
}
