package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.CourseSection;
import com.skillhub.model.Lecture;
import com.skillhub.service.CourseService;
import com.skillhub.service.FileStorageService;
import com.skillhub.repository.CourseSectionRepository;
import com.skillhub.repository.LectureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/curriculum")
@PreAuthorize("hasRole('ADMIN')")
public class CurriculumController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseSectionRepository sectionRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private FileStorageService fileStorageService;

    // --- Sections ---

    @PostMapping("/sections/add")
    public String addSection(@RequestParam Long courseId, @RequestParam String title,
            RedirectAttributes redirectAttributes) {
        Course course = courseService.getCourseById(courseId);
        if (course != null) {
            CourseSection section = new CourseSection();
            section.setTitle(title);
            section.setCourse(course);
            // Simple position logic: put at end
            section.setPosition(course.getSections().size() + 1);

            sectionRepository.save(section);
            redirectAttributes.addFlashAttribute("success", "Section added successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Course not found.");
        }
        return "redirect:/admin/courses/edit?id=" + courseId;
    }

    @PostMapping("/sections/delete")
    public String deleteSection(@RequestParam Long sectionId, @RequestParam Long courseId,
            RedirectAttributes redirectAttributes) {
        sectionRepository.deleteById(sectionId);
        redirectAttributes.addFlashAttribute("success", "Section deleted.");
        return "redirect:/admin/courses/edit?id=" + courseId;
    }

    // --- Lectures ---

    @PostMapping("/lectures/add")
    public String addLecture(
            @RequestParam Long sectionId,
            @RequestParam Long courseId,
            @RequestParam String title,
            @RequestParam(required = false) MultipartFile videoFile,
            @RequestParam(required = false) String youtubeUrl,
            @RequestParam(defaultValue = "false") boolean isPreview,
            RedirectAttributes redirectAttributes) {

        CourseSection section = sectionRepository.findById(sectionId).orElse(null);
        if (section == null) {
            redirectAttributes.addFlashAttribute("error", "Section not found.");
            return "redirect:/admin/courses/edit?id=" + courseId;
        }

        Lecture lecture = new Lecture();
        lecture.setTitle(title);
        lecture.setSection(section);
        lecture.setPreview(isPreview);
        lecture.setPosition(section.getLectures().size() + 1);

        // Handle Video Source
        if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
            lecture.setVideoUrl(youtubeUrl);
        } else if (videoFile != null && !videoFile.isEmpty()) {
            String path = fileStorageService.store(videoFile);
            lecture.setVideoUrl(path);
        } else {
            // Allow creating lecture without video initially? Or error?
            // For now, let's require one.
            redirectAttributes.addFlashAttribute("error", "Please provide a video file or YouTube URL.");
            return "redirect:/admin/courses/edit?id=" + courseId;
        }

        lectureRepository.save(lecture);
        redirectAttributes.addFlashAttribute("success", "Lecture added successfully!");

        return "redirect:/admin/courses/edit?id=" + courseId;
    }

    @PostMapping("/lectures/delete")
    public String deleteLecture(@RequestParam Long lectureId, @RequestParam Long courseId,
            RedirectAttributes redirectAttributes) {
        lectureRepository.deleteById(lectureId);
        redirectAttributes.addFlashAttribute("success", "Lecture deleted.");
        return "redirect:/admin/courses/edit?id=" + courseId;
    }
}
