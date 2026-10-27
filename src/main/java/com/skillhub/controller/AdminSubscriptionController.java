package com.skillhub.controller;

import com.skillhub.dto.CourseUsageDto;
import com.skillhub.model.InstructorSubscription;
import com.skillhub.model.SubscriptionPayment;
import com.skillhub.model.SubscriptionPlan;
import com.skillhub.model.SubscriptionStatus;
import com.skillhub.model.User;
import com.skillhub.repository.SubscriptionPaymentRepository;
import com.skillhub.service.SubscriptionService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/subscriptions")
public class AdminSubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserService userService;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    /**
     * Admin Subscriptions Console
     */
    @GetMapping
    public String showAdminSubscriptions(
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "planName", required = false) String planName,
            @RequestParam(value = "search", required = false) String search,
            Model model, Principal principal) {

        SubscriptionStatus status = null;
        if (statusStr != null && !statusStr.trim().isEmpty() && !statusStr.equalsIgnoreCase("ALL")) {
            try {
                status = SubscriptionStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        List<InstructorSubscription> subscriptions = subscriptionService.searchSubscriptions(status, planName, search);
        List<SubscriptionPlan> plans = subscriptionService.getAllPlans();
        List<SubscriptionPayment> recentPayments = paymentRepository.findAllByOrderByCreatedAtDesc();
        Map<String, Object> metrics = subscriptionService.getAdminSubscriptionMetrics();

        // Calculate course usage per subscription for the admin view
        Map<Long, CourseUsageDto> usageMap = new HashMap<>();
        for (InstructorSubscription sub : subscriptions) {
            CourseUsageDto usage = subscriptionService.getCourseUsage(sub.getInstructor());
            usageMap.put(sub.getId(), usage);
        }

        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("plans", plans);
        model.addAttribute("payments", recentPayments);
        model.addAttribute("metrics", metrics);
        model.addAttribute("usageMap", usageMap);
        model.addAttribute("selectedStatus", statusStr != null ? statusStr : "ALL");
        model.addAttribute("selectedPlanName", planName);
        model.addAttribute("searchQuery", search);

        return "admin/subscriptions";
    }

    /**
     * Save / Update a Subscription Plan
     */
    @PostMapping("/plans/save")
    public String savePlan(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("monthlyPrice") Double monthlyPrice,
            @RequestParam(value = "courseLimit", required = false) Integer courseLimit,
            @RequestParam("features") String features,
            @RequestParam(value = "isCustom", defaultValue = "false") boolean isCustom,
            @RequestParam(value = "isPopular", defaultValue = "false") boolean isPopular,
            @RequestParam(value = "sortOrder", defaultValue = "0") Integer sortOrder,
            RedirectAttributes redirectAttributes) {

        try {
            SubscriptionPlan plan;
            if (id != null) {
                plan = subscriptionService.getPlanById(id)
                        .orElse(new SubscriptionPlan());
            } else {
                plan = new SubscriptionPlan();
            }

            plan.setName(name);
            plan.setDescription(description);
            plan.setMonthlyPrice(monthlyPrice);
            plan.setCourseLimit(courseLimit);
            plan.setFeatures(features);
            plan.setCustom(isCustom);
            plan.setPopular(isPopular);
            plan.setSortOrder(sortOrder);
            plan.setActive(true);

            subscriptionService.savePlan(plan);
            redirectAttributes.addFlashAttribute("success", "Subscription plan '" + name + "' saved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving plan: " + e.getMessage());
        }
        return "redirect:/admin/subscriptions";
    }

    /**
     * Toggle Plan active / inactive
     */
    @PostMapping("/plans/{id}/toggle")
    public String togglePlan(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        subscriptionService.getPlanById(id).ifPresent(plan -> {
            plan.setActive(!plan.isActive());
            subscriptionService.savePlan(plan);
            redirectAttributes.addFlashAttribute("success", "Plan '" + plan.getName() + "' is now " + (plan.isActive() ? "Active" : "Inactive") + ".");
        });
        return "redirect:/admin/subscriptions";
    }

    /**
     * Admin override of Subscription Status
     */
    @PostMapping("/{id}/status")
    public String updateSubscriptionStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String newStatus,
            RedirectAttributes redirectAttributes) {

        try {
            SubscriptionStatus status = SubscriptionStatus.valueOf(newStatus.toUpperCase());
            InstructorSubscription updated = subscriptionService.updateSubscriptionStatus(id, status);
            String instructorName = updated.getInstructor() != null ? updated.getInstructor().getName() : "Instructor";
            redirectAttributes.addFlashAttribute("success", 
                "Subscription #" + id + " (" + updated.getPlanName() + " for " + instructorName + ") status updated to " + status.name() + " successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update subscription status: " + e.getMessage());
        }
        return "redirect:/admin/subscriptions";
    }
}
