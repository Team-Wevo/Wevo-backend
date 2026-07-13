package com.wevo.backend.ai.client;

/**
 * 서비스 계층과 Claude 제공자 구현을 분리하는 경계.
 */
public interface ClaudeGateway {

    ClaudeResponse generate(ClaudeRequest request);
}
