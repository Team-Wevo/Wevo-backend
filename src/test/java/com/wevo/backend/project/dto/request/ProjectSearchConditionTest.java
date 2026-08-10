package com.wevo.backend.project.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로젝트 목록 검색·필터 조건의 판정 규칙. (API_SPEC §3.2.2)
 */
class ProjectSearchConditionTest {

    @Test
    @DisplayName("조건이 없으면 모두 통과한다 — 기존 동작 유지")
    void noConditionMatchesEverything() {
        assertThat(ProjectSearchCondition.none().matches(project("위보 발표 준비",
                OutputType.PRESENTATION, ProjectStatus.ACTIVE))).isTrue();
    }

    @Test
    @DisplayName("검색어는 제목 부분 일치이고 대소문자를 구분하지 않는다")
    void keywordIsCaseInsensitivePartialMatch() {
        Project project = project("Wevo 발표 준비", OutputType.PRESENTATION, ProjectStatus.ACTIVE);

        assertThat(new ProjectSearchCondition("wevo", null, null).matches(project)).isTrue();
        assertThat(new ProjectSearchCondition("발표", null, null).matches(project)).isTrue();
        assertThat(new ProjectSearchCondition("제안서", null, null).matches(project)).isFalse();
    }

    @Test
    @DisplayName("공백만 넣은 검색어는 검색하지 않은 것으로 다룬다")
    void blankKeywordIsIgnored() {
        // 그대로 두면 모든 제목에 걸려 필터가 의미를 잃는다.
        assertThat(new ProjectSearchCondition("   ", null, null).keyword()).isNull();
    }

    @Test
    @DisplayName("유형·상태 필터는 정확히 일치할 때만 통과한다")
    void typeAndStatusMatchExactly() {
        Project project = project("위보", OutputType.PRESENTATION, ProjectStatus.ACTIVE);

        assertThat(new ProjectSearchCondition(null, OutputType.PRESENTATION, null).matches(project))
                .isTrue();
        assertThat(new ProjectSearchCondition(null, OutputType.PROPOSAL, null).matches(project))
                .isFalse();
        assertThat(new ProjectSearchCondition(null, null, ProjectStatus.ACTIVE).matches(project))
                .isTrue();
        assertThat(new ProjectSearchCondition(null, null, ProjectStatus.COMPLETED).matches(project))
                .isFalse();
    }

    @Test
    @DisplayName("여러 조건은 AND 로 묶인다 — 하나라도 어긋나면 제외된다")
    void conditionsAreCombinedWithAnd() {
        Project project = project("위보 발표 준비", OutputType.PRESENTATION, ProjectStatus.ACTIVE);

        assertThat(new ProjectSearchCondition("발표", OutputType.PRESENTATION, ProjectStatus.ACTIVE)
                .matches(project)).isTrue();
        assertThat(new ProjectSearchCondition("발표", OutputType.PROPOSAL, ProjectStatus.ACTIVE)
                .matches(project)).isFalse();
    }

    private Project project(String title, OutputType resultType, ProjectStatus status) {
        User owner = User.builder().name("호석").status(UserStatus.ACTIVE).build();
        return Project.builder()
                .owner(owner)
                .title(title)
                .ideaText("아이디어")
                .resultType(resultType)
                .audience("심사위원")
                .status(status)
                .build();
    }
}
