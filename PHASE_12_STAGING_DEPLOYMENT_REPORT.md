# SkillHub Platform — Phase 12: Staging Deployment & Launch Readiness Report

**Document Reference:** STAGING-DEPLOY-PHASE12  
**Auditor Roles:** Senior DevOps Engineer, Spring Boot Production Engineer, QA Lead, Application Security Engineer  
**Framework & Runtime:** Spring Boot 3.5.5 | Java 17 (Eclipse Temurin JRE) | Hibernate 6.6.26.Final | Spring Security 6  
**Execution Environment:** Windows 11 (Development/Audit Workstation)  
**Target Deployment Environment:** Ubuntu 22.04 LTS (Docker Compose / Staging Cloud Node)  
**Date:** October 27, 2026  
**Overall Staging Readiness:** **READY FOR STAGING PROVISIONING**  
**Live Production Cutover Recommendation:** **CONDITIONAL GO**  

---

## 1. Executive Summary & Infrastructure Environment Audit

Phase 12 conducted a rigorous pre-deployment audit to prepare the SkillHub application for cloud staging infrastructure deployment. Every locally executable verification was performed using the project's existing build tools and integration test suites.

### Local Infrastructure Availability Assessment
- **Docker Engine / Daemon:** Not installed on the local Windows audit workstation (`CommandNotFoundException`).
- **MySQL Server / Client:** Not installed on the local Windows audit workstation (`CommandNotFoundException`).
- **SMTP Gateway:** Inbound/outbound mail relay is blocked from the local execution sandbox (`UnknownHostException: smtp.gmail.com`).
- **Audit Rule Adherence:** In accordance with strict auditing mandates, cloud staging resources, live URLs, and third-party API handshakes have **NOT** been fabricated. Live multi-container execution is classified as **BLOCKED / PENDING CLOUD STAGING PROVISIONING**.

---

## 2. Production Configuration Invariant Verification

The production configuration file (`src/main/resources/application-prod.properties`) was inspected and verified against operational security invariants:

| Configuration Property | Value / Pattern | Verification Finding | Status |
| :--- | :--- | :--- | :---: |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Strict schema validation; Hibernate is forbidden from altering or dropping production tables at runtime. | **VERIFIED PASS** |
| `spring.jpa.show-sql` | `false` | SQL query logging disabled to prevent PII and query leakage in stdout. | **VERIFIED PASS** |
| `server.error.include-stacktrace` | `never` | Internal stack traces and class names are completely masked from HTTP error responses. | **VERIFIED PASS** |
| `server.shutdown` | `graceful` | Spring Boot will complete inflight HTTP requests (up to 30s) before terminating. | **VERIFIED PASS** |
| `server.servlet.session.cookie.secure` | `true` | Session cookies require HTTPS transmission. | **VERIFIED PASS** |
| `server.servlet.session.cookie.http-only` | `true` | Session cookies cannot be accessed via JavaScript (`document.cookie`). | **VERIFIED PASS** |
| `server.servlet.session.cookie.same-site` | `lax` | Cross-site request forgery protection attribute attached to all session cookies. | **VERIFIED PASS** |
| `logging.pattern.console` | `... [%X{requestId}] ...` | MDC correlation ID included in all console log events for distributed tracing. | **VERIFIED PASS** |
| `skillhub.code-execution.mode` | `production` | Selects `ExternalSandboxCodeExecutionProvider`; default empty runner URL causes safe fast-fail (`EXECUTION_UNAVAILABLE`). | **VERIFIED PASS** |
| Secret Externalization | `${DB_PASSWORD}`, `${JWT_SECRET}` | No hardcoded credentials exist in source code or properties. | **VERIFIED PASS** |

---

## 3. Staging Deployment Architecture

```mermaid
graph TD
    User([Staging Candidate / Tester]) -->|HTTPS Port 443| Nginx[Nginx Reverse Proxy / TLS 1.3]
    Nginx -->|Proxy Pass HTTP 8080| App[SkillHub Spring Boot App Container]
    App -->|HikariCP Port 3306| DB[(MySQL 8.0 Container)]
    App -->|Volume Mount| Storage[/var/skillhub/uploads]
    App -.->|Blocked until runner deployed| Runner[Isolated Code Runner :8088]
    
    subgraph Internal Docker Backend Network
        App
        DB
        Storage
    end
```

### 3.1 Network & Container Hardening
- **Application User:** Unprivileged non-root user `skillhub:skillhub` (`UID 10001`).
- **Database Network:** Internal backend Docker network (`skillhub-backend-net`), inaccessible from external internet traffic.
- **Port Bindings:** MySQL (3306) and Spring Boot (8080) bound to loopback `127.0.0.1` only; public ingress routed strictly through Nginx reverse proxy.

---

## 4. Manual Staging Deployment Execution Sequence

When the dedicated staging VM (Ubuntu 22.04 LTS) is provisioned, execute the following sequence:

### Phase A: Host Preparation
```bash
# 1. Update OS and install Docker & Nginx
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-plugin nginx certbot python3-certbot-nginx

# 2. Add deploy user to docker group
sudo usermod -aG docker $USER

# 3. Create persistent directories
sudo mkdir -p /opt/skillhub /var/skillhub/uploads /var/skillhub/backups
sudo chown -R 10001:10001 /var/skillhub/uploads
```

### Phase B: Environment & Baseline Schema Provisioning
```bash
# 1. Transfer repository files to /opt/skillhub
cd /opt/skillhub

# 2. Populate .env with staging secrets
cp .env.example .env
# Edit .env and supply actual staging credentials:
# DB_PASSWORD, JWT_SECRET, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, MAIL_PASSWORD

# 3. Launch MySQL database container
docker compose -f docker-compose.prod.yml up -d skillhub-mysql

# 4. Wait for database healthcheck
docker compose -f docker-compose.prod.yml ps skillhub-mysql

# 5. Execute baseline schema migration
docker exec -i skillhub-mysql mysql -u skillhub_user -p"YourStagingDbPass" skillhub < src/main/resources/db/migration/V1__skillhub_schema_baseline.sql
```

### Phase C: Application Launch & Probe Verification
```bash
# 1. Build and start application container
docker compose -f docker-compose.prod.yml up -d --build skillhub-app

# 2. Verify container logs
docker compose -f docker-compose.prod.yml logs -f skillhub-app

# 3. Test Liveness and Readiness probes
curl -i http://localhost:8080/api/health/liveness
curl -i http://localhost:8080/api/health/readiness
```

### Phase D: TLS Certificate & Nginx Reverse Proxy
```bash
# 1. Deploy Nginx site configuration
sudo cp nginx/skillhub.conf /etc/nginx/sites-available/staging.skillhub.com.conf
sudo ln -sf /etc/nginx/sites-available/staging.skillhub.com.conf /etc/nginx/sites-enabled/

# 2. Obtain Let's Encrypt certificate
sudo certbot --nginx -d staging.skillhub.com

# 3. Reload Nginx
sudo nginx -t && sudo systemctl reload nginx
```
