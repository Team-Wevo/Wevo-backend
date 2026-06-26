package com.wevo.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 createdAt/updatedAt 자동 기록을 위한 JPA Auditing 활성화.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
