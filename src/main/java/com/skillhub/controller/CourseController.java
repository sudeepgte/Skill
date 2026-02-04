package com.skillhub.controller;

import com.skillhub.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.skillhub.dto.CourseDTO;
import com.skillhub.mapper.CourseMapper;
import org.springframework.web.bind.annotation.RequestParam;
import java.security.Principal;

@Controller
@RequestMapping("/course")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    @GetMapping("/{id}")
    public String getCourseDetails(@PathVariable Long id, Model model) {
        // Fetch entity (you might want a service method that returns DTO directly, but
        // mapping here is fine for now)
        com.skillhub.model.Course course = courseService.getCourseById(id);

        // Map to DTO
        CourseDTO courseDTO = courseMapper.toDTO(course);

        model.addAttribute("course", courseDTO);
        return "course-details"; // Ensure this template exists or create it
    }

    @GetMapping("/{id}/learn")
    public String getLearningRoom(@PathVariable Long id,
            @RequestParam(required = false) Long lectureId,
            Model model,
            Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        // Logic to verify enrollment would go here (or in service)
        // For now assuming enrolled or public

        com.skillhub.model.Course course = courseService.getCourseById(id);
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Use provided lectureId or find first/last played
        // This logic should ideally be in a service
        Long targetLectureId = lectureId;
        if (targetLectureId == null) {
            // Default to first lecture of first section
            if (!course.getSections().isEmpty() && !course.getSections().get(0).getLectures().isEmpty()) {
                targetLectureId = course.getSections().get(0).getLectures().get(0).getId();
            }
        }

        // Pass current lesson for titles/video
        // We'll need to find the specific lesson object in the DTO or Entity
        // Simplification: Iterate to find it
        com.skillhub.dto.LectureDTO currentLesson = null;
        if (targetLectureId != null) {
            for (com.skillhub.dto.SectionDTO section : courseDTO.getSections()) {
                for (com.skillhub.dto.LectureDTO lecture : section.getLectures()) {
                    if (lecture.getId().equals(targetLectureId)) {
                        currentLesson = lecture;
                        break;
                    }
                }
            }
        }

        model.addAttribute("course", courseDTO);
        model.addAttribute("currentLesson", currentLesson);

        return "course/learning_room";
    }
}
