package com.skillhub.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.skillhub.dto.CourseUsageDto;
import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.InstructorSubscriptionRepository;
import com.skillhub.repository.SubscriptionPaymentRepository;
import com.skillhub.repository.SubscriptionPlanRepository;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SubscriptionService {

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private InstructorSubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private com.skillhub.repository.UserRepository userRepository;

    @Value("${razorpay.key.id:rzp_test_RIlD5bEKRjyn3h}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:Ltg6uo9vI8TiFMVfj2cGm4I8}")
    private String razorpayKeySecret;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() {
        try {
            if (razorpayKeyId != null && razorpayKeySecret != null && !razorpayKeyId.isEmpty() && !razorpayKeySecret.isEmpty()) {
                this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            }
        } catch (Exception e) {
            System.err.println("SubscriptionService: Could not initialize Razorpay client: " + e.getMessage());
        }

        // Seed default subscription plans if table is empty
        seedDefaultPlansIfEmpty();
    }

    private synchronized RazorpayClient getClient() throws RazorpayException {
        if (this.razorpayClient == null) {
            this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        }
        return this.razorpayClient;
    }

    @Transactional
    public void seedDefaultPlansIfEmpty() {
        if (planRepository.count() > 0) return;

        List<SubscriptionPlan> defaultPlans = Arrays.asList(
            new SubscriptionPlan("Free", "Ideal for new instructors to get started and explore the platform.", 0.0, 1,
                "Publish 1 Course / Month, Standard Student Review, Community Support, Standard Video Hosting", false, false, 1),

            new SubscriptionPlan("Starter", "Perfect for part-time educators building their initial course catalog.", 299.0, 3,
                "Publish 3 Courses / Month, Student Q&A Dashboard, Basic Performance Analytics, Certificate Generation, Email Support", false, false, 2),

            new SubscriptionPlan("Growth", "Best for active creators expanding their student reach and revenue.", 499.0, 5,
                "Publish 5 Courses / Month, Priority Review Queue, Comprehensive Course Analytics, Student Messaging, Fast Support", false, true, 3),

            new SubscriptionPlan("Professional", "Built for professional educators and training institutes.", 999.0, 10,
                "Publish 10 Courses / Month, VIP Review Turnaround, Advanced Student Progress Tracking, Promotional Tools, Dedicated Support", false, false, 4),

            new SubscriptionPlan("Enterprise", "Tailored publishing allowance with volume pricing for institutions.", 1899.0, null,
                "Custom Course Limit (Up to 50 Courses), Institutional SLA, Custom Branding, Dedicated Account Manager, Priority CDN", true, false, 5)
        );

        planRepository.saveAll(defaultPlans);
        System.out.println("Default Instructor Subscription Plans seeded successfully.");
    }

    public List<SubscriptionPlan> getAllActivePlans() {
        return planRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    public List<SubscriptionPlan> getAllPlans() {
        return planRepository.findAll();
    }

    public Optional<SubscriptionPlan> getPlanById(Long id) {
        return planRepository.findById(id);
    }

    public Optional<SubscriptionPlan> getPlanByName(String name) {
        return planRepository.findByNameIgnoreCase(name);
    }

    @Transactional
    public SubscriptionPlan savePlan(SubscriptionPlan plan) {
        return planRepository.save(plan);
    }

    /**
     * Calculate custom tiered pricing for any selected course count (1 to 50)
     */
    public double calculateCustomPrice(int courses) {
        if (courses <= 0) return 0.0;
        if (courses == 1) return 0.0;
        if (courses == 2) return 199.0;
        if (courses == 3) return 299.0;
        if (courses == 4) return 399.0;
        if (courses == 5) return 499.0;
        if (courses <= 9) {
            // ₹499 base for 5 + ₹100 per additional course
            return 499.0 + ((courses - 5) * 100.0);
        }
        if (courses == 10) return 999.0;
        if (courses <= 20) {
            // ₹999 base for 10 + ₹90 per additional course
            return 999.0 + ((courses - 10) * 90.0);
        }
        // 21 to 50 courses: ₹1899 for 20 + ₹80 per additional course
        return 1899.0 + ((courses - 20) * 80.0);
    }

    /**
     * Get active subscription for instructor
     */
    public Optional<InstructorSubscription> getActiveSubscription(User instructor) {
        if (instructor == null) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        List<InstructorSubscription> activeList = subscriptionRepository.findActiveSubscriptions(instructor, SubscriptionStatus.ACTIVE, now);
        if (!activeList.isEmpty()) {
            return Optional.of(activeList.get(0));
        }
        // Fallback: Check if there is an active subscription record whose endDate >= now
        return subscriptionRepository.findFirstByInstructorAndStatusOrderByEndDateDesc(instructor, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getEndDate() != null && !s.getEndDate().isBefore(now));
    }

    /**
     * Get monthly course usage statistics for instructor
     */
    public CourseUsageDto getCourseUsage(User instructor) {
        if (instructor == null) {
            return new CourseUsageDto(0, 0, 0, true, LocalDateTime.now(), LocalDateTime.now().plusDays(30), "None", "NONE");
        }

        Optional<InstructorSubscription> activeSubOpt = getActiveSubscription(instructor);
        if (activeSubOpt.isPresent()) {
            InstructorSubscription sub = activeSubOpt.get();
            long usedCount = courseRepository.countCoursesCreatedInPeriod(instructor.getId(), sub.getStartDate(), sub.getEndDate());
            int limit = sub.getCourseLimit() != null ? sub.getCourseLimit() : 1;
            int remaining = Math.max(0, limit - (int) usedCount);
            boolean isReached = usedCount >= limit;
            return new CourseUsageDto((int) usedCount, limit, remaining, isReached, sub.getStartDate(), sub.getEndDate(), sub.getPlanName(), sub.getStatus().name());
        }

        // No active subscription: fallback to calendar month with Free 1-course allowance
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
        long usedCount = courseRepository.countCoursesCreatedInPeriod(instructor.getId(), startOfMonth, endOfMonth);
        int freeLimit = 1;
        int remaining = Math.max(0, freeLimit - (int) usedCount);
        boolean isReached = usedCount >= freeLimit;
        return new CourseUsageDto((int) usedCount, freeLimit, remaining, isReached, startOfMonth, endOfMonth, "Free Plan (Default)", "INACTIVE");
    }

    /**
     * Check if instructor is currently permitted to create a new course
     */
    public boolean canCreateCourse(User instructor) {
        CourseUsageDto usage = getCourseUsage(instructor);
        return !usage.isLimitReached();
    }

    /**
     * Subscribe to Free Plan immediately (₹0 / 1 course per month)
     */
    @Transactional
    public InstructorSubscription subscribeFreePlan(User instructor) {
        if (instructor != null && instructor.getId() != null) {
            userRepository.findByIdWithPessimisticLock(instructor.getId());
        }
        LocalDateTime now = LocalDateTime.now();

        // Deactivate any currently active subscriptions to avoid duplicates
        List<InstructorSubscription> currentActive = subscriptionRepository.findActiveSubscriptions(instructor, SubscriptionStatus.ACTIVE, now);
        for (InstructorSubscription sub : currentActive) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setCancelledAt(now);
            sub.setCancellationReason("Replaced by Free Plan activation");
            subscriptionRepository.save(sub);
        }

        SubscriptionPlan freePlan = planRepository.findByNameIgnoreCase("Free")
                .orElse(null);

        InstructorSubscription sub = new InstructorSubscription();
        sub.setInstructor(instructor);
        sub.setPlan(freePlan);
        sub.setPlanName(freePlan != null ? freePlan.getName() : "Free");
        sub.setCourseLimit(1);
        sub.setMonthlyPrice(0.0);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingInterval("MONTHLY");
        sub.setStartDate(now);
        sub.setEndDate(now.plusDays(30));
        sub.setAutoRenew(true);

        InstructorSubscription savedSub = subscriptionRepository.save(sub);

        // Record successful free tier payment
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setSubscription(savedSub);
        payment.setInstructor(instructor);
        payment.setAmount(0.0);
        payment.setTransactionId("FREE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setPaymentMethod("FREE_TIER");
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentDate(now);
        payment.setNotes("Free Plan activated for instructor.");
        paymentRepository.save(payment);

        return savedSub;
    }

    /**
     * Create Razorpay Order for a paid subscription (Standard Plan or Custom Course Quantity)
     */
    @Transactional
    public Map<String, Object> createSubscriptionOrder(User instructor, Long planId, Integer customCourses) throws RazorpayException {
        SubscriptionPlan plan = null;
        if (planId != null) {
            plan = planRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("Subscription Plan not found with id: " + planId));
        }

        int courseLimit;
        double amount;
        String planDisplayName;

        if (plan != null && !plan.isCustom()) {
            courseLimit = plan.getCourseLimit();
            amount = plan.getMonthlyPrice();
            planDisplayName = plan.getName();
        } else {
            // Custom Enterprise tier
            if (customCourses == null || customCourses < 1) {
                customCourses = 1;
            } else if (customCourses > 50) {
                customCourses = 50;
            }
            courseLimit = customCourses;
            amount = calculateCustomPrice(customCourses);
            planDisplayName = "Custom Plan (" + customCourses + " Courses)";
        }

        // Amount in paise
        int amountInPaise = (int) Math.round(amount * 100);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "sub_" + UUID.randomUUID().toString().substring(0, 8));

        Order razorpayOrder = getClient().orders.create(orderRequest);
        String orderId = razorpayOrder.get("id");

        LocalDateTime now = LocalDateTime.now();

        // Create PENDING subscription record
        InstructorSubscription sub = new InstructorSubscription();
        sub.setInstructor(instructor);
        sub.setPlan(plan);
        sub.setPlanName(planDisplayName);
        sub.setCourseLimit(courseLimit);
        sub.setMonthlyPrice(amount);
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        sub.setBillingInterval("MONTHLY");
        sub.setStartDate(now);
        sub.setEndDate(now.plusDays(30));
        sub.setAutoRenew(true);
        InstructorSubscription savedSub = subscriptionRepository.save(sub);

        // Create PENDING payment record
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setSubscription(savedSub);
        payment.setInstructor(instructor);
        payment.setAmount(amount);
        payment.setTransactionId("PEND-SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setPaymentMethod("RAZORPAY");
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setRazorpayOrderId(orderId);
        payment.setNotes("Subscription order initiated for " + planDisplayName);
        paymentRepository.save(payment);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("amount", amountInPaise);
        result.put("amountInr", amount);
        result.put("planName", planDisplayName);
        result.put("courseLimit", courseLimit);
        result.put("keyId", razorpayKeyId);
        result.put("userName", instructor.getName() != null ? instructor.getName() : "Instructor");
        result.put("userEmail", instructor.getEmail() != null ? instructor.getEmail() : "");
        result.put("userPhone", instructor.getPhone() != null && !instructor.getPhone().isEmpty() ? instructor.getPhone() : "9876543210");

        return result;
    }

    /**
     * Verify Razorpay Payment Signature and Activate Subscription
     */
    @Transactional
    public InstructorSubscription verifyAndActivateSubscription(String orderId, String paymentId, String signature, User instructor) {
        if (instructor != null && instructor.getId() != null) {
            userRepository.findByIdWithPessimisticLock(instructor.getId());
        }
        SubscriptionPayment payment = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Subscription payment record not found for Razorpay order: " + orderId));

        if (!payment.getInstructor().getId().equals(instructor.getId())) {
            throw new RuntimeException("Unauthorized: Payment does not belong to the current authenticated instructor.");
        }

        // Idempotency: if already paid and active, return without duplicate operations
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS &&
            payment.getSubscription() != null &&
            payment.getSubscription().getStatus() == SubscriptionStatus.ACTIVE) {
            return payment.getSubscription();
        }

        // Verify Razorpay HMAC signature
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
            if (!isValid) {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                throw new RuntimeException("Payment signature verification failed.");
            }
        } catch (Exception e) {
            System.err.println("Subscription signature check error: " + e.getMessage());
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new RuntimeException("Invalid payment signature: " + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Mark payment as SUCCESS
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(paymentId);
        payment.setRazorpaySignature(signature);
        payment.setTransactionId(paymentId);
        payment.setPaymentDate(now);
        payment.setNotes("Payment confirmed via Razorpay for subscription.");
        paymentRepository.save(payment);

        // 2. Deactivate previous active subscriptions for this instructor
        List<InstructorSubscription> currentActive = subscriptionRepository.findActiveSubscriptions(instructor, SubscriptionStatus.ACTIVE, now);
        for (InstructorSubscription oldSub : currentActive) {
            if (!oldSub.getId().equals(payment.getSubscription().getId())) {
                oldSub.setStatus(SubscriptionStatus.CANCELLED);
                oldSub.setCancelledAt(now);
                oldSub.setCancellationReason("Upgraded to new subscription: " + payment.getSubscription().getPlanName());
                subscriptionRepository.save(oldSub);
            }
        }

        // 3. Activate the new subscription
        InstructorSubscription sub = payment.getSubscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(now);
        sub.setEndDate(now.plusDays(30));
        sub.setAutoRenew(true);
        InstructorSubscription activeSub = subscriptionRepository.save(sub);

        System.out.println("Instructor Subscription " + activeSub.getId() + " activated successfully for " + instructor.getEmail());
        return activeSub;
    }

    /**
     * Admin override of Subscription Status with immediate cascade to instructor
     */
    @Transactional
    public InstructorSubscription updateSubscriptionStatus(Long subscriptionId, SubscriptionStatus newStatus) {
        InstructorSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found with ID: " + subscriptionId));

        LocalDateTime now = LocalDateTime.now();
        User instructor = sub.getInstructor();

        if (newStatus == SubscriptionStatus.ACTIVE) {
            // 1. Ensure valid start and end dates covering active period
            if (sub.getStartDate() == null || sub.getStartDate().isAfter(now)) {
                sub.setStartDate(now);
            }
            if (sub.getEndDate() == null || !sub.getEndDate().isAfter(now)) {
                sub.setStartDate(now);
                sub.setEndDate(now.plusDays(30));
            }
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setAutoRenew(true);
            sub.setCancelledAt(null);
            sub.setCancellationReason(null);

            // 2. Deactivate any other active subscriptions for this instructor to avoid ambiguity
            if (instructor != null) {
                List<InstructorSubscription> allSubs = subscriptionRepository.findByInstructorOrderByCreatedAtDesc(instructor);
                for (InstructorSubscription other : allSubs) {
                    if (!other.getId().equals(sub.getId()) && other.getStatus() == SubscriptionStatus.ACTIVE) {
                        other.setStatus(SubscriptionStatus.CANCELLED);
                        other.setAutoRenew(false);
                        other.setCancelledAt(now);
                        other.setCancellationReason("Superseded by Admin activating Subscription #" + sub.getId());
                        subscriptionRepository.save(other);
                    }
                }
            }

            // 3. Mark pending payments for this subscription as SUCCESS
            List<SubscriptionPayment> payments = paymentRepository.findAll();
            for (SubscriptionPayment p : payments) {
                if (p.getSubscription() != null && p.getSubscription().getId().equals(sub.getId())) {
                    if (p.getPaymentStatus() == PaymentStatus.PENDING) {
                        p.setPaymentStatus(PaymentStatus.SUCCESS);
                        p.setPaymentDate(now);
                        p.setNotes("Payment approved and marked SUCCESS by Admin override.");
                        paymentRepository.save(p);
                    }
                }
            }

        } else if (newStatus == SubscriptionStatus.CANCELLED) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setAutoRenew(false);
            sub.setCancelledAt(now);
            sub.setCancellationReason("Status updated to CANCELLED by Admin");

        } else if (newStatus == SubscriptionStatus.EXPIRED) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setAutoRenew(false);
            if (sub.getEndDate() == null || sub.getEndDate().isAfter(now)) {
                sub.setEndDate(now.minusSeconds(1));
            }

        } else if (newStatus == SubscriptionStatus.PENDING_PAYMENT) {
            sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
            sub.setAutoRenew(false);
        }

        InstructorSubscription saved = subscriptionRepository.save(sub);
        System.out.println("Admin updated Subscription #" + saved.getId() + " status to " + saved.getStatus() + 
                           " for instructor " + (instructor != null ? instructor.getEmail() : "N/A"));
        return saved;
    }

    /**
     * Cancel an instructor subscription
     */
    @Transactional
    public InstructorSubscription cancelSubscription(Long subscriptionId, User user, String reason) {
        InstructorSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        boolean isAdmin = user.getRole() != null && (user.getRole().equalsIgnoreCase("ADMIN") || user.getRole().equalsIgnoreCase("ROLE_ADMIN"));
        if (!isAdmin && !sub.getInstructor().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized to cancel this subscription.");
        }

        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setAutoRenew(false);
        sub.setCancelledAt(LocalDateTime.now());
        sub.setCancellationReason(reason != null && !reason.trim().isEmpty() ? reason : "Cancelled by user");
        return subscriptionRepository.save(sub);
    }

    /**
     * Get payment history for instructor
     */
    public List<SubscriptionPayment> getPaymentsForInstructor(User instructor) {
        return paymentRepository.findByInstructorOrderByCreatedAtDesc(instructor);
    }

    /**
     * Get subscription history for instructor
     */
    public List<InstructorSubscription> getSubscriptionsForInstructor(User instructor) {
        return subscriptionRepository.findByInstructorOrderByCreatedAtDesc(instructor);
    }

    /**
     * Admin: get all subscriptions with search/filter
     */
    public List<InstructorSubscription> searchSubscriptions(SubscriptionStatus status, String planName, String search) {
        return subscriptionRepository.searchSubscriptions(status, planName, search);
    }

    /**
     * Admin: metrics
     */
    public Map<String, Object> getAdminSubscriptionMetrics() {
        LocalDateTime now = LocalDateTime.now();
        long activeCount = subscriptionRepository.countCurrentlyActive(now);
        Double monthlyRevenue = subscriptionRepository.sumMonthlyActiveRevenue(now);
        Double totalLifetimeRevenue = paymentRepository.sumTotalSuccessfulRevenue();
        long successfulPaymentsCount = paymentRepository.countByPaymentStatus(PaymentStatus.SUCCESS);
        long pendingSubscriptions = subscriptionRepository.countByStatus(SubscriptionStatus.PENDING_PAYMENT);
        long totalPlans = planRepository.count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("activeSubscriptions", activeCount);
        metrics.put("monthlyRevenue", monthlyRevenue != null ? monthlyRevenue : 0.0);
        metrics.put("totalLifetimeRevenue", totalLifetimeRevenue != null ? totalLifetimeRevenue : 0.0);
        metrics.put("successfulPaymentsCount", successfulPaymentsCount);
        metrics.put("pendingSubscriptions", pendingSubscriptions);
        metrics.put("totalPlans", totalPlans);
        return metrics;
    }

    /**
     * Webhook activation for instructor subscription
     */
    @Transactional
    public InstructorSubscription activateSubscriptionFromWebhook(String orderId, String paymentId, Integer amountPaise) {
        SubscriptionPayment payment = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new BusinessException("Subscription payment record not found for Razorpay order: " + orderId));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS &&
                payment.getSubscription() != null &&
                payment.getSubscription().getStatus() == SubscriptionStatus.ACTIVE) {
            return payment.getSubscription();
        }

        User instructor = payment.getInstructor();
        if (instructor != null && instructor.getId() != null) {
            userRepository.findByIdWithPessimisticLock(instructor.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(paymentId);
        payment.setTransactionId(paymentId);
        payment.setPaymentDate(now);
        payment.setNotes("Payment confirmed via Razorpay Webhook.");
        paymentRepository.save(payment);

        List<InstructorSubscription> currentActive = subscriptionRepository.findActiveSubscriptions(instructor, SubscriptionStatus.ACTIVE, now);
        for (InstructorSubscription oldSub : currentActive) {
            if (!oldSub.getId().equals(payment.getSubscription().getId())) {
                oldSub.setStatus(SubscriptionStatus.CANCELLED);
                oldSub.setCancelledAt(now);
                oldSub.setCancellationReason("Upgraded via webhook: " + payment.getSubscription().getPlanName());
                subscriptionRepository.save(oldSub);
            }
        }

        InstructorSubscription sub = payment.getSubscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(now);
        sub.setEndDate(now.plusDays(30));
        sub.setAutoRenew(true);
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public void processWebhookSubscriptionFailed(String orderId, String reason) {
        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(p -> {
            p.setPaymentStatus(PaymentStatus.FAILED);
            p.setNotes("Failed via webhook: " + reason);
            paymentRepository.save(p);
        });
    }
}
