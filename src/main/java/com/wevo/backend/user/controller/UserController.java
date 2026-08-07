package com.wevo.backend.user.controller;

import com.wevo.backend.global.config.ApiExampleRefs;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.user.dto.request.ProfileUpdateRequest;
import com.wevo.backend.user.dto.response.MyProfileResponse;
import com.wevo.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "내 정보 조회·프로필 수정 API")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 내 정보 조회 — 로그인한 사용자의 프로필을 반환한다.
     */
    @Operation(summary = "내 정보 조회 — 프로필 표시·세션 복원용")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "U001 — 토큰의 사용자를 찾을 수 없음")
    })
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
    @Operation(summary = "내 프로필 수정 — 표시 이름만 (이메일·사진 수정 불가)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "USER_UPDATED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "C001 — name 누락·공백·100자 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "U001 — 토큰의 사용자를 찾을 수 없음")
    })
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        MyProfileResponse response = userService.updateMyProfile(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("USER_UPDATED", "프로필이 수정되었습니다.", response));
    }

    /**
     * 회원 탈퇴 — 계정을 소프트 삭제하고 개인 식별정보를 지운다.
     *
     * <p>의견·초안·검토는 팀 공동 결과물이라 지우지 않는다. 작성자 표시만 "탈퇴한 사용자"로 바뀐다.
     *
     * <p>본문 없이 204 를 반환한다. 이미 탈퇴한 계정으로 다시 호출해도 멱등하게 성공한다.
     */
    @Operation(summary = "회원 탈퇴 — 소프트 삭제. 작성물은 보존하고 개인정보만 삭제. OWNER 인 프로젝트가 "
            + "남아 있으면 거부")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "본문 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "A001 — 인증 필요",
                    content = @Content(examples = @ExampleObject(
                            name = "A001", ref = ApiExampleRefs.UNAUTHORIZED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "U001 — 토큰의 사용자를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "U003 — 보관되지 않은 프로젝트의 OWNER 라 탈퇴 불가")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        userService.withdraw(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
