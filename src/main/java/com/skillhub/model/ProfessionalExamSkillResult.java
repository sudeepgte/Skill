package com.skillhub.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "professional_exam_skill_results", indexes = {
    @Index(name = "idx_prof_skill_res", columnList = "result_id")
})
public class ProfessionalExamSkillResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private ProfessionalExamResult result;

    @Column(nullable = false)
    private String skill;

    private int totalMarks;
    private BigDecimal obtainedMarks;
    private double percentage;

    public ProfessionalExamSkillResult() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProfessionalExamResult getResult() { return result; }
    public void setResult(ProfessionalExamResult result) { this.result = result; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public BigDecimal getObtainedMarks() { return obtainedMarks; }
    public void setObtainedMarks(BigDecimal obtainedMarks) { this.obtainedMarks = obtainedMarks; }
    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }
}
