package com.skillhub.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;
    
    @Column(name = "course_subtitle", columnDefinition = "LONGTEXT")
    private String subtitle; // Short description
    
    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String subCategory;

    @ManyToOne
    @JoinColumn(name = "instructor_id")
    private User instructor;

    private Double price = 0.0;
    private Double salePrice;
    private String imageUrl; // Thumbnail

    private String language;
    private String level; // Beginner, Intermediate, Advanced, All Levels

    private boolean isPublished = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    private String lastUpdated;

    @Column(name = "exam_updated_at")
    private LocalDateTime examUpdatedAt;

    @Column(length = 1000)
    private String videoUrl; // Promotional or intro video

    @Column(columnDefinition = "TEXT")
    private String learningObjectives; // New line separated

    @Column(columnDefinition = "TEXT")
    private String requirements; // New line separated

    @Column(columnDefinition = "LONGTEXT")
    private String longDescription; // HTML content

    private String contentLength; // e.g. "10h 30m"

    // Course Format & Teaching Method
    private String format; // Video Course, Text Course, Practical / Hands-on Course, Project-based Course, Instructor-led Course
    private String teachingMethod; // Self-paced, Cohort-based, Interactive Practice, Live Sessions
    @Column(columnDefinition = "TEXT")
    private String tags; // Comma-separated tags/skills

    // Intended Learners Details
    @Column(columnDefinition = "TEXT")
    private String targetAudience;

    private String requiredExperience; // Beginner / No Experience, Intermediate, Advanced

    @Column(columnDefinition = "TEXT")
    private String expectedSkills;

    private String ageRange; // All Ages, Kids (6-12), Teens (13-17), Adults (18+)

    @Column(columnDefinition = "TEXT")
    private String accessibilityRequirements;

    private String accessDuration; // Lifetime Access, 1 Year Access, 6 Months Access

    // Structured JSON for Category-Specific Dynamic Fields
    @Column(name = "category_specific_data", columnDefinition = "LONGTEXT")
    private String categorySpecificData;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CourseSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseResource> resources = new ArrayList<>();

    private Long viewCount = 0L;

    @Column(name = "viewed_user_ids", columnDefinition = "TEXT")
    private String viewedUserIds;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Enrollment> enrollments = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Wishlist> wishlist = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamQuestion> examQuestions = new ArrayList<>();

    // Transient fields for UI display (not stored in DB, calculated)
    @Transient
    private Double averageRating = 0.0;

    @Transient
    private Integer reviewCount = 0;

    public Course() {
    }

    // Convenience Methods
    public void addSection(CourseSection section) {
        sections.add(section);
        section.setCourse(this);
    }

    public void addResource(CourseResource resource) {
        resources.add(resource);
        resource.setCourse(this);
    }

    // Category Specific Data Helper Methods
    public Map<String, String> getCategoryDataMap() {
        if (this.categorySpecificData == null || this.categorySpecificData.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(this.categorySpecificData, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public void setCategoryDataMap(Map<String, String> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            this.categorySpecificData = null;
        } else {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.categorySpecificData = mapper.writeValueAsString(dataMap);
            } catch (Exception e) {
                this.categorySpecificData = null;
            }
        }
    }

    public String getCategoryDataValue(String key) {
        Map<String, String> map = getCategoryDataMap();
        return map != null ? map.get(key) : null;
    }

    public void putCategoryData(String key, String value) {
        Map<String, String> map = getCategoryDataMap();
        map.put(key, value);
        setCategoryDataMap(map);
    }

    // Status & Publishing compatibility methods
    public CourseStatus getStatus() {
        if (status == null) {
            return isPublished ? CourseStatus.PUBLISHED : CourseStatus.DRAFT;
        }
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
        if (status == CourseStatus.PUBLISHED) {
            this.isPublished = true;
        } else {
            this.isPublished = false;
        }
    }

    public void setStatus(String status) {
        if (status != null) {
            try {
                this.status = CourseStatus.valueOf(status.trim().toUpperCase());
            } catch (Exception ignored) {
                this.status = CourseStatus.DRAFT;
            }
        }
        if (this.status == CourseStatus.PUBLISHED) {
            this.isPublished = true;
        } else {
            this.isPublished = false;
        }
    }

    public boolean isPublished() {
        if (status != null) {
            return status == CourseStatus.PUBLISHED;
        }
        return isPublished;
    }

    public void setPublished(boolean published) {
        this.isPublished = published;
        if (published) {
            this.status = CourseStatus.PUBLISHED;
        } else if (this.status == CourseStatus.PUBLISHED) {
            this.status = CourseStatus.DRAFT;
        }
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

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public User getInstructor() {
        return instructor;
    }

    public void setInstructor(User instructor) {
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

    public LocalDateTime getExamUpdatedAt() {
        return examUpdatedAt;
    }

    public void setExamUpdatedAt(LocalDateTime examUpdatedAt) {
        this.examUpdatedAt = examUpdatedAt;
    }

    @Transient
    public String getFormattedExamDate() {
        LocalDateTime dt = getEffectiveExamTimestamp();
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    @Transient
    public String getFormattedExamTime() {
        LocalDateTime dt = getEffectiveExamTimestamp();
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
    }

    @Transient
    public String getFormattedExamDateTime() {
        LocalDateTime dt = getEffectiveExamTimestamp();
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
    }

    private LocalDateTime getEffectiveExamTimestamp() {
        if (examUpdatedAt != null) {
            return examUpdatedAt;
        }
        if (submittedAt != null) {
            return submittedAt;
        }
        if (reviewedAt != null) {
            return reviewedAt;
        }
        return LocalDateTime.now();
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
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

    public List<CourseSection> getSections() {
        return sections;
    }

    public void setSections(List<CourseSection> sections) {
        if (this.sections == null) {
            this.sections = new ArrayList<>();
        } else {
            this.sections.clear();
        }
        if (sections != null) {
            this.sections.addAll(sections);
        }
    }

    public List<CourseResource> getResources() {
        return resources;
    }

    public void setResources(List<CourseResource> resources) {
        if (this.resources == null) {
            this.resources = new ArrayList<>();
        } else {
            this.resources.clear();
        }
        if (resources != null) {
            this.resources.addAll(resources);
        }
    }

    public List<Enrollment> getEnrollments() {
        return enrollments;
    }

    public void setEnrollments(List<Enrollment> enrollments) {
        this.enrollments = enrollments;
    }

    public List<Wishlist> getWishlist() {
        return wishlist;
    }

    public void setWishlist(List<Wishlist> wishlist) {
        this.wishlist = wishlist;
    }

    public List<ExamQuestion> getExamQuestions() {
        return examQuestions;
    }

    public void setExamQuestions(List<ExamQuestion> examQuestions) {
        this.examQuestions = examQuestions;
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

    public Long getViewCount() {
        if (viewedUserIds != null && !viewedUserIds.trim().isEmpty()) {
            return (long) getViewedUserIdSet().size();
        }
        return viewCount != null ? viewCount : 0L;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public String getViewedUserIds() {
        return viewedUserIds;
    }

    public void setViewedUserIds(String viewedUserIds) {
        this.viewedUserIds = viewedUserIds;
    }

    public java.util.Set<Long> getViewedUserIdSet() {
        if (this.viewedUserIds == null || this.viewedUserIds.trim().isEmpty()) {
            return new java.util.HashSet<>();
        }
        return java.util.Arrays.stream(this.viewedUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toSet());
    }

    public boolean recordUserView(Long userId) {
        if (userId == null) return false;
        java.util.Set<Long> set = getViewedUserIdSet();
        if (set.add(userId)) {
            this.viewedUserIds = set.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            this.viewCount = (long) set.size();
            return true;
        }
        return false;
    }
}
