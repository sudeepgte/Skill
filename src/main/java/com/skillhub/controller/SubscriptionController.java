package com.skillhub.controller;

import com.skillhub.dto.CourseUsageDto;
import com.skillhub.model.InstructorSubscription;
import com.skillhub.model.SubscriptionPayment;
import com.skillhub.model.SubscriptionPlan;
import com.skillhub.model.User;
import com.skillhub.service.CourseService;
import com.skillhub.service.SubscriptionService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Value("${razorpay.key.id:rzp_test_RIlD5bEKRjyn3h}")
    private String razorpayKeyId;

    /**
     * Dedicated Instructor Subscription Management Page
     * URL: /vendor/subscription (and alias /instructor/subscription)
     */
    @GetMapping({"/vendor/subscription", "/instructor/subscription"})
    public String showInstructorSubscriptionPage(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User instructor = userService.findByEmail(principal.getName());
        if (instructor == null) {
            return "redirect:/login";
        }

        List<SubscriptionPlan> plans = subscriptionService.getAllActivePlans();
        Optional<InstructorSubscription> activeSubOpt = subscriptionService.getActiveSubscription(instructor);
        CourseUsageDto usage = subscriptionService.getCourseUsage(instructor);
        List<InstructorSubscription> history = subscriptionService.getSubscriptionsForInstructor(instructor);
        List<SubscriptionPayment> payments = subscriptionService.getPaymentsForInstructor(instructor);

        model.addAttribute("user", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("instructor", instructor);
        model.addAttribute("plans", plans);
        model.addAttribute("activeSubscription", activeSubOpt.orElse(null));
        model.addAttribute("hasActiveSubscription", activeSubOpt.isPresent());
        model.addAttribute("usage", usage);
        model.addAttribute("history", history);
        model.addAttribute("payments", payments);
        model.addAttribute("razorpayKeyId", razorpayKeyId);

        return "instructor/subscription";
    }

    /**
     * Subscription Success Page
     */
    @GetMapping({"/vendor/subscription/success/{txnId}", "/instructor/subscription/success/{txnId}"})
    public String showSubscriptionSuccess(@PathVariable("txnId") String txnId, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User instructor = userService.findByEmail(principal.getName());
        model.addAttribute("user", instructor);
        model.addAttribute("vendor", instructor);
        model.addAttribute("instructor", instructor);
        model.addAttribute("txnId", txnId);

        Optional<InstructorSubscription> activeSub = subscriptionService.getActiveSubscription(instructor);
        model.addAttribute("subscription", activeSub.orElse(null));

        return "instructor/subscription_success";
    }
}
