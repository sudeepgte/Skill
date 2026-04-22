package com.skillhub.service;

import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {
    // Stores emails of online users
    private final Set<String> onlineUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void markOnline(String email) {
        if (email != null) {
            onlineUsers.add(email);
        }
    }

    public void markOffline(String email) {
        if (email != null) {
            onlineUsers.remove(email);
        }
    }

    public boolean isOnline(String email) {
        return email != null && onlineUsers.contains(email);
    }

    public Set<String> getOnlineUsers() {
        return Collections.unmodifiableSet(onlineUsers);
    }
}
