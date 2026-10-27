# SkillHub Platform — Phase 10: Production Release Checklist

**Release Target:** SkillHub v1.0.0-PROD  
**Evaluation Standard:** Production Hardening Specification (Phases 00–10)  
**Verification Date:** October 27, 2026  
**Auditor:** Principal Software Architect & QA Lead  

---

## Status Legend
- `[VERIFIED PASS]` — Confirmed by automated integration tests, source inspection, and configuration checks.
- `[CONDITIONAL PASS]` — Architecturally complete and verified in test harness; requires environmental secret injection in production.
- `[BLOCKED / PENDING INFRASTRUCTURE]` — Feature cannot be enabled in production until dedicated infrastructure is deployed.
- `[NOT TESTED (STAGING REQUIRED)]` — Requires live third-party staging handshake (e.g., live banking network, real SMTP server).

---

## 1. Identity, Authentication & Session Security

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 1.1 | BCrypt password hashing with work factor $\ge 10$ | `AuthHardeningIntegrationTest.testPasswordHashing` | `[VERIFIED PASS]` |
| 1.2 | Unapproved instructor login prevention | `AuthHardeningIntegrationTest.testUnapprovedInstructorCannotLogin` | `[VERIFIED PASS]` |
| 1.3 | Admin registration endpoint lockdown (Security Key) | `AuthHardeningIntegrationTest.testUnauthenticatedCannotRegisterAdmin` | `[VERIFIED PASS]` |
| 1.4 | JWT signature verification & expired token rejection | `AuthHardeningIntegrationTest.testJwtTamperingAndExpiration` | `[VERIFIED PASS]` |
| 1.5 | Role-based authorization on protected endpoints | `AuthHardeningIntegrationTest.testRbacEnforcement` | `[VERIFIED PASS]` |
| 1.6 | IDOR prevention on student/instructor resources | `AuthHardeningIntegrationTest.testIdorProtection` | `[VERIFIED PASS]` |
| 1.7 | Session fixation protection & secure cookie attributes | `application-prod.properties` (`http-only`, `secure`, `same-site=lax`) | `[VERIFIED PASS]` |
| 1.8 | Real SMTP email verification delivery | Requires production mail server configuration | `[NOT TESTED (STAGING REQUIRED)]` |

---

## 2. Web Application Security & API Defense

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 2.1 | CSRF enabled on browser session state-changing endpoints | `SpringBootSecurityHardeningIntegrationTest.testCsrfProtection` | `[VERIFIED PASS]` |
| 2.2 | CSRF exemption restricted to stateless webhooks & health | `SecurityConfig.java` | `[VERIFIED PASS]` |
| 2.3 | CORS restricted to authorized production origins | `SecurityConfig.corsConfigurationSource` | `[VERIFIED PASS]` |
| 2.4 | Strict Content Security Policy (CSP) header enforcement | `SpringBootSecurityHardeningIntegrationTest.testSecurityHeaders` | `[VERIFIED PASS]` |
| 2.5 | Frame protection (`X-Frame-Options: DENY`) | `SpringBootSecurityHardeningIntegrationTest.testSecurityHeaders` | `[VERIFIED PASS]` |
| 2.6 | MIME-type sniffing defense (`X-Content-Type-Options: nosniff`) | `SpringBootSecurityHardeningIntegrationTest.testSecurityHeaders` | `[VERIFIED PASS]` |
| 2.7 | Strict-Transport-Security (HSTS) in Nginx & Spring | `nginx/skillhub.conf` | `[VERIFIED PASS]` |
| 2.8 | Global exception handling sanitizing internal stack traces | `GlobalExceptionHandler.java` | `[VERIFIED PASS]` |
| 2.9 | Rate limiting on login and sensitive endpoints | Nginx `limit_req` & Bucket4j filter | `[VERIFIED PASS]` |

---

