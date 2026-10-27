package com.skillhub.controller;

import com.skillhub.dto.CourseUsageDto;
import com.skillhub.model.InstructorSubscription;
import com.skillhub.model.User;
import com.skillhub.service.SubscriptionService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionRestController {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserService userService;

    /**
     * Calculate dynamic custom pricing for a given course quantity (1 - 50)
     */
    @GetMapping("/custom-price")
    public ResponseEntity<Map<String, Object>> calculatePrice(@RequestParam(value = "courses", defaultValue = "1") int courses) {
        if (courses < 1) courses = 1;
        if (courses > 50) courses = 50;

        double price = subscriptionService.calculateCustomPrice(courses);

        Map<String, Object> response = new HashMap<>();
        response.put("courses", courses);
        response.put("price", price);
        response.put("formattedPrice", "₹" + String.format("%,.0f", price));
        response.put("billingCycle", "Monthly (30 Days)");
        return ResponseEntity.ok(response);
    }

    /**
     * Get real-time course usage for the current logged-in instructor
     */
    @GetMapping("/usage")
    public ResponseEntity<?> getInstructorUsage(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        User instructor = userService.findByEmail(principal.getName());
        CourseUsageDto usage = subscriptionService.getCourseUsage(instructor);
        return ResponseEntity.ok(usage);
    }

    /**
     * Subscribe to Free Plan immediately (₹0 / 1 course)
     */
    @PostMapping("/subscribe-free")
    public ResponseEntity<Map<String, Object>> subscribeFree(Principal principal) {
        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("status", "error");
            response.put("message", "User not authenticated. Please log in.");
            return ResponseEntity.status(401).body(response);
        }

        try {
            User instructor = userService.findByEmail(principal.getName());
            if (instructor == null || instructor.isBlocked() || !Boolean.TRUE.equals(instructor.isApproved())) {
                response.put("status", "error");
                response.put("message", "Instructor account is not approved or is blocked.");
                return ResponseEntity.status(403).body(response);
            }
            InstructorSubscription sub = subscriptionService.subscribeFreePlan(instructor);

            response.put("status", "success");
            response.put("message", "Free Plan successfully activated! You can now publish 1 course per month.");
            response.put("subscriptionId", sub.getId());
            response.put("redirectUrl", "/vendor/subscription/success/FREE-" + sub.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to activate Free Plan: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Initiate Razorpay Order for a paid subscription
     */
    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "customCourses", required = false) Integer customCourses,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("status", "error");
            response.put("message", "Please log in to continue.");
            return ResponseEntity.status(401).body(response);
        }

        try {
            User instructor = userService.findByEmail(principal.getName());
            if (instructor == null || instructor.isBlocked() || !Boolean.TRUE.equals(instructor.isApproved())) {
                response.put("status", "error");
                response.put("message", "Instructor account is not approved or is blocked.");
                return ResponseEntity.status(403).body(response);
            }
            Map<String, Object> orderData = subscriptionService.createSubscriptionOrder(instructor, planId, customCourses);
            orderData.put("status", "success");
            return ResponseEntity.ok(orderData);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to initiate payment: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Verify Razorpay Payment Signature and Activate Subscription
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestBody Map<String, String> payload,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("status", "error");
            response.put("message", "Please log in to verify payment.");
            return ResponseEntity.status(401).body(response);
        }

        try {
            String orderId = payload.get("razorpay_order_id");
            String paymentId = payload.get("razorpay_payment_id");
            String signature = payload.get("razorpay_signature");

            if (orderId == null || paymentId == null || signature == null) {
                response.put("status", "error");
                response.put("message", "Missing payment verification parameters.");
                return ResponseEntity.badRequest().body(response);
            }

            User instructor = userService.findByEmail(principal.getName());
            if (instructor == null || instructor.isBlocked() || !Boolean.TRUE.equals(instructor.isApproved())) {
                response.put("status", "error");
                response.put("message", "Instructor account is not approved or is blocked.");
                return ResponseEntity.status(403).body(response);
            }
            InstructorSubscription sub = subscriptionService.verifyAndActivateSubscription(orderId, paymentId, signature, instructor);

            response.put("status", "success");
            response.put("message", "Payment verified and subscription activated successfully!");
            response.put("redirectUrl", "/vendor/subscription/success/" + paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Payment verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Cancel an active subscription
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @RequestParam("subscriptionId") Long subscriptionId,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();
        if (principal == null) {
            response.put("status", "error");
            response.put("message", "Unauthorized.");
            return ResponseEntity.status(401).body(response);
        }

        try {
            User instructor = userService.findByEmail(principal.getName());
            if (instructor == null || instructor.isBlocked() || !Boolean.TRUE.equals(instructor.isApproved())) {
                response.put("status", "error");
                response.put("message", "Instructor account is not approved or is blocked.");
                return ResponseEntity.status(403).body(response);
            }
            subscriptionService.cancelSubscription(subscriptionId, instructor, reason);

            response.put("status", "success");
            response.put("message", "Subscription renewal successfully cancelled.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to cancel subscription: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
