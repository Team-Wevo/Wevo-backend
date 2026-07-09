package com.wevo.backend.project.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.dto.request.ProjectCreateRequest;
import com.wevo.backend.project.dto.response.ProjectCreateResponse;
import com.wevo.backend.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
