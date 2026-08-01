package com.wevo.backend.project.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.dto.response.ProjectDetailResponse;
import com.wevo.backend.project.dto.response.ProjectMemberListResponse;
import com.wevo.backend.project.dto.response.ProjectSummaryResponse;
import com.wevo.backend.project.dto.response.SectionSummaryResponse;
import com.wevo.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 프로젝트 생성 — 생성자를 OWNER 로 등록하고 유형별 고정 섹션을 자동 생성한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectCreateResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        ProjectCreateResponse response = projectService.create(principal.userId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("PROJECT_CREATED", "프로젝트가 생성되었습니다.", response));
    }

    /**
     * 내 프로젝트 목록 조회 — 내가 멤버로 속한 프로젝트만 최신순으로 반환한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummaryResponse>>> getMyProjects(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        List<ProjectSummaryResponse> projects = projectService.getMyProjects(principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", projects));
    }

    /**
     * 프로젝트 상세 조회 — 멤버가 아니면 404(PROJECT_NOT_FOUND).
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProject(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        ProjectDetailResponse project = projectService.getProject(principal.userId(), projectId);
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", project));
    }

    /**
     * 프로젝트 멤버 목록 조회 — 멤버가 아니면 404(PROJECT_NOT_FOUND).
     */
    @GetMapping("/{projectId}/members")
    public ResponseEntity<ApiResponse<ProjectMemberListResponse>> getMembers(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        ProjectMemberListResponse members = projectService.getMembers(principal.userId(), projectId);
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", members));
    }

    /**
     * 프로젝트 섹션 목록 조회 — 멤버가 아니면 404(PROJECT_NOT_FOUND).
     */
    @GetMapping("/{projectId}/sections")
    public ResponseEntity<ApiResponse<List<SectionSummaryResponse>>> getSections(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        List<SectionSummaryResponse> sections = projectService.getSections(principal.userId(), projectId);
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", sections));
    }

    /**
     * 프로젝트 삭제 — 보관 처리(ARCHIVED)로 목록에서 제외한다. OWNER 만 호출할 수 있다.
     *
     * <p>본문 없이 204 를 반환한다. 이미 보관된 프로젝트를 다시 삭제해도 멱등하게 성공한다.
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        projectService.archiveProject(principal.userId(), projectId);
        return ResponseEntity.noContent().build();
    }
}
