package com.wevo.backend.section.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DraftLeaseServiceTest {

    private static final Long SECTION_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private SectionAccessGuard sectionAccessGuard;
    @Mock
    private DraftLeaseRepository draftLeaseRepository;
    @Mock
    private SectionDraftRepository sectionDraftRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private DraftLeaseService draftLeaseService;

    private ProjectSection section;
    private User owner;
    private User member;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().title("위보 프로젝트").build();
        section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .status(ProjectSectionStatus.DRAFTING)
                .build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);

        owner = user(OWNER_ID, "김민준");
        member = user(MEMBER_ID, "이서연");
    }

    @ParameterizedTest
    @EnumSource(value = ProjectSectionStatus.class, names = {"DRAFTING", "REVIEWING", "CONFIRMED"})
    @DisplayName("편집 가능한 상태에서 lease가 없으면 5분 편집권을 새로 만든다")
    void acquire_createsLease(ProjectSectionStatus status) {
        ReflectionTestUtils.setField(section, "status", status);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.empty());
        given(userService.getUserReference(OWNER_ID)).willReturn(owner);
        given(draftLeaseRepository.saveAndFlush(any(DraftLease.class))).willAnswer(invocation -> {
            DraftLease lease = invocation.getArgument(0);
            ReflectionTestUtils.setField(lease, "id", 91L);
            return lease;
        });
        LocalDateTime before = LocalDateTime.now(KST);

        DraftLeaseAcquireResponse response = draftLeaseService.acquire(SECTION_ID, OWNER_ID);

        assertThat(response.expiresAt()).isAfterOrEqualTo(before.plusMinutes(5));
        verify(sectionAccessGuard).requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID);
    }

    @Test
    @DisplayName("본인이 보유한 활성 lease를 다시 획득하면 같은 lease의 만료 시각을 연장한다")
    void acquire_byCurrentHolder_renewsExistingLease() {
        DraftLease lease = lease(91L, owner, LocalDateTime.now(KST).plusSeconds(10));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.of(lease));
        given(userService.getUserReference(OWNER_ID)).willReturn(owner);
        LocalDateTime before = LocalDateTime.now(KST);

        DraftLeaseAcquireResponse response = draftLeaseService.acquire(SECTION_ID, OWNER_ID);

        assertThat(response.expiresAt()).isAfterOrEqualTo(before.plusMinutes(5));
        verify(draftLeaseRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 사용자의 활성 lease가 있으면 errors 없이 S004 충돌을 반환한다")
    void acquire_whenHeldByOther_throwsS004() {
        DraftLease lease = lease(91L, member, LocalDateTime.now(KST).plusSeconds(30));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.of(lease));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> draftLeaseService.acquire(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        assertThat(exception.getErrors()).isNull();
        verify(userService, never()).getUserReference(any());
    }

    @Test
    @DisplayName("만료된 lease는 새 요청자가 같은 섹션의 편집권으로 재획득할 수 있다")
    void acquire_whenExpired_grantsLeaseToRequester() {
        DraftLease lease = lease(91L, member, LocalDateTime.now(KST).minusSeconds(1));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.of(lease));
        given(userService.getUserReference(OWNER_ID)).willReturn(owner);

        draftLeaseService.acquire(SECTION_ID, OWNER_ID);

        assertThat(lease.getHolder()).isSameAs(owner);
    }

    @Test
    @DisplayName("초안이 없으면 S003으로 편집 잠금 획득을 거부한다")
    void acquire_withoutDraft_throwsS003() {
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> draftLeaseService.acquire(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_DRAFT_NOT_FOUND);
        verifyNoInteractions(draftLeaseRepository, userService);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectSectionStatus.class, names = {"COLLECTING", "SYNTHESIZING"})
    @DisplayName("편집 불가 상태이면 S002로 편집 잠금 획득을 거부한다")
    void acquire_inNonEditableStatus_throwsS002(ProjectSectionStatus status) {
        ReflectionTestUtils.setField(section, "status", status);
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(sectionDraftRepository.existsByProjectSection_Id(SECTION_ID)).willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> draftLeaseService.acquire(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        verifyNoInteractions(draftLeaseRepository, userService);
    }

    private User user(Long id, String name) {
        User user = User.builder().name(name).email(name + "@wevo.com").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private DraftLease lease(Long id, User holder, LocalDateTime leaseUntil) {
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holder(holder)
                .leaseUntil(leaseUntil)
                .build();
        ReflectionTestUtils.setField(lease, "id", id);
        return lease;
    }
}
