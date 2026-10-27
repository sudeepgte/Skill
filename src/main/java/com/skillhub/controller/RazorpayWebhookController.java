package com.skillhub.controller;

import com.razorpay.Utils;
import com.skillhub.service.PaymentService;
import com.skillhub.service.SubscriptionService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Value("${razorpay.webhook.secret:SkillHubWebhookSecret2026}")
    private String webhookSecret;

    /**
     * Razorpay Server-to-Server Webhook Handler
     * Validates cryptographic signature against the raw request body.
     */
    @PostMapping(value = {"/payment/webhook", "/razorpay/webhook"})
    public ResponseEntity<Map<String, Object>> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (signature == null || signature.trim().isEmpty()) {
            log.warn("Rejected webhook request: Missing X-Razorpay-Signature header.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Missing webhook signature"));
        }

        // Requirement 2: Validate webhook signature against the raw request body
        try {
            boolean isValid = Utils.verifyWebhookSignature(rawPayload, signature, webhookSecret);
            if (!isValid) {
                log.warn("Rejected webhook request: Cryptographic signature mismatch.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "Invalid webhook signature"));
            }
        } catch (Exception e) {
            log.error("Webhook signature verification exception: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Signature verification error"));
        }

        try {
            JSONObject event = new JSONObject(rawPayload);
            String eventType = event.optString("event");
            JSONObject payloadObj = event.optJSONObject("payload");

            log.info("Processing verified Razorpay webhook event: {}", eventType);

            switch (eventType) {
                case "payment.captured", "order.paid" -> {
                    JSONObject paymentEntity = payloadObj != null && payloadObj.has("payment") 
                            ? payloadObj.getJSONObject("payment").getJSONObject("entity")
                            : (payloadObj != null && payloadObj.has("order") 
                                ? payloadObj.getJSONObject("order").getJSONObject("entity") 
                                : null);

                    if (paymentEntity != null) {
                        String orderId = paymentEntity.optString("order_id");
                        if (orderId.isEmpty() && paymentEntity.has("id") && paymentEntity.getString("id").startsWith("order_")) {
                            orderId = paymentEntity.getString("id");
                        }
                        String paymentId = paymentEntity.optString("id");
                        int amount = paymentEntity.optInt("amount", 0);

                        // Try resolving course payment first
                        try {
                            paymentService.processWebhookPaymentCaptured(orderId, paymentId, amount);
                        } catch (Exception ex) {
                            log.debug("Not a course payment order, checking subscription: {}", ex.getMessage());
                        }

                        // Try resolving subscription payment
                        try {
                            subscriptionService.activateSubscriptionFromWebhook(orderId, paymentId, amount);
                        } catch (Exception ex) {
                            log.debug("Not a subscription payment order: {}", ex.getMessage());
                        }
                    }
                }

                case "payment.failed" -> {
                    if (payloadObj != null && payloadObj.has("payment")) {
                        JSONObject paymentEntity = payloadObj.getJSONObject("payment").getJSONObject("entity");
                        String orderId = paymentEntity.optString("order_id");
                        String errorReason = paymentEntity.optString("error_description", "Payment failed");

                        paymentService.processWebhookPaymentFailed(orderId, errorReason);
                        subscriptionService.processWebhookSubscriptionFailed(orderId, errorReason);
                    }
                }

                case "refund.processed" -> {
                    if (payloadObj != null && payloadObj.has("refund")) {
                        JSONObject refundEntity = payloadObj.getJSONObject("refund").getJSONObject("entity");
                        String paymentId = refundEntity.optString("payment_id");
                        double amount = refundEntity.optDouble("amount", 0.0) / 100.0;

                        paymentService.processWebhookRefund(paymentId, amount);
                    }
                }

                default -> log.info("Unhandled webhook event type: {}", eventType);
            }

            return ResponseEntity.ok(Map.of("status", "success", "message", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("Error processing Razorpay webhook body: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Internal processing error"));
        }
    }
}
