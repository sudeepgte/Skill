package com.skillhub.model;
import jakarta.persistence.*;

@Entity
@Table(name = "exam_skill_taxonomies")
public class ExamSkillTaxonomy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String category;
    private String skillName;
    private String description;
    
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; } public void setCategory(String category) { this.category = category; }
    public String getSkillName() { return skillName; } public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
