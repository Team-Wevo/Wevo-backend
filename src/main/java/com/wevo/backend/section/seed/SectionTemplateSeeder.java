package com.wevo.backend.section.seed;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 결과물 유형별 고정 섹션 baseline 을 시드한다. (제품 정책서 §2.3, §2.4.3/§2.4.4)
 *
 * <p>멱등: 이미 시드돼 있으면(테이블 비어있지 않으면) 아무것도 하지 않는다.
 * <p>필드 매핑(ERD 준수 — 질문 전용 컬럼 없음): {@code description} = 기본 핵심 질문,
 * {@code guideText} = 작성 가이드. (섹션 목적/의도는 title 로 대변)
 */
@Component
public class SectionTemplateSeeder implements ApplicationRunner {

    private final SectionTemplateRepository sectionTemplateRepository;

    public SectionTemplateSeeder(SectionTemplateRepository sectionTemplateRepository) {
        this.sectionTemplateRepository = sectionTemplateRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sectionTemplateRepository.count() > 0) {
            return;
        }
        sectionTemplateRepository.saveAll(baselineTemplates());
    }

    private List<SectionTemplate> baselineTemplates() {
        return List.of(
                // ── 발표 구성안 (PRESENTATION) — §2.4.3 ──
                template(OutputType.PRESENTATION, 1, "problem-definition", "문제 정의",
                        "지금 어떤 불편·문제가 있나요? 왜 중요한가요? 기존 방식은 왜 부족한가요?",
                        "추상적 서술보다 구체적인 상황·사례로 작성하세요."),
                template(OutputType.PRESENTATION, 2, "target-user", "타겟 사용자",
                        "주요 사용자는 누구인가요? 어떤 상황에서 문제를 겪나요? 가장 먼저 공략할 사용자는 누구인가요?",
                        "'모두'가 아니라 좁고 뾰족하게 정의하세요."),
                template(OutputType.PRESENTATION, 3, "solution-direction", "해결 방향",
                        "핵심 해결 아이디어는 무엇인가요? 기존과 접근이 어떻게 다른가요? 사용자는 어떤 변화를 경험하나요?",
                        "기능을 나열하기 전에 방향·컨셉을 한 문장으로 정리하세요."),
                template(OutputType.PRESENTATION, 4, "core-features", "핵심 기능",
                        "반드시 필요한 핵심 기능은 무엇인가요? 각 기능이 어떤 문제를 푸나요? 가장 먼저 보여줄 기능은 무엇인가요?",
                        "3개 내외로 우선순위를 정하세요."),
                template(OutputType.PRESENTATION, 5, "differentiation", "차별점",
                        "기존·경쟁과 다른 점은 무엇인가요? 왜 우리가 더 잘하나요? 쉽게 따라오지 못하는 이유는 무엇인가요?",
                        "'더 좋다'보다 '무엇이 다른가'로 설명하세요."),
                template(OutputType.PRESENTATION, 6, "expected-impact", "기대 효과",
                        "실현되면 무엇이 좋아지나요? 어떤 변화가 생기나요? 마지막으로 강조할 포인트는 무엇인가요?",
                        "가능하면 수치나 before/after로 표현하세요."),

                // ── 제안서 (PROPOSAL) — §2.4.4 ──
                template(OutputType.PROPOSAL, 1, "proposal-background", "제안 배경",
                        "어떤 상황에서 시작됐나요? 최근 어떤 변화·요구가 있었나요? 왜 지금인가요?",
                        "트렌드·데이터로 '지금'의 당위를 보여주세요."),
                template(OutputType.PROPOSAL, 2, "problem-necessity", "문제 및 필요성",
                        "가장 큰 문제는 무엇인가요? 방치하면 어떤 손실·불편이 있나요? 누구에게 중요한가요?",
                        "문제의 크기·심각성을 근거로 제시하세요."),
                template(OutputType.PROPOSAL, 3, "goal-scope", "목표 및 제안 범위",
                        "달성할 목표는 무엇인가요? 포함/제외 범위는 어디까지인가요? 성공 판단 기준은 무엇인가요?",
                        "'안 하는 것'을 명시해 범위 과대를 방지하세요."),
                template(OutputType.PROPOSAL, 4, "proposal-content", "제안 내용",
                        "핵심 제안은 무엇인가요? 누구를 위한 것이며(타겟), 기존보다 나은 점은 무엇인가요(차별점)? 주요 기능·활동은 무엇인가요?",
                        "타겟·차별점을 함께 녹여 서술하세요."),
                template(OutputType.PROPOSAL, 5, "execution-plan", "실행 방안",
                        "어떤 순서로 실행하나요? 필요한 인력·도구·일정·역할은 무엇인가요? 먼저 할 일은 무엇인가요?",
                        "현실적 단계·일정으로 실행 가능성을 입증하세요."),
                template(OutputType.PROPOSAL, 6, "expected-impact", "기대 효과",
                        "실행되면 어떤 효과가 있나요? 어떻게 측정하나요? 성공 기준은 무엇인가요?",
                        "검증 기준은 가능한 범위에서 제시하세요.")
        );
    }

    private SectionTemplate template(OutputType resultType, int orderNo, String sectionKey,
                                     String title, String keyQuestion, String guideText) {
        return SectionTemplate.builder()
                .resultType(resultType)
                .sectionKey(sectionKey)
                .title(title)
                .description(keyQuestion) // ERD: 질문 전용 컬럼이 없어 description 에 핵심 질문을 담는다
                .guideText(guideText)
                .orderNo(orderNo)
                .isRequired(true)
                .build();
    }
}
