package com.wevo.backend.opinion.repository;

import com.wevo.backend.global.config.JpaAuditingConfig;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class OpinionRepositoryTest {

    private static final String CONTENT = "타겟을 공모전 참가 대학생 팀으로 좁히는 게 좋겠습니다.";

    @Autowired
    private OpinionRepository opinionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectSectionRepository projectSectionRepository;
    @Autowired
    private EntityManager entityManager;

    private User author;
    private ProjectSection section;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .name("김민준")
                .email("author@wevo.com")
                .status(UserStatus.ACTIVE)
                .build());
        Project project = projectRepository.save(Project.builder()
                .owner(author)
                .title("발표 프로젝트")
                .resultType(OutputType.PRESENTATION)
                .audience("발표 심사위원")
                .status(ProjectStatus.ACTIVE)
                .build());
        section = projectSectionRepository.save(ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.COLLECTING)
                .build());
    }

    @Test
    @DisplayName("저장 시 JPA Auditing 으로 createdAt/updatedAt 이 채워진다")
    void save_populatesAuditingFields() {
        Opinion saved = opinionRepository.saveAndFlush(Opinion.builder()
                .projectSection(section)
                .author(author)
                .content(CONTENT)
                .status(OpinionStatus.DRAFT)
                .build());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 섹션 + 같은 작성자 조합의 의견은 하나만 저장할 수 있다")
    void save_duplicateSectionAuthor_throwsDataIntegrityViolation() {
        opinionRepository.saveAndFlush(Opinion.builder()
                .projectSection(section)
                .author(author)
                .content(CONTENT)
                .status(OpinionStatus.DRAFT)
                .build());

        assertThatThrownBy(() -> opinionRepository.saveAndFlush(Opinion.builder()
                .projectSection(section)
                .author(author)
                .content("같은 사람이 같은 섹션에 쓴 두번째 의견입니다.")
                .status(OpinionStatus.DRAFT)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("섹션 ID + 작성자 ID 로 내 의견을 조회할 수 있다")
    void findByProjectSectionIdAndAuthorId_returnsMyOpinion() {
        Opinion saved = opinionRepository.saveAndFlush(Opinion.builder()
                .projectSection(section)
                .author(author)
                .content(CONTENT)
                .status(OpinionStatus.DRAFT)
                .build());

        Optional<Opinion> found = opinionRepository
                .findByProjectSection_IdAndAuthor_Id(section.getId(), author.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getContent()).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("최초 제출 시 SUBMITTED 상태와 제출 시각이 저장되고 재호출해도 시각이 유지된다")
    void submit_persistsStatusAndKeepsFirstSubmittedAt() {
        Opinion opinion = opinionRepository.saveAndFlush(Opinion.builder()
                .projectSection(section)
                .author(author)
                .content(CONTENT)
                .status(OpinionStatus.DRAFT)
                .build());
        LocalDateTime firstSubmittedAt = LocalDateTime.of(2026, 7, 14, 12, 5);

        opinion.submit(firstSubmittedAt);
        opinionRepository.flush();
        opinion.submit(firstSubmittedAt.plusMinutes(10));
        opinionRepository.flush();
        entityManager.clear();

        Opinion found = opinionRepository.findById(opinion.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(found.getSubmittedAt()).isEqualTo(firstSubmittedAt);
    }

    @Test
    @DisplayName("제출 목록 조회는 SUBMITTED 만 제출 시각 오름차순으로 반환하고 DRAFT 는 제외한다")
    void findAllWithAuthor_returnsSubmittedOnlyOrderedBySubmittedAt() {
        User late = userRepository.save(User.builder()
                .name("이서연").email("late@wevo.com").status(UserStatus.ACTIVE).build());
        User early = userRepository.save(User.builder()
                .name("박지훈").email("early@wevo.com").status(UserStatus.ACTIVE).build());
        // author 의 DRAFT — 목록에 나오면 안 된다
        opinionRepository.save(Opinion.builder()
                .projectSection(section).author(author)
                .content(CONTENT).status(OpinionStatus.DRAFT)
                .build());
        // 늦게 제출한 의견을 먼저 저장해 정렬이 저장 순서가 아님을 보장한다
        Opinion lateOpinion = opinionRepository.save(Opinion.builder()
                .projectSection(section).author(late)
                .content(CONTENT).status(OpinionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 7, 14, 11, 0))
                .build());
        Opinion earlyOpinion = opinionRepository.save(Opinion.builder()
                .projectSection(section).author(early)
                .content(CONTENT).status(OpinionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 7, 14, 10, 20))
                .build());
        opinionRepository.flush();
        entityManager.clear();

        List<Opinion> found = opinionRepository
                .findAllWithAuthorByProjectSectionIdAndStatus(section.getId(), OpinionStatus.SUBMITTED);

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getId()).isEqualTo(earlyOpinion.getId());
        assertThat(found.get(0).getAuthor().getName()).isEqualTo("박지훈");
        assertThat(found.get(1).getId()).isEqualTo(lateOpinion.getId());
        assertThat(found.get(1).getAuthor().getName()).isEqualTo("이서연");
    }
}
