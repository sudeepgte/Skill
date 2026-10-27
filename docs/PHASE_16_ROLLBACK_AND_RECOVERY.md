# SKILLHUB PRODUCTION HARDENING — PHASE 16
## PRODUCTION CUTOVER ROLLBACK & DISASTER RECOVERY GUIDE

**Document ID:** SKILLHUB-PHASE16-RRG-005  
**Version:** 1.0 (Phase 16 Hardened)  
**Effective Date:** October 27, 2026  
**Audience:** Site Reliability Engineers, DevOps Engineers, On-Call Production Support  

---

## 1. Rollback Policy & Objectives

The primary goal of the SkillHub Rollback & Recovery procedure is to guarantee **zero data loss**, **uncompromised payment records**, and a **maximum Mean Time to Recovery (MTTR) of < 3 minutes** in the event of an unrecoverable failure during a release window.

### Key Recovery Metrics (SLAs):
- **Recovery Time Objective (RTO):** < 3 minutes for application rollback; < 15 minutes for full database restore.
- **Recovery Point Objective (RPO):** 0 seconds for financial transactions; < 5 minutes for user activities via MySQL binlogs.

---

## 2. Pre-Cutover Backup Requirements (Mandatory Step 0)

Before executing any deployment or schema modification, the deployment engineer **must** execute and verify a full consistent snapshot:

```bash
# 1. Database Consistent Backup with Binary Log Position
mysqldump -u root -p \
  --single-transaction \
  --flush-logs \
  --master-data=2 \
  --routines \
  --triggers \
  --databases skillhub > /opt/skillhub/backups/pre_cutover_$(date +%Y%m%d_%H%M%S).sql

# 2. Archive Current Application Artifact
cp /opt/skillhub/app.war /opt/skillhub/backups/app_previous_stable.war

# 3. Archive Uploaded User Files
tar -czf /opt/skillhub/backups/uploads_pre_cutover_$(date +%Y%m%d_%H%M%S).tar.gz /opt/skillhub/uploads/
```

Verify that backup files are non-empty (`ls -lh /opt/skillhub/backups`).

---

## 3. Fast Application Rollback Procedure (< 3 Minutes)

If an abort trigger is reached during cutover, execute the appropriate rollback workflow immediately:

### 3.1 Docker Compose Rollback

```bash
cd /opt/skillhub

# 1. Stop the failing container
docker compose -f docker-compose.prod.yml stop skillhub_app

# 2. Restore previous stable artifact
cp backups/app_previous_stable.war app.war

# 3. Restart the container
docker compose -f docker-compose.prod.yml up -d skillhub_app

# 4. Verify recovery
curl -f -s http://127.0.0.1:8080/api/health | jq .status
```

### 3.2 Native Systemd Service Rollback

```bash
# 1. Stop application service
sudo systemctl stop skillhub

# 2. Revert WAR file to previous stable build
sudo cp /opt/skillhub/backups/app_previous_stable.war /opt/skillhub/app.war

# 3. Start service
sudo systemctl start skillhub

# 4. Confirm health
curl -f -s http://127.0.0.1:8080/api/health | jq .status
```

---

## 4. Database Disaster Recovery Strategy

In the rare event that database schema or data corruption occurs during migration:

### 4.1 Schema Migration Downgrade Policy
Because production uses `spring.jpa.hibernate.ddl-auto=validate`, Hibernate will not auto-alter tables. If a schema change broke application compatibility:

1. Put application into maintenance mode:
   ```bash
   touch /opt/skillhub/nginx/maintenance.flag
   sudo systemctl reload nginx
   ```
2. Restore database from pre-cutover snapshot:
   ```bash
   mysql -u root -p skillhub < /opt/skillhub/backups/pre_cutover_<TIMESTAMP>.sql
   ```
3. Remove maintenance flag and reload Nginx:
   ```bash
   rm /opt/skillhub/nginx/maintenance.flag
   sudo systemctl reload nginx
   ```

---

## 5. Financial Reconciliation & Payment Preservation

If a rollback occurs while users were attempting payments or subscriptions:

1. **Lock Payments Table:**
   Do not truncate or revert the `payments` or `subscription_payments` tables blindly.
2. **Export Transactions Created During Cutover Window:**
   ```sql
   SELECT * FROM payments WHERE payment_date >= '<CUTOVER_START_TIMESTAMP>' INTO OUTFILE '/tmp/cutover_payments.csv';
   SELECT * FROM subscription_payments WHERE payment_date >= '<CUTOVER_START_TIMESTAMP>' INTO OUTFILE '/tmp/cutover_sub_payments.csv';
   ```
3. **Reconcile with Razorpay Merchant Portal:**
   - Run the automated reconciliation script or inspect webhook logs.
   - For every `captured` payment in Razorpay missing from the restored database, manually credit student enrollments or issue instructor subscription days:
   ```sql
   INSERT INTO enrollments (student_id, course_id, enrolled_at) VALUES (<USER_ID>, <COURSE_ID>, NOW());
   ```

---

## 6. Post-Incident Review & Postmortem Requirements

Following any production rollback:
1. Preserve all container logs and system journal logs:
   ```bash
   docker logs skillhub_app > /opt/skillhub/logs/incident_$(date +%Y%m%d).log
   ```
2. Archive JVM heap dump if OutOfMemoryError occurred (`/opt/skillhub/heapdump.hprof`).
3. Convene an incident review within 24 hours comprising the DevOps Engineer, Application Security Engineer, and Lead Developer.
4. Document root cause, timeline, contributing factors, and permanent preventive actions in `docs/postmortems/`.
