package com.skillhub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Operational Observability and Metrics Service for SkillHub Professional Certification.
 * Tracks high-priority real-time operational telemetry for system health, security, and load monitoring.
 */
@Service
public class ExamObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(ExamObservabilityService.class);

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public ExamObservabilityService() {
        // Initialize standard metrics
        getCounter("exam_start_count");
        getCounter("exam_submission_count");
        getCounter("auto_submission_count");
        getCounter("evaluation_failure_count");
        getCounter("coding_execution_count");
        getCounter("coding_failure_count");
        getCounter("sql_execution_failure_count");
        getCounter("certificate_generation_count");
        getCounter("certificate_verification_count");
        getCounter("authorization_failure_count");
        getCounter("rate_limit_count");
    }

    private AtomicLong getCounter(String name) {
        return counters.computeIfAbsent(name, k -> new AtomicLong(0));
    }

    public void increment(String metricName) {
        long value = getCounter(metricName).incrementAndGet();
        log.debug("Metric [{}] incremented to {}", metricName, value);
    }

    public long getCount(String metricName) {
        return getCounter(metricName).get();
    }

    public Map<String, Long> getAllMetrics() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> snapshot.put(key, value.get()));
        return snapshot;
    }
}
