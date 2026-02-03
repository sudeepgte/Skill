package com.skillhub.model;

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    @ManyToOne
    @JoinColumn(name = "section_id")
    private Section section;

    private String instructor;
    private Double price;
    private String imageUrl;

    private String subtitle;

    @Column(length = 5000)
    private String longDescription;

    private String language;
    private String level;
    private String contentLength;
    private String lastUpdated;

    @Column(length = 2000)
    private String learningObjectives; // Newline separated

    @Column(length = 2000)
    private String requirements; // Newline separated

    public Course() {
    }

    public Course(String title, String subtitle, String description, String longDescription, Section section,
            String instructor, Double price, String imageUrl, String language, String level,
            String contentLength, String lastUpdated, String learningObjectives, String requirements) {
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
        this.longDescription = longDescription;
        this.section = section;
        this.instructor = instructor;
        this.price = price;
        this.imageUrl = imageUrl;
        this.language = language;
        this.level = level;
        this.contentLength = contentLength;
        this.lastUpdated = lastUpdated;
        this.learningObjectives = learningObjectives;
        this.requirements = requirements;
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

    public String getLongDescription() {
        return longDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
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

    public String getContentLength() {
        return contentLength;
    }

    public void setContentLength(String contentLength) {
        this.contentLength = contentLength;
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
}
