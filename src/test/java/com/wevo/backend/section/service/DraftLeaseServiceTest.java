package com.wevo.backend.section.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.dto.response.DraftLeaseAcquireResponse;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    private UserService userService;

    @InjectMocks
    private DraftLeaseService draftLeaseService;

    private ProjectSection section;
    private User owner;
    private User member;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().title("위보 프로젝트").build();
        section = ProjectSection.builder().project(project).title("문제 정의").build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);

        owner = user(OWNER_ID, "김민준");
        member = user(MEMBER_ID, "이서연");
    }

    @Test
    @DisplayName("lease가 없으면 섹션 잠금 아래에서 60초 편집권을 새로 만든다")
    void acquire_createsLease() {
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.empty());
        given(userService.getUserReference(OWNER_ID)).willReturn(owner);
        given(draftLeaseRepository.saveAndFlush(any(DraftLease.class))).willAnswer(invocation -> {
            DraftLease lease = invocation.getArgument(0);
            ReflectionTestUtils.setField(lease, "id", 91L);
            return lease;
        });
        LocalDateTime before = LocalDateTime.now(KST);

        DraftLeaseAcquireResponse response = draftLeaseService.acquire(SECTION_ID, OWNER_ID);

        assertThat(response.leaseId()).isEqualTo(91L);
        assertThat(response.projectSectionId()).isEqualTo(SECTION_ID);
        assertThat(response.holder().id()).isEqualTo(OWNER_ID);
        assertThat(response.holder().name()).isEqualTo("김민준");
        assertThat(response.leaseUntil()).isAfterOrEqualTo(before.plusSeconds(59));
        verify(sectionAccessGuard).requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID);
    }

    @Test
    @DisplayName("본인이 보유한 활성 lease를 다시 획득하면 같은 lease의 만료 시각을 연장한다")
    void acquire_byCurrentHolder_renewsExistingLease() {
        DraftLease lease = lease(91L, owner, LocalDateTime.now(KST).plusSeconds(10));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.of(lease));
        given(userService.getUserReference(OWNER_ID)).willReturn(owner);
        LocalDateTime before = LocalDateTime.now(KST);

        DraftLeaseAcquireResponse response = draftLeaseService.acquire(SECTION_ID, OWNER_ID);

        assertThat(response.leaseId()).isEqualTo(91L);
        assertThat(response.holder().id()).isEqualTo(OWNER_ID);
        assertThat(response.leaseUntil()).isAfterOrEqualTo(before.plusSeconds(59));
        verify(draftLeaseRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 사용자의 활성 lease가 있으면 S005 충돌과 보유자 정보를 반환한다")
    void acquire_whenHeldByOther_throwsS005() {
        DraftLease lease = lease(91L, member, LocalDateTime.now(KST).plusSeconds(30));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.of(lease));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> draftLeaseService.acquire(SECTION_ID, OWNER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DRAFT_LEASE_HELD_BY_OTHER);
        assertThat(exception.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getField()).isEqualTo("holder"));
        verify(userService, never()).getUserReference(any());
    }

    @Test
    @DisplayName("만료된 lease는 새 요청자가 같은 섹션의 편집권으로 재획득할 수 있다")
    void acquire_whenExpired_grantsLeaseToRequester() {
        DraftLease lease = lease(91L, member, LocalDateTime.now(KST).minusSeconds(1));
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(draftLeaseRepository.findByProjectSection_Id(SECTION_ID)).willReturn(Optional.of(lease));
        given(userService.getUserReference(OWNER_ID)).willReturn(owner);

        DraftLeaseAcquireResponse response = draftLeaseService.acquire(SECTION_ID, OWNER_ID);

        assertThat(response.leaseId()).isEqualTo(91L);
        assertThat(response.holder().id()).isEqualTo(OWNER_ID);
        assertThat(lease.getHolder()).isSameAs(owner);
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
