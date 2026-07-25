package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 PostgreSQL에서 {@link SynthesisRequestService#requestSynthesis} 를 호출해 트랜잭션·잠금
 * 상호작용을 검증한다. 특히 신규 작업 생성이 섹션 FK 잠금과 self-wait에 빠지지 않는지 확인한다
 * (요청 경로가 섹션을 {@code FOR UPDATE}로 잠그면 AI 작업 INSERT의 FK {@code KEY SHARE}와 충돌).
 *
 * <p>실행 폴러는 꺼서(dispatch-enabled=false) 작업이 QUEUED로 남게 하고, provider도 none이라
 * 실제 AI는 호출되지 않는다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "wevo.ai.jobs.dispatch-enabled=false"
})
@Import(PostgresTestContainerConfig.class)
class SynthesisRequestServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 14, 0);

    @Autowired private SynthesisRequestService synthesisRequestService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectSectionRepository projectSectionRepository;
    @Autowired private OpinionRepository opinionRepository;
    @Autowired private AiJobRepository aiJobRepository;

    private User owner;
    private User member;
    private Project project;
    private ProjectSection synthesizingSection;

    @BeforeEach
    void setUp() {
        deleteAllFixtures();

        owner = userRepository.save(User.builder()
                .name("윤호").email("owner@wevo.com").status(UserStatus.ACTIVE).build());
        member = userRepository.save(User.builder()
                .name("팀원").email("member@wevo.com").status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder()
                .owner(owner).title("정리 실행 테스트").resultType(OutputType.PROPOSAL)
                .audience("심사위원").status(ProjectStatus.ACTIVE).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(owner).role(ProjectMemberRole.OWNER).joinedAt(NOW).build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(member).role(ProjectMemberRole.MEMBER).joinedAt(NOW).build());
        synthesizingSection = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING).build());
        Opinion opinion = Opinion.builder()
                .projectSection(synthesizingSection).author(member)
                .content("대학생 팀의 협업 문제를 우선 해결해야 합니다.")
                .status(OpinionStatus.DRAFT).build();
        opinion.submit(NOW.minusHours(1));
        opinionRepository.save(opinion);
    }

    /**
     * 남긴 데이터를 <b>끝날 때도 지운다</b> — 롤백되지 않는 통합 테스트라 의견 행이 남으면
     * 섹션만 지우는 다른 통합 테스트의 정리가 {@code opinions → project_sections} FK로 실패한다.
     */
    @AfterEach
    void tearDown() {
        deleteAllFixtures();
    }

    /** 자식 → 부모 순으로 지운다. REQUIRES_NEW로 커밋되는 ai_jobs도 명시적으로 비운다. */
    private void deleteAllFixtures() {
        aiJobRepository.deleteAll();
        opinionRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectSectionRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Timeout(15) // self-wait가 재발하면 lock timeout까지 멈추므로 빠르게 실패시킨다.
    @DisplayName("OWNER의 신규 정리 요청은 self-wait 없이 QUEUED 작업을 만든다")
    void requestSynthesis_createsQueuedJob_withoutSelfWait() {
        UUID requestId = synthesisRequestService.requestSynthesis(synthesizingSection.getId(), owner.getId());

        assertThat(requestId).isNotNull();
        assertThat(aiJobRepository.findByRequestId(requestId)).isPresent()
                .get().satisfies(job -> assertThat(job.getStatus()).isEqualTo(AiJobStatus.QUEUED));
        assertThat(aiJobRepository.count()).isEqualTo(1);
    }

    @Test
    @Timeout(15)
    @DisplayName("같은 입력의 재요청은 기존 QUEUED 작업을 재사용한다 (중복 생성 없음)")
    void requestSynthesis_isIdempotent() {
        UUID first = synthesisRequestService.requestSynthesis(synthesizingSection.getId(), owner.getId());
        UUID second = synthesisRequestService.requestSynthesis(synthesizingSection.getId(), owner.getId());

        assertThat(second).isEqualTo(first);
        assertThat(aiJobRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("MEMBER가 요청하면 403 FORBIDDEN이고 작업을 만들지 않는다")
    void requestSynthesis_byMember_forbidden() {
        assertThatThrownBy(() ->
                synthesisRequestService.requestSynthesis(synthesizingSection.getId(), member.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(aiJobRepository.count()).isZero();
    }

    @Test
    @DisplayName("SYNTHESIZING이 아닌 섹션의 신규 요청은 409 S002다")
    void requestSynthesis_notSynthesizing_throwsS002() {
        ProjectSection collecting = projectSectionRepository.save(ProjectSection.builder()
                .project(project).title("배경").sectionOrder(2)
                .status(ProjectSectionStatus.COLLECTING).build());

        assertThatThrownBy(() ->
                synthesisRequestService.requestSynthesis(collecting.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION));

        assertThat(aiJobRepository.count()).isZero();
    }
}
