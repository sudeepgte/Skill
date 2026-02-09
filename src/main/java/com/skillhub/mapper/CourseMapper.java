package com.skillhub.mapper;

import com.skillhub.dto.CourseDTO;
import com.skillhub.dto.LectureDTO;
import com.skillhub.dto.SectionDTO;
import com.skillhub.model.Course;
import com.skillhub.model.CourseSection;
import com.skillhub.model.Lecture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class CourseMapper {

    @Autowired
    private UserMapper userMapper;

    public CourseDTO toDTO(Course course) {
        if (course == null)
            return null;

        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setSubtitle(course.getSubtitle());
        dto.setDescription(course.getDescription());
        if (course.getCategory() != null) {
            dto.setCategoryName(course.getCategory().getName());
        }
        dto.setInstructor(userMapper.toDTO(course.getInstructor()));
        dto.setPrice(course.getPrice());
        dto.setImageUrl(course.getImageUrl());
        dto.setLanguage(course.getLanguage());
        dto.setLevel(course.getLevel());
        dto.setPublished(course.isPublished());
        dto.setLastUpdated(course.getLastUpdated());
        dto.setLearningObjectives(course.getLearningObjectives());
        dto.setRequirements(course.getRequirements());
        dto.setLongDescription(course.getLongDescription());
        dto.setLongDescription(course.getLongDescription());
        dto.setContentLength(course.getContentLength());
        dto.setVideoUrl(course.getVideoUrl());

        // Map transient fields if computed or set
        dto.setAverageRating(course.getAverageRating());
        dto.setReviewCount(course.getReviewCount());

        if (course.getSections() != null) {
            dto.setSections(course.getSections().stream()
                    .map(this::toSectionDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private SectionDTO toSectionDTO(CourseSection section) {
        SectionDTO dto = new SectionDTO();
        dto.setId(section.getId());
        dto.setTitle(section.getTitle());
        dto.setPosition(section.getPosition());

        if (section.getLectures() != null) {
            dto.setLectures(section.getLectures().stream()
                    .map(this::toLectureDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private LectureDTO toLectureDTO(Lecture lecture) {
        LectureDTO dto = new LectureDTO();
        dto.setId(lecture.getId());
        dto.setTitle(lecture.getTitle());
        dto.setVideoUrl(lecture.getVideoUrl());
        dto.setDurationSeconds(lecture.getDurationSeconds());
        dto.setPreview(lecture.isPreview());
        dto.setPosition(lecture.getPosition());
        return dto;
    }
}
