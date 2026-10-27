-- ============================================================================
-- SkillHub Platform Database Baseline Schema (V1__skillhub_schema_baseline.sql)
-- Target RDBMS: MySQL 8.0+
-- Storage Engine: InnoDB
-- Default Charset: utf8mb4 / Collation: utf8mb4_unicode_ci
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. Users & Core Authentication
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `role` VARCHAR(50) NOT NULL DEFAULT 'STUDENT',
    `headline` VARCHAR(255) NULL,
    `bio` VARCHAR(2000) NULL,
    `profile_image_url` VARCHAR(500) NULL,
    `phone` VARCHAR(50) NULL,
    `skills` VARCHAR(1000) NULL,
    `is_blocked` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_approved` BOOLEAN NOT NULL DEFAULT TRUE,
    `last_login_date` DATETIME NULL,
    `instagram_url` VARCHAR(500) NULL,
    `linkedin_url` VARCHAR(500) NULL,
    `youtube_url` VARCHAR(500) NULL,
    `expertise` VARCHAR(255) NULL,
    `totalxp` INT NOT NULL DEFAULT 0,
    `streak_count` INT NOT NULL DEFAULT 0,
    `last_streak_date` DATETIME NULL,
    `level` INT NOT NULL DEFAULT 1,
    `badges` VARCHAR(2000) NULL,
    `aadhar_card_url` VARCHAR(500) NULL,
    `proof_doc_url` VARCHAR(500) NULL,
    `resume_url` VARCHAR(500) NULL,
    `tenth_cert_url` VARCHAR(500) NULL,
    `pu_cert_url` VARCHAR(500) NULL,
    `ug_cert_url` VARCHAR(500) NULL,
    `pg_cert_url` VARCHAR(500) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    INDEX `idx_user_email` (`email`),
    INDEX `idx_user_role` (`role`),
    INDEX `idx_user_status` (`is_blocked`, `is_approved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Categories
