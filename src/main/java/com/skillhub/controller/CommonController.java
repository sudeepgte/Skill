package com.skillhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CommonController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.CourseService courseService;





    @GetMapping("/teaching")
    public String teaching() {
        return "common/teaching";
    }

    @GetMapping("/courses/{category}")
    public String browseCategory(@org.springframework.web.bind.annotation.PathVariable String category,
            org.springframework.ui.Model model) {
        model.addAttribute("categoryName", category);
        model.addAttribute("courses", courseService.getCoursesByCategory(category));
        return "common/category_courses";
    }



    @GetMapping("/pricing")
    public String pricing() {
        return "common/pricing";
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.repository.VideoResourceRepository videoResourceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillhub.service.CategoryService categoryService;

    @GetMapping("/library")
    public String videoLibrary(@org.springframework.web.bind.annotation.RequestParam(required = false) Long categoryId,
            org.springframework.ui.Model model) {

        if (categoryId != null) {
            com.skillhub.model.Category cat = categoryService.getCategoryById(categoryId);
            model.addAttribute("activeCategory", cat);
            model.addAttribute("videos", videoResourceRepository.findByCategory(cat));
        } else {
            model.addAttribute("videos", videoResourceRepository.findByOrderByUploadDateDesc());
        }

        model.addAttribute("categories", categoryService.getAllCategories());
        return "common/video_library";
    }

    @GetMapping("/user/edit-profile")
    public String editProfile() {
        return "student/edit-profile";
    }
}
