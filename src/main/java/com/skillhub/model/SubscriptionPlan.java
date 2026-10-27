package com.skillhub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // Free, Starter, Growth, Professional, Enterprise

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double monthlyPrice = 0.0; // Monthly price in INR

    private Integer courseLimit; // Number of courses per month, null for custom

    @Column(columnDefinition = "TEXT")
    private String features; // Features/benefits summary

    private boolean isCustom = false;

    private boolean isActive = true;

    private boolean isPopular = false;

    private Integer sortOrder = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SubscriptionPlan() {
    }

    public SubscriptionPlan(String name, String description, Double monthlyPrice, Integer courseLimit, String features, boolean isCustom, boolean isPopular, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.monthlyPrice = monthlyPrice;
        this.courseLimit = courseLimit;
        this.features = features;
        this.isCustom = isCustom;
        this.isPopular = isPopular;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getMonthlyPrice() {
        return monthlyPrice;
    }

    public void setMonthlyPrice(Double monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public Integer getCourseLimit() {
        return courseLimit;
    }

    public void setCourseLimit(Integer courseLimit) {
        this.courseLimit = courseLimit;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isPopular() {
        return isPopular;
    }

    public void setPopular(boolean popular) {
        isPopular = popular;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
