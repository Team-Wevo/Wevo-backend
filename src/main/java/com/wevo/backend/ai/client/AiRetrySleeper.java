package com.wevo.backend.ai.client;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiRetrySleeper {

    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }
}
