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
import org.springframework.ui.Model;

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
    public String addSection(@RequestParam("courseId") Long courseId, @RequestParam("title") String title,
            Model model) {
        Course course = courseService.getCourseById(courseId);
        if (course != null) {
            CourseSection section = new CourseSection();
            section.setTitle(title);
            section.setCourse(course);
            section.setPosition(course.getSections().size() + 1);
            sectionRepository.save(section);

            // Refresh course to get updated list
            courseService.updateCourseStats(courseId);
            course = courseService.getCourseById(courseId);
        }
        model.addAttribute("course", course);
        return "admin/fragments/curriculum :: curriculum_content";
    }

    @PostMapping("/sections/delete")
    public String deleteSection(@RequestParam("sectionId") Long sectionId, @RequestParam("courseId") Long courseId,
            Model model) {
        sectionRepository.deleteById(sectionId);
        courseService.updateCourseStats(courseId);

        Course course = courseService.getCourseById(courseId);
        model.addAttribute("course", course);
        return "admin/fragments/curriculum :: curriculum_content";
    }

    // --- Lectures ---

    @PostMapping("/lectures/add")
    public String addLecture(
            @RequestParam("sectionId") Long sectionId,
            @RequestParam("courseId") Long courseId,
            @RequestParam("title") String title,
            @RequestParam(name = "videoFile", required = false) MultipartFile videoFile,
            @RequestParam(name = "youtubeUrl", required = false) String youtubeUrl,
            @RequestParam(name = "isPreview", defaultValue = "false") boolean isPreview,
            @RequestParam(name = "durationMinutes", required = false, defaultValue = "0") Integer durationMinutes,
            @RequestParam(name = "durationSeconds", required = false, defaultValue = "0") Integer durationSeconds,
            Model model) {

        CourseSection section = sectionRepository.findById(sectionId).orElse(null);
        if (section != null) {
            Lecture lecture = new Lecture();
            lecture.setTitle(title);
            lecture.setSection(section);
            lecture.setPreview(isPreview);
            // Fix position logic (get refreshed list size if possible, or simpler)
            int pos = (section.getLectures() != null) ? section.getLectures().size() + 1 : 1;
            lecture.setPosition(pos);

            int min = (durationMinutes != null) ? durationMinutes : 0;
            int sec = (durationSeconds != null) ? durationSeconds : 0;
            int totalSec = (min * 60) + sec;
            lecture.setDurationSeconds(totalSec);

            if (youtubeUrl != null && !youtubeUrl.trim().isEmpty()) {
                lecture.setVideoUrl(youtubeUrl);
            } else if (videoFile != null && !videoFile.isEmpty()) {
                String path = fileStorageService.store(videoFile);
                lecture.setVideoUrl(path);
            }

            lectureRepository.save(lecture);
            courseService.updateCourseStats(courseId);
        }

        Course course = courseService.getCourseById(courseId);
        model.addAttribute("course", course);
        return "admin/fragments/curriculum :: curriculum_content";
    }

    @PostMapping("/lectures/delete")
    public String deleteLecture(@RequestParam("lectureId") Long lectureId, @RequestParam("courseId") Long courseId,
            Model model) {
        lectureRepository.deleteById(lectureId);
        courseService.updateCourseStats(courseId);

        Course course = courseService.getCourseById(courseId);
        model.addAttribute("course", course);
        return "admin/fragments/curriculum :: curriculum_content";
    }
}
