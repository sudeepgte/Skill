# SkillHub Platform — Phase 11: Staging Deployment Runbook

**Document Reference:** RUNBOOK-PHASE11-STAGING  
**Target Environments:** Staging / Pre-Production Canary  
**Stack:** Docker Compose / Linux / MySQL 8.0 / Nginx / Spring Boot 3.5.5  
**Version:** 1.0.0-PROD  

---

## 1. Prerequisites & Environment Configuration

### 1.1 Host Environment Requirements
- **OS:** Ubuntu 22.04 LTS or Debian 12 (Dedicated VM or Cloud Compute Instance)
- **CPU / RAM:** Minimum 4 vCPU, 8 GB RAM (16 GB recommended for MySQL + App + Nginx)
- **Disk:** 50 GB SSD with mount point `/var/skillhub`
- **Docker Engine:** v24.0+ with Docker Compose v2.20+
- **Network Ingress:** Port 80 (HTTP) and Port 443 (HTTPS) open to public; Port 8080 and Port 3306 bound to `127.0.0.1` only.

### 1.2 Environment File Setup (`.env`)
Create `/opt/skillhub/.env` from `.env.example`. Replace all placeholders with secure random strings:

```bash
# Server & Port
PORT=8080
SPRING_PROFILES_ACTIVE=prod

# Database Credentials
MYSQL_ROOT_PASSWORD=GenerateStrong32CharRootPass!
MYSQL_DATABASE=skillhub
DB_USERNAME=skillhub_user
DB_PASSWORD=GenerateStrong32CharAppPass!
DB_URL=jdbc:mysql://skillhub-mysql:3306/skillhub?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true

# Database Pool Settings
DB_POOL_MAX_SIZE=20
DB_POOL_MIN_IDLE=5

# JWT Secret (Min 64 alphanumeric characters)
JWT_SECRET=c2tpbGxodWJfcHJvZHVjdGlvbl9zZWN1cmVfc3Ryb25nX2p3dF9rZXlfMjAyNg==
JWT_EXPIRATION_MS=86400000

# Razorpay Test Mode Credentials
RAZORPAY_KEY_ID=rzp_test_YourTestKeyIdHere
RAZORPAY_KEY_SECRET=YourRazorpayTestSecretHere
RAZORPAY_WEBHOOK_SECRET=YourRazorpayWebhookSecretHere

# Mail Server Configuration (AWS SES / SendGrid Staging)
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=SG.YourActualSendgridApiKeyHere

# Code Execution Setting (Kept in production mode without external runner URL)
CODE_EXECUTION_MODE=production
CODING_RUNNER_URL=
CODING_RUNNER_SECRET=

# File Storage Root
UPLOAD_DIR=/var/skillhub/uploads
```

---

## 2. Database Initialization & Schema Validation

SkillHub enforces `spring.jpa.hibernate.ddl-auto=validate` in production, meaning Hibernate will NOT alter or generate tables on startup. The schema must be initialized beforehand.

### Step 2.1: Start MySQL Container
```bash
cd /opt/skillhub
docker compose -f docker-compose.prod.yml up -d skillhub-mysql
```

### Step 2.2: Verify MySQL Health
```bash
docker compose -f docker-compose.prod.yml ps skillhub-mysql
# Ensure status reports: (healthy)
```

### Step 2.3: Execute Baseline Schema Migration
If initializing a fresh database, execute the baseline DDL script:
```bash
docker exec -i skillhub-mysql mysql -u skillhub_user -p"GenerateStrong32CharAppPass!" skillhub < schema_baseline.sql
```

---

## 3. Application Artifact Build & Launch

### Step 3.1: Build Executable WAR Artifact
```bash
# On build server or local deployment machine:
./mvnw clean package -DskipTests
# Verify artifact creation: target/skillhub-0.0.1-SNAPSHOT.war
```

### Step 3.2: Transfer Artifact & Build Container
```bash
# Transfer target/skillhub-0.0.1-SNAPSHOT.war to /opt/skillhub/target/
cd /opt/skillhub
docker compose -f docker-compose.prod.yml build skillhub-app
```

### Step 3.3: Launch Staging Application Container
```bash
docker compose -f docker-compose.prod.yml up -d skillhub-app
```

---

## 4. Verification & Smoke Test Checklist

### 4.1 Health & Readiness Probes
Execute curl against the container:
```bash
# 1. Liveness Probe (Should return HTTP 200 {"status":"UP"})
curl -i http://localhost:8080/api/health/liveness

# 2. Readiness Probe (Verifies DB connection pool and disk space)
curl -i http://localhost:8080/api/health/readiness
```

### 4.2 TLS Certificate & Nginx Reverse Proxy
Install Nginx configuration:
```bash
sudo cp nginx/skillhub.conf /etc/nginx/sites-available/skillhub.conf
sudo ln -sf /etc/nginx/sites-available/skillhub.conf /etc/nginx/sites-enabled/

# Obtain Let's Encrypt certificate:
sudo certbot --nginx -d staging.skillhub.com

# Test Nginx syntax and reload:
sudo nginx -t && sudo systemctl reload nginx
```

Verify TLS headers:
```bash
curl -I https://staging.skillhub.com/
# Confirm presence of:
# Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
# X-Content-Type-Options: nosniff
# X-Frame-Options: DENY
```

### 4.3 Staging Email (SMTP) Delivery Verification
1. Open `https://staging.skillhub.com/forgot-password`.
2. Enter a test email address (`staging-test@skillhub.com`).
3. Inspect `docker logs -f skillhub-app`:
   - Confirm log line: `[requestId] INFO ... Password reset email dispatched successfully`.
4. Verify email arrival in the test mailbox.

### 4.4 Razorpay Test-Mode Payment Verification
1. Log into staging as a test student.
2. Select a paid test course ($₹1,000$).
3. Click **Enroll Now** -> Razorpay modal opens.
4. Input Razorpay test card (`4111 1111 1111 1111`, any future date, CVV 123).
5. Submit OTP `123456`.
6. Confirm redirection to success page:
   - Order marked `PAID`.
   - Student enrolled in course.
   - Entry created in `payment_audit_logs`.

---

## 5. Rollback Procedure

If any critical failure occurs during deployment (e.g., startup failure, database timeout, broken checkout):

```bash
# Step 1: Immediately revert to previous Docker image
docker tag skillhub-app:previous skillhub-app:latest
docker compose -f docker-compose.prod.yml up -d --force-recreate skillhub-app

# Step 2: If database schema was corrupted, restore from pre-deployment backup
cd /opt/skillhub
./scripts/restore_production.sh /opt/skillhub/backups/pre_deploy_backup.sql.gz

# Step 3: Verify system recovery
curl -i http://localhost:8080/api/health/readiness
```

---

## 6. Backup & Recovery Verification

Execute backup script to ensure cold-standby snapshots function properly:
```bash
cd /opt/skillhub
chmod +x scripts/backup_production.sh scripts/restore_production.sh
./scripts/backup_production.sh

# Verify output in /var/skillhub/backups/
ls -lh /var/skillhub/backups/
```
