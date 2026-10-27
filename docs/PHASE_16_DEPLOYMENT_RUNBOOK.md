# SKILLHUB PRODUCTION HARDENING — PHASE 16
## STAGING & PRODUCTION DEPLOYMENT RUNBOOK

**Document ID:** SKILLHUB-PHASE16-DRB-002  
**Target Release Artifact:** `target/skillhub-0.0.1-SNAPSHOT.war` (SHA-256 verified)  
**Target Platform:** Linux Ubuntu 22.04 LTS / 24.04 LTS (Docker Compose or Native Systemd)  
**Database:** MySQL Server 8.0+  
**Target JVM:** OpenJDK 17 or OpenJDK 21 LTS  

---

## 1. Pre-Deployment Environment Checklist

Before initiating the deployment, the Site Reliability Engineer (SRE) or DevOps Engineer must verify that all required environment variables and infrastructure components are configured.

### Required Environment Variables

Create a secure `/opt/skillhub/.env` file with restrictive permissions (`chmod 600 /opt/skillhub/.env`):

```bash
# --- Core Application Configuration ---
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# --- Database Credentials (MySQL 8.0) ---
DATABASE_URL=jdbc:mysql://127.0.0.1:3306/skillhub?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=skillhub_app
DB_PASSWORD=<REDACTED_SECURE_MYSQL_PASSWORD>

# --- Security & JWT ---
JWT_SECRET=<REDACTED_MINIMUM_64_CHARACTER_BASE64_KEY>
JWT_EXPIRATION_MS=86400000

# --- Payment Gateway (Razorpay) ---
RAZORPAY_KEY_ID=rzp_live_<REDACTED_KEY_ID>
RAZORPAY_KEY_SECRET=<REDACTED_SECRET>
RAZORPAY_WEBHOOK_SECRET=<REDACTED_WEBHOOK_SECRET>

# --- Mail Relay (SMTP) ---
SPRING_MAIL_HOST=smtp.sendgrid.net
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=apikey
SPRING_MAIL_PASSWORD=<REDACTED_SENDGRID_KEY>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true

# --- Code Execution Runner (Safety Lock) ---
SKILLHUB_CODE_EXECUTION_MODE=production
SKILLHUB_CODE_EXECUTION_RUNNER_URL=
SKILLHUB_CODE_EXECUTION_RUNNER_SECRET=

# --- File Storage Directory ---
FILE_UPLOAD_DIR=/opt/skillhub/uploads
```

> [!WARNING]
> Ensure `SKILLHUB_CODE_EXECUTION_RUNNER_URL` remains empty until a dedicated, network-isolated runner instance with gVisor/nsjail containment is deployed. This prevents any arbitrary code from executing on the application server.

---

## 2. Database Baseline Schema Execution

The production profile configures `spring.jpa.hibernate.ddl-auto=validate`. Hibernate will strictly validate that database entities match the schema without modifying the database at runtime.

### Step 2.1: Execute Schema Baseline

On the MySQL 8.0 staging/production database instance, run the baseline DDL script:

```bash
# Connect to MySQL instance and apply baseline
mysql -u root -p -h <MYSQL_HOST> skillhub < src/main/resources/db/migration/V1__skillhub_schema_baseline.sql
```

### Step 2.2: Verify Table Creation & Collation

Execute the following verification query:

```sql
SELECT table_name, table_collation, table_rows 
FROM information_schema.tables 
WHERE table_schema = 'skillhub' 
ORDER BY table_name;
```

**Verification Criteria:**
- 42 core tables exist.
- Collation for all text tables is `utf8mb4_unicode_ci` or `utf8mb4_0900_ai_ci`.
- Primary keys and indexes match `V1__skillhub_schema_baseline.sql`.

---

## 3. Staging Deployment Procedure (Docker Compose)

### Step 3.1: Transfer Artifacts to Staging Host

```bash
# From build workstation:
scp target/skillhub-0.0.1-SNAPSHOT.war deploy@staging.skillhub.com:/opt/skillhub/app.war
scp Dockerfile docker-compose.prod.yml deploy@staging.skillhub.com:/opt/skillhub/
scp nginx/nginx.conf deploy@staging.skillhub.com:/opt/skillhub/nginx/
```

