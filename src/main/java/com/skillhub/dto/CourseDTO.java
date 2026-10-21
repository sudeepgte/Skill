package com.skillhub.dto;

import com.skillhub.model.CourseStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourseDTO {
    private Long id;
    private String title;
    private String subtitle;
    private String description;
    private String categoryName;
    private Long categoryId;
    private String subCategory;
    private UserDTO instructor;
    private Double price;
    private Double salePrice;
    private String imageUrl;
    private String language;
    private String level;
    private boolean isPublished;
    private CourseStatus status;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String lastUpdated;
    private String learningObjectives;
    private String requirements;
    private String longDescription;
    private String contentLength;
    private Double averageRating;
    private Integer reviewCount;
    private Long enrolledStudentCount;
    private String videoUrl;
    private List<SectionDTO> sections = new ArrayList<>();

    // Course format & teaching method
    private String format;
    private String teachingMethod;
    private String tags;

    // Intended learners
    private String targetAudience;
    private String requiredExperience;
    private String expectedSkills;
    private String ageRange;
    private String accessibilityRequirements;
    private String accessDuration;

    // Category specific dynamic data
    private String categorySpecificData;
    private Map<String, String> categoryDataMap = new HashMap<>();

    // Downloadable learning resources
    private List<CourseResourceDTO> resources = new ArrayList<>();

    public CourseDTO() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public UserDTO getInstructor() {
        return instructor;
    }

    public void setInstructor(UserDTO instructor) {
        this.instructor = instructor;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(Double salePrice) {
        this.salePrice = salePrice;
    }

    public String getImageUrl() {
        if (imageUrl == null || imageUrl.trim().isEmpty() || imageUrl.contains("udemycdn.com")) {
            return "/images/course-placeholder.jpg";
        }
        String cleanUrl = imageUrl.trim().replace('\\', '/');
        if (cleanUrl.startsWith("uploads/")) {
            cleanUrl = "/" + cleanUrl;
        }
        return cleanUrl;
    }

    public void setImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty() || imageUrl.contains("udemycdn.com")) {
            this.imageUrl = "/images/course-placeholder.jpg";
        } else {
            String cleanUrl = imageUrl.trim().replace('\\', '/');
            if (cleanUrl.startsWith("uploads/")) {
                cleanUrl = "/" + cleanUrl;
            }
            this.imageUrl = cleanUrl;
        }
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = "DM Sansmediate".equalsIgnoreCase(level) ? "Intermediate" : level;
    }

    public boolean isPublished() {
        return isPublished;
    }

    public void setPublished(boolean published) {
        isPublished = published;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getLearningObjectives() {
        return learningObjectives;
    }

    public void setLearningObjectives(String learningObjectives) {
        this.learningObjectives = learningObjectives;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public String getContentLength() {
        return contentLength;
    }

    public void setContentLength(String contentLength) {
        this.contentLength = contentLength;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Long getEnrolledStudentCount() {
        return enrolledStudentCount;
    }

    public void setEnrolledStudentCount(Long enrolledStudentCount) {
        this.enrolledStudentCount = enrolledStudentCount;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public List<SectionDTO> getSections() {
        return sections;
    }

    public void setSections(List<SectionDTO> sections) {
        this.sections = sections;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTeachingMethod() {
        return teachingMethod;
    }

    public void setTeachingMethod(String teachingMethod) {
        this.teachingMethod = teachingMethod;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public String getRequiredExperience() {
        return requiredExperience;
    }

    public void setRequiredExperience(String requiredExperience) {
        this.requiredExperience = requiredExperience;
    }

    public String getExpectedSkills() {
        return expectedSkills;
    }

    public void setExpectedSkills(String expectedSkills) {
        this.expectedSkills = expectedSkills;
    }

    public String getAgeRange() {
        return ageRange;
    }

    public void setAgeRange(String ageRange) {
        this.ageRange = ageRange;
    }

    public String getAccessibilityRequirements() {
        return accessibilityRequirements;
    }

    public void setAccessibilityRequirements(String accessibilityRequirements) {
        this.accessibilityRequirements = accessibilityRequirements;
    }

    public String getAccessDuration() {
        return accessDuration;
    }

    public void setAccessDuration(String accessDuration) {
        this.accessDuration = accessDuration;
    }

    public String getCategorySpecificData() {
        return categorySpecificData;
    }

    public void setCategorySpecificData(String categorySpecificData) {
        this.categorySpecificData = categorySpecificData;
    }

    public Map<String, String> getCategoryDataMap() {
        return categoryDataMap;
    }

    public void setCategoryDataMap(Map<String, String> categoryDataMap) {
        this.categoryDataMap = categoryDataMap;
    }

    public List<CourseResourceDTO> getResources() {
        return resources;
    }

    public void setResources(List<CourseResourceDTO> resources) {
        this.resources = resources;
    }
}
