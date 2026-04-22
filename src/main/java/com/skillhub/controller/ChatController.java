package com.skillhub.controller;

import com.skillhub.model.ChatMessage;
import com.skillhub.model.User;
import com.skillhub.repository.MessageRepository;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private com.skillhub.service.PresenceService presenceService;

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.service.FileStorageService fileStorageService;

    @Autowired
    private com.skillhub.service.NotificationService notificationService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload java.util.Map<String, String> payload, java.security.Principal principal) {
        String senderEmail = principal.getName();
        String recipientEmail = payload.get("recipientEmail");
        String content = payload.get("content");
        
        // File fields
        String fileUrl = payload.get("fileUrl");
        String fileType = payload.get("fileType");
        String fileName = payload.get("fileName");

        User sender = userService.findByEmail(senderEmail);
        User recipient = userService.findByEmail(recipientEmail);

        if (sender != null && recipient != null) {
            com.skillhub.model.ChatMessage message = new com.skillhub.model.ChatMessage(sender, recipient, content);
            
            if (fileUrl != null && !fileUrl.isEmpty()) {
                message.setFileUrl(fileUrl);
                message.setFileType(fileType);
                message.setFileName(fileName);
            }
            
            messageRepository.save(message);

            // Convert to DTO for broadcasting
            com.skillhub.dto.MessageDTO dto = convertToDTO(message);

            // Send to recipient's private queue
            messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/messages", dto);
            // Echo back to sender for synchronization across tabs
            messagingTemplate.convertAndSendToUser(senderEmail, "/queue/messages", dto);

            // Trigger Notification
            String notificationMsg = (fileUrl != null) ? "Sent an attachment" : content;
            if (notificationMsg != null && notificationMsg.length() > 50) {
                notificationMsg = notificationMsg.substring(0, 47) + "...";
            }
            notificationService.createAndSend(
                recipient, 
                "New Message from " + sender.getName(), 
                notificationMsg, 
                "CHAT"
            );
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/chat/upload")
    @ResponseBody
    public java.util.Map<String, String> uploadFile(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String url = fileStorageService.store(file);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("url", url);
        response.put("type", file.getContentType());
        response.put("name", file.getOriginalFilename());
        return response;
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload java.util.Map<String, Object> payload, java.security.Principal principal) {
        String recipientEmail = (String) payload.get("recipientEmail");
        java.util.Map<String, Object> typingStatus = new java.util.HashMap<>();
        typingStatus.put("senderEmail", principal.getName());
        typingStatus.put("isTyping", payload.get("isTyping"));
        
        messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/typing", typingStatus);
    }

    @GetMapping("/api/chat/history")
    @ResponseBody
    public List<com.skillhub.dto.MessageDTO> getChatHistory(@RequestParam String otherUserEmail, java.security.Principal principal) {
        String myEmail = principal.getName();
        User currentUser = userService.findByEmail(myEmail);
        User otherUser = userService.findByEmail(otherUserEmail);
        
        if (currentUser != null && otherUser != null) {
            return messageRepository.findConversation(currentUser, otherUser).stream()
                    .map(this::convertToDTO)
                    .collect(java.util.stream.Collectors.toList());
        }
        return List.of();
    }

    private com.skillhub.dto.MessageDTO convertToDTO(com.skillhub.model.ChatMessage msg) {
        String time = msg.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
        com.skillhub.dto.MessageDTO dto = new com.skillhub.dto.MessageDTO(
            msg.getSender().getEmail(),
            msg.getSender().getName(),
            msg.getContent(),
            time
        );
        dto.setFileUrl(msg.getFileUrl());
        dto.setFileType(msg.getFileType());
        dto.setFileName(msg.getFileName());
        return dto;
    }
    
    @GetMapping("/api/chat/contacts")
    @ResponseBody
    public List<com.skillhub.dto.UserDTO> getContacts(java.security.Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        if (currentUser == null) return List.of();
        
        List<User> recipients;
        if ("INSTRUCTOR".equals(currentUser.getRole())) {
            recipients = userService.getAllUsers().stream()
                    .filter(u -> !"INSTRUCTOR".equals(u.getRole()) && !"ADMIN".equals(u.getRole()))
                    .toList();
        } else {
            recipients = userService.getAllUsers().stream()
                    .filter(u -> "INSTRUCTOR".equals(u.getRole()))
                    .toList();
        }

        return recipients.stream().map(u -> new com.skillhub.dto.UserDTO(
            u.getId(),
            u.getName(),
            u.getEmail(),
            u.getRole(),
            u.getHeadline(),
            u.getBio(),
            u.getProfileImageUrl(),
            presenceService.isOnline(u.getEmail())
        )).collect(java.util.stream.Collectors.toList());
    }
}