### Step 3.2: Launch Containers

```bash
cd /opt/skillhub
docker compose -f docker-compose.prod.yml down --remove-orphans
docker compose -f docker-compose.prod.yml up --build -d
```

### Step 3.3: Verify Container Health

```bash
docker compose -f docker-compose.prod.yml ps
docker logs -f skillhub_app --tail 100
```

---

## 4. Alternative: Native Systemd Service Deployment

If deploying directly on an Ubuntu VM without Docker:

### Step 4.1: Configure Systemd Unit (`/etc/systemd/system/skillhub.service`)

```ini
[Unit]
Description=SkillHub Production Service
After=network.target mysql.service

[Service]
User=skillhub
Group=skillhub
WorkingDirectory=/opt/skillhub
EnvironmentFile=/opt/skillhub/.env
ExecStart=/usr/bin/java -Xms1024m -Xmx2048m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/skillhub/heapdump.hprof -jar /opt/skillhub/app.war
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### Step 4.2: Enable and Start Service

```bash
sudo systemctl daemon-reload
sudo systemctl enable skillhub
sudo systemctl restart skillhub
sudo systemctl status skillhub
```

---

## 5. Post-Deployment Verification & Smoke Testing

Immediately after starting the service, execute the automated smoke test script:

### Step 5.1: Health Endpoint Telemetry Check

```bash
curl -f -s http://127.0.0.1:8080/api/health | jq .
```

**Expected JSON Response:**
```json
{
  "status": "UP",
  "system": "SkillHub E-Learning Platform",
  "timestamp": "2026-10-27T...",
  "jvm": {
    "totalMemoryMb": ...,
    "freeMemoryMb": ...,
    "usedMemoryMb": ...,
    "activeThreads": ...
  },
  "datasource": {
    "status": "UP",
    "activeConnections": 0,
    "idleConnections": 10,
    "totalConnections": 10,
    "threadsAwaitingConnection": 0
  }
}
```

### Step 5.2: End-to-End Functional Smoke Test Sequence

1. **Public Catalog Browsing:**
   - Execute: `curl -I https://staging.skillhub.com/courses`
   - Expect: `HTTP/2 200 OK`
2. **Admin Authentication:**
   - Navigate to `/login`, authenticate with staging admin credentials.
   - Verify `/admin/dashboard` renders system metrics.
3. **Student Course Checkout (Mock/Sandbox Mode):**
   - Log in as test student, open course `/course/1`.
   - Click "Buy Now", simulate test payment completion.
   - Verify immediate redirect to `/user/course/1/learn`.
4. **Examination Engine Lifecycle:**
   - Open approved exam application, start attempt.
   - Submit answers for MCQ and SQL sandbox questions.
   - Verify score calculation and instant certificate issuance.
5. **Video Upload & Playback Security:**
   - Upload sample `.mp4` video via `/api/upload-video`.
   - Verify that unpaid students requesting `/user/course/{id}/learn` receive HTTP 302 redirect to checkout.

---

## 6. Abort Triggers & Emergency Halt Protocol

If any of the following conditions occur during the cutover window, **HALT DEPLOYMENT IMMEDIATELY** and trigger the rollback protocol in [docs/PHASE_16_ROLLBACK_AND_RECOVERY.md](file:///c:/Users/kisho/OneDrive/Desktop/New%20folder/Skill/docs/PHASE_16_ROLLBACK_AND_RECOVERY.md):

1. **Health Check Failure:** `/api/health` returns HTTP 500 or status `"DOWN"`.
2. **Database Schema Mismatch:** Hibernate startup logs `SchemaManagementException: Schema-validation: missing table / missing column`.
3. **Elevated Error Rate:** HTTP 5xx responses exceed 1.0% over a 5-minute sampling window.
4. **Connection Pool Starvation:** `threadsAwaitingConnection > 5` for longer than 30 consecutive seconds.
5. **Unauthorized Access Regressions:** Unpaid student successfully renders `/user/course/{id}/learn` for a paid course.
