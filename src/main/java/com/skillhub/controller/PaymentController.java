package com.skillhub.controller;

import com.skillhub.model.Course;
import com.skillhub.model.Payment;
import com.skillhub.model.User;
import com.skillhub.service.CourseService;
import com.skillhub.service.PaymentService;
import com.skillhub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import java.util.Map;
import java.util.HashMap;

@Controller
public class PaymentController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserService userService;

    @GetMapping("/checkout/{courseId}")
    public String showCheckout(@PathVariable Long courseId, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        Course course = courseService.getCourseById(courseId);
        if (course == null) return "redirect:/";
        
        User user = userService.findByEmail(principal.getName());
        model.addAttribute("course", course);
        model.addAttribute("user", user);
        return "course/checkout";
    }

    /**
     * Create Razorpay Order
     */
    @PostMapping("/payment/create-order")
    @ResponseBody
    public Map<String, Object> createOrder(@RequestParam Long courseId, Principal principal) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (principal == null) {
                response.put("status", "error");
                response.put("message", "User not logged in");
                return response;
            }

            User user = userService.findByEmail(principal.getName());
            Course course = courseService.getCourseById(courseId);

            Payment payment = paymentService.createOrder(user, course);
            
            response.put("status", "success");
            response.put("orderId", payment.getRazorpayOrderId());
            response.put("amount", (int)(payment.getAmount() * 100)); // paise
            response.put("userName", user.getName());
            response.put("userEmail", user.getEmail());
            response.put("userPhone", user.getPhone());
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * Verify Razorpay Payment
     */
    @PostMapping("/payment/verify")
    @ResponseBody
    public Map<String, Object> verifyPayment(@RequestBody Map<String, String> data, Principal principal) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (principal == null) {
                response.put("status", "error");
                response.put("message", "User not logged in");
                return response;
            }

            String orderId = data.get("razorpay_order_id");
            String paymentId = data.get("razorpay_payment_id");
            String signature = data.get("razorpay_signature");

            boolean isValid = paymentService.verifyPayment(orderId, paymentId, signature);
            
            if (isValid) {
                response.put("status", "success");
                response.put("redirectUrl", "/payment/success/" + paymentId);
            } else {
                response.put("status", "error");
                response.put("message", "Invalid signature");
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PostMapping("/payment/process")
    public String processPayment(@RequestParam Long courseId, @RequestParam String method, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        User user = userService.findByEmail(principal.getName());
        Course course = courseService.getCourseById(courseId);
        
        Payment payment = paymentService.processPayment(user, course, method);
        return "redirect:/payment/success/" + payment.getTransactionId();
    }

    @GetMapping("/payment/success/{txnId}")
    public String showSuccess(@PathVariable String txnId, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        Payment payment = paymentService.getPaymentByTransactionId(txnId);
        if (payment == null) return "redirect:/";
        
        model.addAttribute("payment", payment);
        return "course/payment_success";
    }

    @GetMapping("/payment/receipt/{txnId}")
    public String showReceipt(@PathVariable String txnId, Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        
        Payment payment = paymentService.getPaymentByTransactionId(txnId);
        if (payment == null) return "redirect:/";
        
        model.addAttribute("payment", payment);
        return "course/payment_receipt";
    }
}
