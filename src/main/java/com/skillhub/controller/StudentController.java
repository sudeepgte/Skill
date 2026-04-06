package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.skillhub.repository.WishlistRepository wishlistRepository;

    @GetMapping("/dashboard")
    public String dashboard(Principal principal) {
        return "redirect:/profile";
    }

    @GetMapping("/wishlist")
    public String wishlist(Model model, Principal principal) {
        String email = principal.getName();
        User student = userService.findByEmail(email);
        List<com.skillhub.model.Wishlist> wishlistItems = wishlistRepository.findByUser(student);
        model.addAttribute("wishlistItems", wishlistItems);
        return "student/wishlist";
    }
}
