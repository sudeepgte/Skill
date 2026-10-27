package com.skillhub.dto;

import java.time.LocalDateTime;

public class CourseUsageDto {
    private int usedCount;
    private int limitCount;
    private int remainingSlots;
    private boolean limitReached;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String planName;
    private String status;

    public CourseUsageDto() {
    }

    public CourseUsageDto(int usedCount, int limitCount, int remainingSlots, boolean limitReached, LocalDateTime periodStart, LocalDateTime periodEnd, String planName, String status) {
        this.usedCount = usedCount;
        this.limitCount = limitCount;
        this.remainingSlots = remainingSlots;
        this.limitReached = limitReached;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.planName = planName;
        this.status = status;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(int limitCount) {
        this.limitCount = limitCount;
    }

    public int getRemainingSlots() {
        return remainingSlots;
    }

    public void setRemainingSlots(int remainingSlots) {
        this.remainingSlots = remainingSlots;
    }

    public boolean isLimitReached() {
        return limitReached;
    }

    public void setLimitReached(boolean limitReached) {
        this.limitReached = limitReached;
    }

    public LocalDateTime getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDateTime periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDateTime getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