## 3. Persistence, Transactions & Concurrency

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 3.1 | Transactional boundaries on critical workflows | `DatabaseProductionHardeningIntegrationTest` | `[VERIFIED PASS]` |
| 3.2 | Pessimistic locking on course creation quotas | `CourseService.saveCourse` & `DatabaseHardeningTest` | `[VERIFIED PASS]` |
| 3.3 | Unique constraints on Wishlist and Reviews | `uk_wishlist_user_course`, `uk_reviews_user_course` | `[VERIFIED PASS]` |
| 3.4 | Multi-column indexing on high-frequency queries | `Notification(user_id, is_read)` index | `[VERIFIED PASS]` |
| 3.5 | N+1 review query elimination on course lists | `CourseService.populateCourseRatingsBatch` (4ms for 20 courses) | `[VERIFIED PASS]` |
| 3.6 | JPA cascade rules & orphan removal safety | Entity inspection (`Course`, `Section`, `Lecture`) | `[VERIFIED PASS]` |
| 3.7 | Safe database migration script validation | `application-prod.properties` (`ddl-auto=validate`) | `[VERIFIED PASS]` |

---

## 4. Razorpay Payments & Subscriptions

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 4.1 | Server-side HMAC-SHA256 signature verification | `RazorpayPaymentHardeningIntegrationTest.testSignatureVerification` | `[VERIFIED PASS]` |
| 4.2 | Raw webhook signature payload validation | `RazorpayPaymentHardeningIntegrationTest.testWebhookSignature` | `[VERIFIED PASS]` |
| 4.3 | Payment callback idempotency (duplicate prevention) | `RazorpayPaymentHardeningIntegrationTest.testDuplicateWebhookIdempotency` | `[VERIFIED PASS]` |
| 4.4 | Frontend callback verification rejection without backend | `RazorpayPaymentHardeningIntegrationTest.testUntrustedFrontendCallback` | `[VERIFIED PASS]` |
| 4.5 | Paid amount matching course price verification | `RazorpayPaymentHardeningIntegrationTest.testAmountMismatch` | `[VERIFIED PASS]` |
| 4.6 | Instructor subscription tier quota enforcement | `SubscriptionService.canCreateCourse` | `[VERIFIED PASS]` |
| 4.7 | Complete financial audit trail logging | `PaymentAuditLogRepository` | `[VERIFIED PASS]` |
| 4.8 | Live Razorpay staging merchant account handshake | Requires live sandbox API keys | `[NOT TESTED (STAGING REQUIRED)]` |

---

## 5. Untrusted Code & SQL Execution Sandbox

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 5.1 | Prohibition of arbitrary execution in main JVM process | Architectural inspection (`CodeExecutionProvider`) | `[VERIFIED PASS]` |
| 5.2 | Circuit breaker & HTTP timeouts for external runner | `ExternalSandboxCodeExecutionProvider` (5s/10s timeouts) | `[VERIFIED PASS]` |
| 5.3 | Bearer token authorization between app and runner | `CodeExecutionConfig` bearer token header | `[VERIFIED PASS]` |
| 5.4 | Disposable read-only SQL sandbox assessment mode | `SqlSandboxEvaluationTest` | `[VERIFIED PASS]` |
| 5.5 | Deployment of external containerized runner daemon | Dedicated Docker container on port 8088 | `[BLOCKED / PENDING INFRASTRUCTURE]` |
| 5.6 | Linux kernel seccomp, cgroups & dropped capabilities | Requires deployment on Linux Docker host | `[BLOCKED / PENDING INFRASTRUCTURE]` |

---

