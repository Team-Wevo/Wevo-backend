package com.wevo.backend.user.controller;

import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.user.dto.request.ProfileUpdateRequest;
import com.wevo.backend.user.dto.response.MyProfileResponse;
import com.wevo.backend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 내 정보 조회 — 로그인한 사용자의 프로필을 반환한다.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        MyProfileResponse response = userService.getMyProfile(principal.userId());
        return ResponseEntity.ok(ApiResponse.success("OK", "조회에 성공했습니다.", response));
    }

    /**
     * 내 정보 수정 — 표시 이름을 변경한다.
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        MyProfileResponse response = userService.updateMyProfile(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("USER_UPDATED", "프로필이 수정되었습니다.", response));
    }
}
