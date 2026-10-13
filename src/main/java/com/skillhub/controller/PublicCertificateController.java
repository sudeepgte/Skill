package com.skillhub.controller;

import com.skillhub.dto.PublicCertificateDTO;
import com.skillhub.service.ProfessionalCertificateService;
import com.skillhub.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/certifications/verify")
public class PublicCertificateController {

    @Autowired
    private ProfessionalCertificateService certificateService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @GetMapping("/{id}")
    public String verify(@PathVariable String id, HttpServletRequest request, Model model) {
        String clientIp = getClientIp(request);

        // Enumeration protection: Max 30 requests per minute per IP
        if (!rateLimitingService.allowRequest("cert-verify:" + clientIp, 30, 60)) {
            model.addAttribute("error", "Too many verification requests. Please wait a moment before trying again.");
            return "public/certificate-verify";
        }

        // Clean input
        String cleanId = id != null ? id.trim() : "";
        Optional<PublicCertificateDTO> certOpt = certificateService.verifyPublicCertificate(cleanId);

        if (certOpt.isPresent()) {
            model.addAttribute("certificate", certOpt.get());
        } else {
            // Conceal internal existence details
            model.addAttribute("error", "No valid certificate was found matching the provided identifier.");
        }

        return "public/certificate-verify";
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
