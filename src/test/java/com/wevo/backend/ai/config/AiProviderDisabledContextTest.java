package com.wevo.backend.ai.config;

import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.opinion.service.OpinionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "wevo.ai.provider=none")
class AiProviderDisabledContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void keepsNonAiCoreServiceAvailableWithoutProviderGateway() {
        assertThat(applicationContext.getBeansOfType(AiProviderGateway.class)).isEmpty();
        assertThat(applicationContext.getBean(OpinionService.class)).isNotNull();
    }
}
