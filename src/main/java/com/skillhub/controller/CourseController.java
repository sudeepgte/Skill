package com.skillhub.controller;

import org.springframework.transaction.annotation.Transactional;
import com.skillhub.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.skillhub.dto.CourseDTO;
import com.skillhub.mapper.CourseMapper;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.util.ArrayList;

@Controller
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private com.skillhub.service.EnrollmentService enrollmentService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private com.skillhub.repository.ReviewRepository reviewRepository;

    @Autowired
    private com.skillhub.repository.LectureProgressRepository lectureProgressRepository;

    @Autowired
    private com.skillhub.repository.NoteRepository noteRepository;

    @Autowired
    private com.skillhub.repository.EnrollmentRepository enrollmentRepository;

    @Autowired
    private com.skillhub.repository.LectureRepository lectureRepository;

    @Autowired
    private com.skillhub.service.UserService userService;

    @Autowired
    private com.skillhub.repository.CourseQuestionRepository questionRepository;

    @GetMapping({ "/course/{id}", "/user/course/{id}" })
    public String getCourseDetails(@PathVariable Long id, Model model, Principal principal) {
        // Fetch entity
        com.skillhub.model.Course course = courseService.getCourseById(id);

        if (course == null) {
            return "redirect:/?error=CourseNotFound";
        }

        // Map to DTO
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Check Enrollment Status
        boolean isEnrolled = false;
        com.skillhub.model.User user = null;
        if (principal != null) {
            String email = principal.getName();
            user = userService.findByEmail(email);
            if (user != null) {
                // Corrected method name: findByStudentAndCourse
                isEnrolled = enrollmentRepository.findByStudentAndCourse(user, course).isPresent();
            }
        }

        // Fetch reviews (latest first)
        List<com.skillhub.model.Review> reviews = reviewRepository
                .findByCourseIdOrderByCreatedAtDesc(id);

        // Calculate real metadata with failsafe
        try {
            Double avgRatingObj = reviewRepository.getAverageRatingByCourseId(id).orElse(0.0);
            double avgRating = (avgRatingObj != null) ? avgRatingObj : 0.0;
            int revCount = (reviews != null) ? reviews.size() : 0;
            long studentCount = enrollmentRepository.countByCourse(course);

            courseDTO.setAverageRating(Math.round(avgRating * 10.0) / 10.0);
            courseDTO.setReviewCount(revCount);
            courseDTO.setEnrolledStudentCount(studentCount);

            if (courseDTO.getLastUpdated() == null || courseDTO.getLastUpdated().isEmpty()) {
                courseDTO.setLastUpdated(LocalDate.now().toString());
            }

            // Calculate content stats
            int totalLectures = 0;
            int videoCount = 0;
            int articleCount = 0;

            if (course.getSections() != null) {
                for (com.skillhub.model.CourseSection s : course.getSections()) {
                    if (s.getLectures() != null) {
                        for (com.skillhub.model.Lecture l : s.getLectures()) {
                            totalLectures++;
                            if (l.getVideoUrl() != null && !l.getVideoUrl().isEmpty()) {
                                videoCount++;
                            } else if (l.getContent() != null && !l.getContent().isEmpty()) {
                                articleCount++;
                            }
                        }
                    }
                }
            }

            // Generate a realistic content length (e.g., 45 mins per video)
            int totalMins = videoCount * 45;
            int hours = totalMins / 60;
            int mins = totalMins % 60;
            String duration = (hours > 0 ? hours + "h " : "") + mins + "m";

            courseDTO.setContentLength(duration);
            model.addAttribute("totalLectures", totalLectures);
            model.addAttribute("videoCount", videoCount);
            model.addAttribute("articleCount", articleCount);
            model.addAttribute("totalSections", course.getSections() != null ? course.getSections().size() : 0);
        } catch (Exception e) {
            System.err.println("Error calculating course metadata: " + e.getMessage());
            // Fallbacks are already in DTO or set to 0/empty
            if (courseDTO.getAverageRating() == null)
                courseDTO.setAverageRating(0.0);
            if (courseDTO.getReviewCount() == null)
                courseDTO.setReviewCount(0);
            if (courseDTO.getEnrolledStudentCount() == null)
                courseDTO.setEnrolledStudentCount(0L);
            if (courseDTO.getLastUpdated() == null)
                courseDTO.setLastUpdated("Recently");
        }

        // Fetch questions
        List<com.skillhub.model.CourseQuestion> questions = questionRepository
                .findByCourseIdOrderByCreatedAtDesc(id);

        model.addAttribute("course", courseDTO);
        model.addAttribute("reviews", reviews);
        model.addAttribute("questions", questions);
        model.addAttribute("isEnrolled", isEnrolled);
        model.addAttribute("user", user);
        model.addAttribute("isLoggedIn", principal != null);
        return "course-details";
    }

    @GetMapping({ "/course/{id}/learn", "/user/course/{id}/learn" })
    public String getLearningRoom(@PathVariable Long id,
            @RequestParam(required = false) Long lectureId,
            Model model,
            Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        com.skillhub.model.Course course = courseService.getCourseById(id);
        CourseDTO courseDTO = courseMapper.toDTO(course);

        // Use provided lectureId or find first/last played
        Long targetLectureId = lectureId;
        if (targetLectureId == null) {
            // Default to first lecture of first section
            if (!course.getSections().isEmpty() && !course.getSections().get(0).getLectures().isEmpty()) {
                targetLectureId = course.getSections().get(0).getLectures().get(0).getId();
            }
        }

        // Pass current lesson for titles/video
        com.skillhub.dto.LectureDTO currentLesson = null;
        if (targetLectureId != null) {
            for (com.skillhub.dto.SectionDTO section : courseDTO.getSections()) {
                for (com.skillhub.dto.LectureDTO lecture : section.getLectures()) {
                    if (lecture.getId().equals(targetLectureId)) {
                        currentLesson = lecture;
                        break;
                    }
                }
                if (currentLesson != null)
                    break;
            }
        }

        // Fallback: If no lesson found but course has a direct video URL, use it as
        // intro
        if (currentLesson == null && courseDTO.getVideoUrl() != null && !courseDTO.getVideoUrl().isEmpty()) {
            currentLesson = new com.skillhub.dto.LectureDTO();
            currentLesson.setTitle("Course Overview");
            currentLesson.setVideoUrl(courseDTO.getVideoUrl());
            currentLesson.setDurationSeconds(0);
            currentLesson.setId(-1L); // Virtual ID
        }

        model.addAttribute("course", courseDTO);
        model.addAttribute("currentLesson", currentLesson);

        // Fetch completed lecture IDs for this user/course
        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        java.util.List<Long> completedLectureIds = lectureProgressRepository.findByUser(user).stream()
                .filter(com.skillhub.model.LectureProgress::isCompleted)
                .map(lp -> lp.getLecture().getId())
                .filter(lectureIdVal -> {
                    // Only include lectures from THIS course
                    return course.getSections().stream()
                            .flatMap(s -> s.getLectures().stream())
                            .anyMatch(l -> l.getId().equals(lectureIdVal));
                })
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("completedLectureIds", completedLectureIds);

        // Fetch Note
        if (currentLesson != null && currentLesson.getId() != null && currentLesson.getId() > 0) {
            com.skillhub.model.Lecture lectureRef = lectureRepository.findById(currentLesson.getId()).orElse(null);
            if (lectureRef != null) {
                noteRepository.findByUserAndLecture(user, lectureRef)
                        .ifPresent(n -> model.addAttribute("lectureNote", n.getContent()));
            }
        } else {
            // General Course Notes
            noteRepository.findByUserAndCourseAndLectureIsNull(user, course)
                    .ifPresent(n -> model.addAttribute("lectureNote", n.getContent()));
        }

        return "course/learning_room";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.repository.WishlistRepository wishlistRepository;

    @org.springframework.web.bind.annotation.PostMapping("/course/wishlist/add")
    public String addToWishlist(@RequestParam Long courseId, Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        String email = principal.getName();
        com.skillhub.model.User user = userService.findByEmail(email);
        com.skillhub.model.Course course = courseService.getCourseById(courseId);

        if (course != null && wishlistRepository.findByUserAndCourse(user, course).isEmpty()) {
            com.skillhub.model.Wishlist wishlist = new com.skillhub.model.Wishlist(user, course);
            wishlistRepository.save(wishlist);
            redirectAttributes.addFlashAttribute("success", "Added to wishlist!");
        }

        return "redirect:/student/wishlist";
    }

    @org.springframework.web.bind.annotation.PostMapping("/course/wishlist/remove")
    public String removeFromWishlist(@RequestParam Long courseId, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        String email = principal.getName();
        com.skillhub.model.User user = userService.findByEmail(email);
        com.skillhub.model.Course course = courseService.getCourseById(courseId);

        wishlistRepository.findByUserAndCourse(user, course).ifPresent(wishlistRepository::delete);

        return "redirect:/student/wishlist";
    }

    @GetMapping("/user/course/{id}/exam")
    public String getExamPage(@PathVariable Long id, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        com.skillhub.model.Course course = courseService.getCourseById(id);

        // check if enrolled
        if (enrollmentRepository.findByStudentAndCourse(user, course).isEmpty()) {
            return "redirect:/user/course/" + id;
        }

        model.addAttribute("course", course);
        return "course/exam";
    }

    @org.springframework.web.bind.annotation.PostMapping("/user/course/{id}/exam/pass")
    public String passExam(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        com.skillhub.model.Course course = courseService.getCourseById(id);

        enrollmentRepository.findByStudentAndCourse(user, course).ifPresent(enrollment -> {
            enrollment.setExamPassed(true);
            enrollment.setCertificateDate(java.time.LocalDateTime.now());
            enrollmentRepository.save(enrollment);
        });

        return "redirect:/user/home?certificateData=earned";
    }

    @GetMapping("/course/enroll/{id}")
    public String enrollCourse(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        enrollmentService.enrollUser(user, id);
        return "redirect:/profile?tab=courses";
    }

    @org.springframework.web.bind.annotation.GetMapping("/user/course/{id}/generate-sample")
    public String generateSample(@PathVariable Long id, Principal principal) {
        if (principal == null)
            return "redirect:/login";
        courseService.generateSampleCurriculum(id);
        return "redirect:/user/course/" + id + "/learn";
    }

    @Transactional
    @org.springframework.web.bind.annotation.PostMapping("/user/lecture/{id}/save-note")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> saveNote(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> payload,
            Principal principal) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        if (principal == null) {
            response.put("success", false);
            return response;
        }

        com.skillhub.model.User user = userService.findByEmail(principal.getName());
        String content = payload.get("content") != null ? payload.get("content").toString() : "";

        Object courseIdObj = payload.get("courseId");
        if (courseIdObj == null) {
            response.put("success", false);
            response.put("message", "Missing courseId");
            return response;
        }

        Long courseId = Long.parseLong(courseIdObj.toString());
        com.skillhub.model.Course course = courseService.getCourseById(courseId);
        if (course == null) {
            response.put("success", false);
            return response;
        }

        try {
            com.skillhub.model.Note note;
            if (id > 0) {
                com.skillhub.model.Lecture lecture = lectureRepository.findById(id).orElse(null);
                if (lecture == null) {
                    response.put("success", false);
                    response.put("message", "Lecture not found");
                    return response;
                }
                note = noteRepository.findByUserAndLecture(user, lecture)
                        .orElse(new com.skillhub.model.Note());
                note.setLecture(lecture);
            } else {
                // Course Overview Note
                note = noteRepository.findByUserAndCourseAndLectureIsNull(user, course)
                        .orElse(new com.skillhub.model.Note());
                note.setLecture(null);
            }

            note.setUser(user);
            note.setCourse(course);
            note.setContent(content);
            noteRepository.save(note);

            response.put("success", true);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Database error: " + e.getMessage());
        }
        return response;
    }
}
