package com.skillhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ContactController {

    @RequestMapping(value = "/submit-contact-form", method = {RequestMethod.POST})
    public String handleContactSubmit(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMessage", "Your message has been sent successfully!");
        return "redirect:/contact";
    }
}
