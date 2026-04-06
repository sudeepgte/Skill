package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String role; // "STUDENT", "INSTRUCTOR", "ADMIN"

    // Instructor Profile fields
    private String headline; // e.g. "Senior Java Developer"

    @Column(length = 2000)
    private String bio;

    private String profileImageUrl;
    private String phone;
    private String skills; // Comma separated skills
    private boolean isBlocked = false;
    private Boolean isApproved = true; // Automatically approved for students/admin, not vendors
    private LocalDateTime lastLoginDate;

    // Social Links & Expertise
    private String instagramUrl;
    private String linkedinUrl;
    private String youtubeUrl;
    private String expertise;

    // Gamification Fields
    private int totalXP = 0;
    private int streakCount = 0;
    private LocalDateTime lastStreakDate;
    private int level = 1;

    @Column(length = 2000)
    private String badges; // Comma-separated badge IDs or names (e.g., "QUIZ_MASTER,FAST_LEARNER")

    // Documents
    private String resumeUrl;
    private String tenthCertUrl;
    private String puCertUrl;
    private String ugCertUrl;
    private String pgCertUrl;

    public User() {
    }

    public User(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }


    public boolean isApproved() {
        return isApproved == null ? true : isApproved; // Existing rows will have null, allow them
    }

    public void setApproved(Boolean approved) {
        this.isApproved = approved;
    }

    // Gamification Helpers
    public void addXP(int amount) {
        this.totalXP += amount;
        // Simple level-up logic: 1000 XP per level
        this.level = (this.totalXP / 1000) + 1;
    }

    public void updateStreak() {
        LocalDateTime now = LocalDateTime.now();
        if (lastStreakDate == null) {
            streakCount = 1;
        } else if (lastStreakDate.toLocalDate().isEqual(now.toLocalDate().minusDays(1))) {
            streakCount++;
        } else if (!lastStreakDate.toLocalDate().isEqual(now.toLocalDate())) {
            streakCount = 1;
        }
        lastStreakDate = now;

    }

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Enrollment> enrollments = new java.util.ArrayList<>();

    public java.util.List<Enrollment> getEnrollments() {
        return enrollments;
    }

    public void setEnrollments(java.util.List<Enrollment> enrollments) {
        this.enrollments = enrollments;
    }

    public LocalDateTime getLastLoginDate() {
        return lastLoginDate;
    }

    public void setLastLoginDate(LocalDateTime lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    // Gamification Getters and Setters
    public int getTotalXP() {
        return totalXP;
    }

    public void setTotalXP(int totalXP) {
        this.totalXP = totalXP;
    }

    public int getStreakCount() {
        return streakCount;
    }

    public void setStreakCount(int streakCount) {
        this.streakCount = streakCount;
    }

    public LocalDateTime getLastStreakDate() {
        return lastStreakDate;
    }

    public void setLastStreakDate(LocalDateTime lastStreakDate) {
        this.lastStreakDate = lastStreakDate;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getBadges() {
        return badges;
    }

    public void setBadges(String badges) {
        this.badges = badges;
    }

    public java.util.List<String> getBadgesList() {
        if (badges == null || badges.isEmpty())
            return java.util.Collections.emptyList();
        return java.util.Arrays.asList(badges.split(","));
    }

    // Document Getters/Setters
    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
    public String getTenthCertUrl() { return tenthCertUrl; }
    public void setTenthCertUrl(String tenthCertUrl) { this.tenthCertUrl = tenthCertUrl; }
    public String getPuCertUrl() { return puCertUrl; }
    public void setPuCertUrl(String puCertUrl) { this.puCertUrl = puCertUrl; }
    public String getUgCertUrl() { return ugCertUrl; }
    public void setUgCertUrl(String ugCertUrl) { this.ugCertUrl = ugCertUrl; }
    public String getPgCertUrl() { return pgCertUrl; }
    public void setPgCertUrl(String pgCertUrl) { this.pgCertUrl = pgCertUrl; }
}