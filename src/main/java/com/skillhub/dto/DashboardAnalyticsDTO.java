package com.skillhub.dto;

import java.util.Map;
import java.util.List;

public class DashboardAnalyticsDTO {
    private int currentStreak;
    private int totalXP;
    private int level;
    private double overallCompletion;
    private List<String> strengths;
    private List<String> weaknesses;
    private Map<String, Integer> weeklyActivity; // Date -> Count
    private Map<String, Double> categoryExpertise; // Category -> Percentage

    public DashboardAnalyticsDTO() {}

    // Getters and Setters
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

    public int getTotalXP() { return totalXP; }
    public void setTotalXP(int totalXP) { this.totalXP = totalXP; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getOverallCompletion() { return overallCompletion; }
    public void setOverallCompletion(double overallCompletion) { this.overallCompletion = overallCompletion; }

    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }

    public List<String> getWeaknesses() { return weaknesses; }
    public void setWeaknesses(List<String> weaknesses) { this.weaknesses = weaknesses; }

    public Map<String, Integer> getWeeklyActivity() { return weeklyActivity; }
    public void setWeeklyActivity(Map<String, Integer> weeklyActivity) { this.weeklyActivity = weeklyActivity; }

    public Map<String, Double> getCategoryExpertise() { return categoryExpertise; }
    public void setCategoryExpertise(Map<String, Double> categoryExpertise) { this.categoryExpertise = categoryExpertise; }
}
