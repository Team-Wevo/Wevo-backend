package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.OpinionGuardrailContext;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
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
 * <p><b>가용성 정책 (fail-closed, 팀 결정)</b>:
 * <ul>
 *   <li>AI 가 <b>아예 구성되지 않은</b> 환경(local 등, classifier 빈 부재)에서는 판정할 AI 자체가
 *       없으므로 가드레일을 <b>건너뛴다</b>(제출 허용) — 판정 불가로 전 제출을 막지 않는다.</li>
 *   <li>AI 는 구성됐으나 <b>판정에 실패</b>(장애·타임아웃·quota 초과·검증 실패)하면 <b>제출을 막고</b>
 *       재시도를 안내한다({@link ErrorCode#OPINION_GUARDRAIL_UNAVAILABLE}). 나쁜 입력이 검증을
 *       우회해 저장되는 것을 허용하지 않는다.</li>
 * </ul>
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
     * @throws BusinessException 내용 거부 시 {@link ErrorCode#OPINION_CONTENT_REJECTED}(사유는
     *                           {@code content} 필드에 GIBBERISH/OFF_TOPIC), 판정 실패 시
     *                           {@link ErrorCode#OPINION_GUARDRAIL_UNAVAILABLE}
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
        } catch (RuntimeException exception) {
            // AI 는 구성됐으나 판정 실패 — fail-closed. 원문·개인정보는 로그에 남기지 않는다(§7).
            log.warn("의견 가드레일 판정 실패로 제출을 차단합니다(fail-closed). sectionId={}, exceptionType={}",
                    section.getId(), exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OPINION_GUARDRAIL_UNAVAILABLE);
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
