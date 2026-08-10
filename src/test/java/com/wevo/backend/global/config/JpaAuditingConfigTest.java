package com.wevo.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

class JpaAuditingConfigTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void auditingDateTimeProviderUsesKstRegardlessOfJvmDefaultTimeZone() {
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            DateTimeProvider provider = new JpaAuditingConfig().kstDateTimeProvider();
            LocalDateTime before = LocalDateTime.now(KST);

            LocalDateTime actual = LocalDateTime.from(provider.getNow().orElseThrow());

            LocalDateTime after = LocalDateTime.now(KST);
            assertThat(actual).isBetween(before, after);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void jpaAuditingUsesKstDateTimeProvider() {
        EnableJpaAuditing annotation = JpaAuditingConfig.class.getAnnotation(EnableJpaAuditing.class);

        assertThat(annotation.dateTimeProviderRef()).isEqualTo("kstDateTimeProvider");
    }
}
