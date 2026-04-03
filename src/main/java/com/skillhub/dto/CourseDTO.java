package com.skillhub.dto;

import java.util.List;

public class CourseDTO {
    private Long id;
    private String title;
    private String subtitle;
    private String description;
    private String categoryName;
    private UserDTO instructor;
    private Double price;
    private String imageUrl;
    private String language;
    private String level;
    private boolean isPublished;
    private String lastUpdated;
    private String learningObjectives;
    private String requirements;
    private String longDescription;
    private String contentLength;
    private Double averageRating;
    private Integer reviewCount;
    private Long enrolledStudentCount;
    private String videoUrl;
    private List<SectionDTO> sections;

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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
        this.level = level;
    }

    public boolean isPublished() {
        return isPublished;
    }

    public void setPublished(boolean published) {
        isPublished = published;
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

    public List<SectionDTO> getSections() {
        return sections;
    }

    public void setSections(List<SectionDTO> sections) {
        this.sections = sections;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getEmbedUrl() {
        if (videoUrl == null)
            return null;
        if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
            String videoId = "";
            if (videoUrl.contains("youtu.be/")) {
                videoId = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
                if (videoId.contains("?")) {
                    videoId = videoId.substring(0, videoId.indexOf("?"));
                }
            } else if (videoUrl.contains("v=")) {
                int index = videoUrl.indexOf("v=");
                videoId = videoUrl.substring(index + 2);
                if (videoId.contains("&")) {
                    videoId = videoId.substring(0, videoId.indexOf("&"));
                }
            } else if (videoUrl.contains("embed/")) {
                videoId = videoUrl.substring(videoUrl.indexOf("embed/") + 6);
                if (videoId.contains("?")) {
                    videoId = videoId.substring(0, videoId.indexOf("?"));
                }
            }

            if (!videoId.isEmpty()) {
                return "https://www.youtube.com/embed/" + videoId;
            }
        }
        return videoUrl;
    }

    public Long getEnrolledStudentCount() {
        return enrolledStudentCount;
    }

    public void setEnrolledStudentCount(Long enrolledStudentCount) {
        this.enrolledStudentCount = enrolledStudentCount;
    }
}
