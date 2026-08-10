package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProjectOutputQueryServiceTest {

    private static final Long PROJECT_ID = 10L;

    @Mock
    private VerifiedProjectAccess access;

    private final ProjectOutputQueryService projectOutputQueryService = new ProjectOutputQueryService();

    @Test
    @DisplayName("결과물 머리말에 쓸 프로젝트 정보를 값 객체로 반환한다")
    void getOutputHeader_returnsIdTitleAndResultType() {
        given(access.project()).willReturn(project("위보 발표 준비", OutputType.PRESENTATION));

        ProjectOutputHeader header = projectOutputQueryService.getOutputHeader(access);

        assertThat(header).isEqualTo(
                new ProjectOutputHeader(PROJECT_ID, "위보 발표 준비", OutputType.PRESENTATION));
    }

    @Test
    @DisplayName("결과물 머리말에 필요 없는 값(아이디어 원문·전달 대상)은 담지 않는다")
    void getOutputHeader_exposesOnlyOutputHeaderFields() {
        Project project = project("장학금 매칭 서비스 제안서", OutputType.PROPOSAL);
        given(access.project()).willReturn(project);

        ProjectOutputHeader header = projectOutputQueryService.getOutputHeader(access);

        assertThat(ProjectOutputHeader.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("projectId", "title", "resultType");
        assertThat(header.title()).isEqualTo(project.getTitle());
    }

    private Project project(String title, OutputType resultType) {
        Project project = Project.builder()
                .title(title)
                .ideaText("흩어진 팀 의견을 하나로 모으는 협업 툴")
                .resultType(resultType)
                .audience("심사위원")
                .status(ProjectStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);

        return project;
    }
}
