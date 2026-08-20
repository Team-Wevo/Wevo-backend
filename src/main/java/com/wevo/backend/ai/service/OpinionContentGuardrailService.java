package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.OpinionGuardrailContext;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import com.wevo.backend.ai.exception.AiGuardrailExceededException;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 의견 제출 시 본문을 AI 가드레일로 검사하는 <b>opinion 도메인용 공개 진입점</b>.
 * 알아들을 수 없는 글(GIBBERISH)·딴 주제(OFF_TOPIC)는 제출을 <b>하드 리젝</b>한다.
 *
 * <p>opinion 이 AI 도메인 구현을 직접 참조하지 않도록 이 서비스만 공개한다. (CLAUDE.md §6)
 * 판정은 동기라 제출을 블록한다 — 하드 리젝이 성립하려면 판정을 기다려야 한다.
 *
 * <p><b>가용성 정책 (fail-open — 팀 결정, #329)</b>: 의견 가드레일은 품질 검사이지 보안 게이트가
 * 아니다. 판정을 <b>할 수 없을 때</b>(AI 미구성, 제공자 장애·타임아웃·한도 소진·AI 킬스위치·서킷
 * open·검증 실패)는 제출을 막지 않고 가드레일을 <b>건너뛴다</b> — 제출(사용자 핵심 행위)을 AI 가용성에
 * 종속시켜 provider 장애 시 서비스 핵심 플로우가 통째로 멈추는 것을 막는다. 오직 판정이 <b>성립하고
 * 내용이 거부</b>된 경우(GIBBERISH/OFF_TOPIC)만 하드 리젝한다({@link ErrorCode#OPINION_CONTENT_REJECTED}).
 */
@Service
public class OpinionContentGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(OpinionContentGuardrailService.class);

    private final ObjectProvider<OpinionGuardrailClassifier> classifierProvider;
    private final UserService userService;

    public OpinionContentGuardrailService(
            ObjectProvider<OpinionGuardrailClassifier> classifierProvider,
            UserService userService
    ) {
        this.classifierProvider = classifierProvider;
        this.userService = userService;
    }

    /**
     * 제출 직전 본문을 검사한다. 통과가 아니면 예외를 던져 제출을 막는다.
     *
     * @throws BusinessException 판정이 성립하고 내용이 거부된 경우에만
     *                           {@link ErrorCode#OPINION_CONTENT_REJECTED}(사유는 {@code content}
     *                           필드에 GIBBERISH/OFF_TOPIC). 판정 불가는 예외 없이 제출을 허용한다(fail-open).
     */
    public void requireAcceptable(ProjectSection section, Long userId, String content) {
        OpinionGuardrailClassifier classifier = classifierProvider.getIfAvailable();
        if (classifier == null) {
            log.debug("AI 미구성으로 의견 가드레일을 건너뜁니다. sectionId={}", section.getId());
            return;
        }

        OpinionGuardrailContext context = new OpinionGuardrailContext(
                section.getId(), section.getTitle(), guideOf(section), content);
        User requestedBy = userService.getUserReference(userId);

        OpinionGuardrailVerdict verdict;
        try {
            verdict = classifier.classify(context, section.getProject(), section, requestedBy);
        } catch (AiGuardrailExceededException exception) {
            // AI 요청·비용 한도 소진은 콘텐츠 문제가 아니라 시스템 부하 조건이다. 의견 가드레일은 보안이
            // 아니라 품질 검사이므로, 이때는 제출(사용자 핵심 행위)을 막지 않고 검사를 건너뛴다(fail-open).
            // 공통 quota 가 다른 AI 기능에 소진돼도 의견 제출이 O006 으로 차단되지 않게 한다.
            log.info("AI 사용 한도 소진으로 의견 가드레일을 건너뜁니다(fail-open). sectionId={}, code={}",
                    section.getId(), exception.getErrorCode().getCode());
            return;
        } catch (RuntimeException exception) {
            // 판정 자체가 불가능한 경우(제공자 장애·타임아웃·AI 킬스위치·서킷 open·검증 실패 등)는 fail-open
            // — 제출(사용자 핵심 행위)을 막지 않고 가드레일만 건너뛴다. 제출을 AI 가용성에 종속시키면
            // provider 장애 시 서비스 핵심 플로우가 통째로 멈춘다(#329). 내용 거부(아래 verdict 판정)만
            // 하드 리젝한다. 원문·개인정보는 로그에 남기지 않는다(§7).
            log.warn("의견 가드레일 판정 실패로 검사를 건너뜁니다(fail-open). sectionId={}, exceptionType={}",
                    section.getId(), exception.getClass().getSimpleName());
            return;
        }

        if (!verdict.acceptable()) {
            throw new BusinessException(
                    ErrorCode.OPINION_CONTENT_REJECTED,
                    List.of(new FieldError("content", verdict.reason())));
        }
    }

    private String guideOf(ProjectSection section) {
        SectionTemplate template = section.getTemplate();
        return template == null ? null : template.getGuideText();
    }
}