CREATE TABLE IF NOT EXISTS `categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `icon` VARCHAR(255) NULL,
    `parent_id` BIGINT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Courses
CREATE TABLE IF NOT EXISTS `courses` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(500) NULL,
    `course_subtitle` LONGTEXT NULL,
    `description` TEXT NULL,
    `category_id` BIGINT NULL,
    `sub_category` VARCHAR(255) NULL,
    `instructor_id` BIGINT NULL,
    `price` DOUBLE NOT NULL DEFAULT 0.0,
    `sale_price` DOUBLE NULL,
    `image_url` VARCHAR(1000) NULL,
    `language` VARCHAR(100) NULL,
    `level` VARCHAR(100) NULL,
    `is_published` BOOLEAN NOT NULL DEFAULT FALSE,
    `status` VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    `rejection_reason` TEXT NULL,
    `submitted_at` DATETIME NULL,
    `reviewed_at` DATETIME NULL,
    `created_at` DATETIME NULL,
    `last_updated` VARCHAR(100) NULL,
    `exam_updated_at` DATETIME NULL,
    `video_url` VARCHAR(1000) NULL,
    `learning_objectives` TEXT NULL,
    `requirements` TEXT NULL,
    `long_description` LONGTEXT NULL,
    `content_length` VARCHAR(100) NULL,
    `format` VARCHAR(100) NULL,
    `teaching_method` VARCHAR(100) NULL,
    `tags` TEXT NULL,
    `target_audience` TEXT NULL,
    `required_experience` VARCHAR(100) NULL,
    `expected_skills` TEXT NULL,
    `age_range` VARCHAR(100) NULL,
    `accessibility_requirements` TEXT NULL,
    `access_duration` VARCHAR(100) NULL,
    `category_specific_data` LONGTEXT NULL,
    `view_count` BIGINT NOT NULL DEFAULT 0,
    `viewed_user_ids` TEXT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_course_instructor` (`instructor_id`),
    INDEX `idx_course_category` (`category_id`),
    INDEX `idx_course_status` (`status`),
    INDEX `idx_course_published` (`is_published`),
    INDEX `idx_course_created` (`created_at`),
    CONSTRAINT `fk_course_instructor` FOREIGN KEY (`instructor_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_course_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Course Sections & Lectures
CREATE TABLE IF NOT EXISTS `course_sections` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `position` INT NOT NULL DEFAULT 0,
    `course_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_section_course` (`course_id`),
    CONSTRAINT `fk_section_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `lectures` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `video_url` VARCHAR(1000) NULL,
    `duration` VARCHAR(50) NULL,
    `position` INT NOT NULL DEFAULT 0,
    `is_free_preview` BOOLEAN NOT NULL DEFAULT FALSE,
    `section_id` BIGINT NOT NULL,
    `video_type` VARCHAR(50) NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_lecture_section` (`section_id`),
    CONSTRAINT `fk_lecture_section` FOREIGN KEY (`section_id`) REFERENCES `course_sections` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Enrollments (Strict Concurrency & Uniqueness Protection)
CREATE TABLE IF NOT EXISTS `enrollments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `course_id` BIGINT NOT NULL,
    `enrolled_at` DATETIME NOT NULL,
    `progress_percentage` DOUBLE NOT NULL DEFAULT 0,
    `last_played_lecture_id` BIGINT NULL,
    `is_exam_passed` BOOLEAN NOT NULL DEFAULT FALSE,
    `certificate_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enrollment_student_course` (`user_id`, `course_id`),
    INDEX `idx_enrollment_student` (`user_id`),
    INDEX `idx_enrollment_course` (`course_id`),
    INDEX `idx_enrollment_date` (`enrolled_at`),
    CONSTRAINT `fk_enrollment_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_enrollment_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Payments
CREATE TABLE IF NOT EXISTS `payments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `transaction_id` VARCHAR(255) NOT NULL,
    `amount` DOUBLE NOT NULL,
    `payment_method` VARCHAR(100) NOT NULL,
    `status` VARCHAR(50) NOT NULL,
    `payment_date` DATETIME NULL,
    `user_id` BIGINT NOT NULL,
    `course_id` BIGINT NOT NULL,
    `razorpay_order_id` VARCHAR(255) NULL,
    `razorpay_payment_id` VARCHAR(255) NULL,
    `razorpay_signature` VARCHAR(500) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_txn` (`transaction_id`),
    UNIQUE KEY `uk_payment_rzp_order` (`razorpay_order_id`),
    INDEX `idx_payment_user` (`user_id`),
    INDEX `idx_payment_course` (`course_id`),
    INDEX `idx_payment_status` (`status`),
    INDEX `idx_payment_date` (`payment_date`),
    INDEX `idx_payment_razorpay_pay` (`razorpay_payment_id`),
    CONSTRAINT `fk_payment_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_payment_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Instructor Subscription Plans, Subscriptions & Payments
CREATE TABLE IF NOT EXISTS `subscription_plans` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) NULL,
    `monthly_price` DOUBLE NOT NULL DEFAULT 0.0,
    `course_limit` INT NULL,
    `features` VARCHAR(2000) NULL,
    `is_custom` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_popular` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `sort_order` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_plan_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `instructor_subscriptions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `instructor_id` BIGINT NOT NULL,
    `plan_id` BIGINT NULL,
    `plan_name` VARCHAR(255) NOT NULL,
    `course_limit` INT NOT NULL DEFAULT 1,
    `monthly_price` DOUBLE NOT NULL DEFAULT 0.0,
    `status` VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    `billing_interval` VARCHAR(50) NOT NULL DEFAULT 'MONTHLY',
    `start_date` DATETIME NOT NULL,
    `end_date` DATETIME NOT NULL,
    `auto_renew` BOOLEAN NOT NULL DEFAULT TRUE,
    `cancelled_at` DATETIME NULL,
    `cancellation_reason` VARCHAR(1000) NULL,
    `created_at` DATETIME NULL,
    `updated_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_ins_sub_instructor` (`instructor_id`),
    INDEX `idx_ins_sub_status` (`status`),
    INDEX `idx_ins_sub_dates` (`start_date`, `end_date`),
    CONSTRAINT `fk_ins_sub_user` FOREIGN KEY (`instructor_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ins_sub_plan` FOREIGN KEY (`plan_id`) REFERENCES `subscription_plans` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `subscription_payments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `subscription_id` BIGINT NOT NULL,
    `instructor_id` BIGINT NOT NULL,
    `amount` DOUBLE NOT NULL,
    `transaction_id` VARCHAR(255) NOT NULL,
    `payment_method` VARCHAR(100) NOT NULL,
    `payment_status` VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    `razorpay_order_id` VARCHAR(255) NULL,
    `razorpay_payment_id` VARCHAR(255) NULL,
    `razorpay_signature` VARCHAR(500) NULL,
    `notes` VARCHAR(1000) NULL,
    `payment_date` DATETIME NULL,
    `created_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_pay_txn` (`transaction_id`),
    UNIQUE KEY `uk_sub_pay_rzp_order` (`razorpay_order_id`),
    INDEX `idx_sub_payment_sub` (`subscription_id`),
    INDEX `idx_sub_payment_ins` (`instructor_id`),
    INDEX `idx_sub_payment_status` (`payment_status`),
    INDEX `idx_sub_payment_date` (`payment_date`),
    CONSTRAINT `fk_sub_pay_sub` FOREIGN KEY (`subscription_id`) REFERENCES `instructor_subscriptions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sub_pay_ins` FOREIGN KEY (`instructor_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Professional Certification & Exams
CREATE TABLE IF NOT EXISTS `professional_exams` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `description` LONGTEXT NULL,
    `track` VARCHAR(50) NOT NULL,
    `difficulty` VARCHAR(50) NOT NULL,
    `duration_minutes` INT NOT NULL DEFAULT 90,
    `passing_percentage` INT NOT NULL DEFAULT 70,
    `maximum_attempts` INT NOT NULL DEFAULT 3,
    `is_application_required` BOOLEAN NOT NULL DEFAULT FALSE,
    `application_fee` DOUBLE NOT NULL DEFAULT 0.0,
    `is_published` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME NULL,
    `updated_at` DATETIME NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `professional_certificates` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `certificate_id` VARCHAR(100) NOT NULL,
    `result_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `exam_id` BIGINT NOT NULL,
    `track` VARCHAR(50) NOT NULL,
    `issue_date` DATE NOT NULL,
    `expiry_date` DATE NULL,
    `status` VARCHAR(50) NOT NULL,
    `verification_token` VARCHAR(150) NOT NULL,
    `verification_url` VARCHAR(500) NULL,
    `created_at` DATETIME NULL,
    `updated_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prof_cert_cid` (`certificate_id`),
    UNIQUE KEY `uk_prof_cert_token` (`verification_token`),
    UNIQUE KEY `uk_prof_cert_result` (`result_id`),
    INDEX `idx_prof_cert_user` (`user_id`),
    INDEX `idx_prof_cert_exam` (`exam_id`),
    INDEX `idx_prof_cert_status` (`status`),
    CONSTRAINT `fk_prof_cert_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_prof_cert_exam` FOREIGN KEY (`exam_id`) REFERENCES `professional_exams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
