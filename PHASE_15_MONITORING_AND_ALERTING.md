# SkillHub Platform — Phase 15: Post-Launch Monitoring, Telemetry & Alerting Architecture

**Document Reference:** OPS-MON-PHASE15  
**Auditor Roles:** Principal Site Reliability Engineer, DevOps Engineer, Application Security Engineer  
**Framework Version:** Spring Boot 3.5.5 | Java 17 | Hibernate 6.6.26.Final | MySQL 8.0  
**Verification Date:** October 27, 2026  
**Operational Status:** LOCALLY VERIFIED & PREPARED FOR CLOUD INTEGRATION  

---

## 1. Executive Summary & Telemetry Architecture

SkillHub's operational monitoring framework is designed to provide end-to-end visibility across application runtime health, database connection pool saturation, JVM performance, transactional payment transitions, and security event anomalies. 

Because cloud hosting infrastructure is pending provisioning, this specification details the locally verified telemetry endpoints, logging MDC correlation patterns, and Prometheus/Grafana alert definitions ready for activation upon cloud staging/production deployment.

```mermaid
graph TD
    subgraph SkillHub Application Pod
        App[Spring Boot 3.5.5 JRE] --> Health[/api/health/liveness & /readiness]
        App --> Telemetry[/api/health Component Telemetry]
        App --> MDC[MDC Request Correlation Filter]
        App --> Counters[ExamObservabilityService]
    end

    subgraph Observability Pipeline (Target Cloud)
        Health --> Scraper[Prometheus / Vector Scraper]
        Telemetry --> Scraper
        Counters --> Scraper
        MDC --> FluentBit[Promtail / FluentBit]
        FluentBit --> Loki[Grafana Loki Log Storage]
        Scraper --> Prometheus[Prometheus Time-Series DB]
        Prometheus --> Grafana[Grafana Dashboards & AlertManager]
    end
```

---

## 2. Real-Time Application Health & Telemetry Endpoints

### 2.1 Liveness Probe (`GET /api/health/liveness`)
- **Target Consumer:** Docker healthcheck, Kubernetes liveness probe, AWS ALB target group health.
- **Payload:** `{"status":"UP","timestamp":"2026-10-27T14:18:33"}`
- **Response Code:** HTTP 200 OK.
- **Failure Condition:** If the Spring context deadlocks or JVM crashes, probe times out (5s), prompting orchestrator container restart.

### 2.2 Readiness Probe (`GET /api/health/readiness`)
- **Target Consumer:** Nginx upstream balancer, Kubernetes readiness probe.
- **Verification Logic:**
  1. Validates MySQL connection using JDBC `Connection.isValid(3)`.
  2. Validates disk write access to `/var/skillhub/uploads`.
- **Response Codes:**
  - `HTTP 200 OK`: `{"status":"UP","database":"UP","storage":"UP"}`
  - `HTTP 503 SERVICE UNAVAILABLE`: `{"status":"OUT_OF_SERVICE","database":"DOWN","storage":"UP"}`
- **Behavior:** Traffic is immediately drained from the node if MySQL disconnects, preventing 500 errors to end users.

### 2.3 Comprehensive Operational Telemetry (`GET /api/health`)
Provides a consolidated snapshot of system internals for operations engineers:
- **Database:** `status`, `databaseProductName` (MySQL).
- **Storage:** `status` (`UP` / `READ_ONLY`).
- **JVM Runtime:** `heapUsedMb`, `heapMaxMb`, `availableProcessors`, `activeThreads`.
- **HikariCP Connection Pool:** `activeConnections`, `idleConnections`, `totalConnections`, `threadsAwaitingConnection`.
- **Security & Business Telemetry:** Counters from `ExamObservabilityService` (`exam_start_count`, `evaluation_failure_count`, `coding_execution_count`, `certificate_generation_count`, `authorization_failure_count`, `rate_limit_count`).

---

## 3. Centralized Logging & Correlation Architecture

