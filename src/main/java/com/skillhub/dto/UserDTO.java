package com.skillhub.dto;

public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String headline;
    private String bio;
    private String profileImageUrl;

    public UserDTO() {
    }

    public UserDTO(Long id, String name, String email, String role, String headline, String bio,
            String profileImageUrl) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.headline = headline;
        this.bio = bio;
        this.profileImageUrl = profileImageUrl;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