## 6. File Storage, Uploads & Personal Data (PII)

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 6.1 | Multipart upload size limits enforced | `FileUploadSecurityAndDataProtectionIntegrationTest` | `[VERIFIED PASS]` |
| 6.2 | Extension whitelisting and MIME validation | `FileUploadSecurityAndDataProtectionIntegrationTest` | `[VERIFIED PASS]` |
| 6.3 | Server-controlled UUID file naming | `FileStorageService.storeFile` | `[VERIFIED PASS]` |
| 6.4 | Path traversal prevention (`../`, `%2e%2e`, null bytes) | `FileUploadSecurityAndDataProtectionIntegrationTest` | `[VERIFIED PASS]` |
| 6.5 | Private Aadhaar/PII storage outside public web root | `uploads/private/aadhaar/` storage root | `[VERIFIED PASS]` |
| 6.6 | Ownership and admin authorization for PII downloads | `FileUploadSecurityAndDataProtectionIntegrationTest` | `[VERIFIED PASS]` |
| 6.7 | Safe `Content-Disposition: attachment` headers | `FileDownloadController` | `[VERIFIED PASS]` |

---

## 7. Infrastructure, Deployment & Operational Readiness

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 7.1 | Unprivileged non-root container user (`UID 10001`) | `Dockerfile` (`USER skillhub:skillhub`) | `[VERIFIED PASS]` |
| 7.2 | Multi-stage Docker build producing slim JRE image | `Dockerfile` (Temurin 17 JRE Alpine) | `[VERIFIED PASS]` |
| 7.3 | Liveness & Readiness health probe endpoints | `ProductionOperationalReadinessTest.testHealthEndpoints` | `[VERIFIED PASS]` |
| 7.4 | Graceful application shutdown (30s buffer) | `ProductionOperationalReadinessTest.testGracefulShutdownConfig` | `[VERIFIED PASS]` |
| 7.5 | Production logging with MDC Correlation IDs | `ProductionOperationalReadinessTest.testMdcLoggingConfiguration` | `[VERIFIED PASS]` |
| 7.6 | Automated MySQL database backup & restore scripts | `scripts/backup_production.sh`, `restore_production.sh` | `[VERIFIED PASS]` |
| 7.7 | Nginx reverse proxy with TLS 1.3 & HSTS | `nginx/skillhub.conf` | `[VERIFIED PASS]` |
| 7.8 | External `.env` secret injection (no hardcoded credentials)| `.env.example` & `docker-compose.prod.yml` | `[CONDITIONAL PASS]` |

---

## 8. Performance, Concurrency & Load Capacity

| Item # | Verification Check | Automated Test / Verification Reference | Status |
| :--- | :--- | :--- | :--- |
| 8.1 | Static asset HTTP 304 caching (`Cache-Control: 7d`) | `PerformanceAndLoadTestingBenchmarkTest.testStaticAssetCaching`| `[VERIFIED PASS]` |
| 8.2 | Batch rating computation for course catalogs | `PerformanceAndLoadTestingBenchmarkTest.testBatchRatingPerformance`| `[VERIFIED PASS]` |
| 8.3 | Concurrency Stage 1: 5 Virtual Users | 50 reqs, p95=295ms, 0% errors | `[VERIFIED PASS]` |
| 8.4 | Concurrency Stage 2: 10 Virtual Users | 100 reqs, p95=357ms, 0% errors | `[VERIFIED PASS]` |
| 8.5 | Concurrency Stage 3: 25 Virtual Users | 150 reqs, p95=1979ms, 0% errors | `[VERIFIED PASS]` |
| 8.6 | Concurrency Stage 4: 50 Virtual Users | 200 reqs, p95=1099ms, 0% errors | `[VERIFIED PASS]` |
| 8.7 | Concurrency Stage 5: 100 Virtual Users | 200 reqs, p95=973ms, 154.7 req/s, 0% errors | `[VERIFIED PASS]` |
| 8.8 | HikariCP pool sizing (50 connections) without starvation| Verified across 100 concurrent threads | `[VERIFIED PASS]` |

---

## 9. Release Approval Summary

| Scope | Recommendation | Action Required Before Launch |
| :--- | :--- | :--- |
| **SkillHub Web Platform** | **CONDITIONAL GO** | Deploy to Staging, inject `.env` secrets, complete Razorpay test payment, bind TLS certs. |
| **Untrusted Code Sandbox** | **NO-GO** | Keep `code.execution.mode=mock` until `external-runner` container is deployed on isolated host. |