### 3.1 MDC Request Correlation IDs
Every incoming HTTP request is intercepted by `CorrelationIdFilter`:
- Inbound Header: `X-Request-ID` or `X-Correlation-ID` (propagated if valid).
- Outbound Header: `X-Request-ID` attached to all HTTP responses.
- MDC Token: `[requestId]` injected into Slf4j context.
- Production Console Log Pattern:
  ```text
  %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{requestId}] %-5level %logger{36} - %msg%n
  ```
- **Operational Value:** An operations engineer can grep a single UUID from user-reported issues or Nginx error logs to trace every database query, authentication check, and external service call across the entire request lifecycle.

### 3.2 Sensitive Data Masking Verification
A comprehensive audit confirmed that zero credentials, JWT tokens, OTPs, or passwords are logged:
- BCrypt hashes and plain passwords are completely excluded from logs.
- Global exception handler intercepts unhandled exceptions, logs full stack traces internally with `[requestId]`, and returns a safe sanitized JSON or error view to the client.

---

## 4. Production Alerting Rules & Escalation Thresholds

The following rules are formatted for Prometheus Alertmanager (`skillhub_alerts.yml`):

| Alert Name | Severity | Metric Expression / Condition | Duration | Operational Action |
| :--- | :---: | :--- | :---: | :--- |
| **SkillHubInstanceDown** | **CRITICAL** | `probe_success{job="skillhub-liveness"} == 0` | 1m | Trigger on-call pager; inspect container crash logs. |
| **HighHttp5xxRate** | **CRITICAL** | `sum(rate(nginx_http_requests_total{status=~"5.."}[2m])) / sum(rate(nginx_http_requests_total[2m])) > 0.02` | 2m | Trigger rollback investigation; check DB connectivity. |
| **HikariConnectionPoolExhaustion** | **HIGH** | `hikari_active_connections / hikari_total_connections > 0.85` | 3m | Inspect slow queries via MySQL `SHOW PROCESSLIST`. |
| **HighRequestLatencyP95** | **HIGH** | `histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket[5m])) by (le)) > 2.5` | 5m | Check database locks and CPU utilization. |
| **DiskSpaceRunningLow** | **HIGH** | `node_filesystem_free_bytes{mountpoint="/var/skillhub"} / node_filesystem_size_bytes < 0.15` | 5m | Purge old backups beyond retention policy. |
| **SuspiciousAuthFailureSpike** | **MEDIUM** | `sum(rate(skillhub_auth_failures_total[5m])) > 10` | 2m | Investigate potential credential stuffing; verify rate limiters. |
| **RazorpayVerificationFailures** | **HIGH** | `sum(rate(skillhub_payment_verification_failures[5m])) > 0` | 1m | Investigate signature mismatch or merchant key expiration. |
| **TLSCertificateExpiringSoon** | **MEDIUM** | `probe_ssl_earliest_cert_expiry - time() < 14 * 86400` | 1h | Verify Let's Encrypt certbot automated cron renewal. |

---

## 5. Capacity Boundaries & Scaling Telemetry

Based on Phase 09 load testing baselines:
- **Observed Baseline:** 100 Virtual Users sustained **154.7 req/s** with **973ms p95** latency and **0.00% error rate** on 50 HikariCP connections.
- **Single-Node Capacity Limit:** ~200 req/s. Beyond this threshold, thread contention and HikariCP connection starvation require horizontal scaling.
- **Path to 10M Users:**
  1. **Tier 1 (10M Registered):** Supported by existing $O(\log N)$ composite indexes on MySQL 8.0.
  2. **Tier 2 (10M DAU):** Requires Cloudflare CDN edge caching (offloading 80%+ of catalog queries) and 1 Primary + 2 Read Replicas.
  3. **Tier 3 (10M Concurrent):** Requires distributed Kubernetes pods, Spring Session Data Redis, AWS S3 object storage, and database sharding.
