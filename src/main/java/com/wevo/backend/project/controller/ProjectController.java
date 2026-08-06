package com.wevo.backend.project.controller;

import com.wevo.backend.global.config.ApiExampleRefs;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.request.ProjectUpdateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.dto.response.ProjectDetailResponse;
import com.wevo.backend.project.dto.response.ProjectMemberListResponse;
import com.wevo.backend.project.dto.response.ProjectSummaryResponse;
import com.wevo.backend.project.dto.response.SectionSummaryResponse;
import com.wevo.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "프로젝트 생성·조회·수정·삭제·멤버·섹션 목록 API")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 프로젝트 생성 — 생성자를 OWNER 로 등록하고 유형별 고정 섹션을 자동 생성한다.
     */
    @Operation(summary = "프로젝트 생성 — 생성자를 OWNER 로 등록하고 유형별 고정 6섹션 자동 생성")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "PROJECT_CREATED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — ideaText·resultType·audience 검증 실패",
                    content = @Content(examples = @ExampleObject(
                            name = "C001", ref = ApiExampleRefs.INVALID_INPUT))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "U001 — 토큰의 사용자를 찾을 수 없음")
    })
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
     * 내 프로젝트 목록 조회 — 내가 멤버로 속한 프로젝트만 최근 작업순으로 반환한다.
     *
     * <p>대시보드 카드가 한 번의 요청으로 그려지도록 마지막 활동 섹션과 확정 진행도를 함께 담는다.
     */
    @Operation(summary = "내 프로젝트 목록 — 멤버인 것만, 보관(ARCHIVED) 제외, 최근 작업순 "
            + "(마지막 활동 섹션·확정 진행도 포함)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED)))
    })
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
    @Operation(summary = "프로젝트 상세 — 내 역할·멤버 수·섹션 진행 요약")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)",
                    content = @Content(examples = @ExampleObject(
                            name = "P001", ref = ApiExampleRefs.PROJECT_NOT_FOUND)))
    })
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
    @Operation(summary = "프로젝트 멤버 목록 — OWNER 우선, 참여 시각 오름차순 (이메일 미포함)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)")
    })
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
    @Operation(summary = "섹션 목록 — 상태·핵심 질문·작성 가이드 (order 오름차순)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)")
    })
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
    @Operation(summary = "프로젝트 삭제 — 보관 처리(ARCHIVED), OWNER 만. 재호출 시 멱등")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "본문 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)")
    })
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId
    ) {
        projectService.archiveProject(principal.userId(), projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 프로젝트 기본 정보 수정 — 이름·설명만. OWNER 만 호출할 수 있다.
     *
     * <p>부분 수정이라 본문에 없는 필드는 기존값을 유지한다. 응답은 상세 조회와 같은 구조라
     * 수정 직후 재조회가 필요 없다.
     */
    @Operation(summary = "프로젝트 기본 정보 수정 — title·description 부분 수정, OWNER 만")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "PROJECT_UPDATED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — title 공백·200자 초과, description 2,000자 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "A002 — 멤버지만 OWNER 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "P001 — 프로젝트 없음 또는 비멤버 (존재 숨김)")
    })
    @PatchMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectUpdateRequest request
    ) {
        ProjectDetailResponse response =
                projectService.updateProject(principal.userId(), projectId, request);
        return ResponseEntity.ok(
                ApiResponse.success("PROJECT_UPDATED", "프로젝트가 수정되었습니다.", response));
    }
}
