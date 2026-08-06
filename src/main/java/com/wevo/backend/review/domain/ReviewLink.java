package com.wevo.backend.review.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 섹션 외부 검토용 공유 링크. 외부 검토자에게 토큰으로 발급된다.
 *
 * <p>내부(팀) 검토는 멤버십 기반(§6.1)이라 링크를 쓰지 않으므로, 이 링크는 외부 검토 전용이다.
 *
 * <h2>보안 · 남용 방지</h2>
 * <ul>
 *   <li><b>토큰 해시 저장</b> — 원문 토큰은 URL 로만 전달하고 DB 에는 SHA-256 해시({@code tokenHash})만 저장한다.
 *       조회 시 요청 토큰을 해시해 비교한다. (DB 유출 시에도 링크 접근 불가)</li>
 *   <li><b>본문 스냅샷 (발급 시점 버전 고정)</b> — 링크는 발급 시점의 <b>최신 초안 버전</b>에 고정되며,
 *       제목·본문·{@code contentVersion} 스냅샷은 발급 이후 <b>불변</b>이다. 외부 검토자는 <b>항상 스냅샷</b>을
 *       읽으며, 이후 본문이 수정돼도 링크가 가리키던 버전을 그대로 본다. 초안이 없으면 발급을 거부한다(R009).
 *       새 본문에 대한 외부 검토는 링크 <b>재발급</b>으로만 가능하다.</li>
 *   <li><b>서버 자동 만료 (기본 경로)</b> — 링크 종료는 서버가 처리한다. 본문이 수정되면 링크는
 *       {@link ReviewLinkStatus#OUTDATED} 로 만료되고, 재발급 시 기존 {@code ACTIVE} 링크는
 *       {@link ReviewLinkStatus#CLOSED} 로 닫힌다. 팀장이 지금 당장 수집을 끊어야 할 때만
 *       수동 종료(API_SPEC §3.5.9)를 쓴다. 만료·비활성 링크는 제출을 받지 않는다.</li>
 *   <li><b>유효 기간 (발급 시 선택)</b> — 발급 시점에 {@code expiresOn}(마지막 유효 날짜)을 지정하면
 *       그 날 끝까지만 제출을 받고 이후에는 {@link ReviewLinkStatus#EXPIRED} 로 취급한다.
 *       기간이 지났는지는 <b>항상 날짜 비교로 판정</b>하고({@link #isPastDue}), 저장된 상태값은
 *       링크를 다시 만질 때 {@link #expireIfPastDue} 로 따라온다 — 만료를 배치로 훑지 않아도
 *       기간이 지난 링크가 제출을 받는 구간이 생기지 않는다.</li>
 *   <li><b>종료 상태는 되돌아가지 않는다</b> — {@code ACTIVE} 에서만 다른 상태로 전이하며,
 *       한 번 {@code OUTDATED}·{@code EXPIRED}·{@code CLOSED} 가 된 링크는 상태가 다시 바뀌지 않는다.
 *       종료 사유({@code OUTDATED} = 본문 수정, {@code EXPIRED} = 유효 기간 만료,
 *       {@code CLOSED} = 종료)가 나중 호출에 덮여 외부 검토자 안내 문구가 뒤바뀌는 일을 막기 위함이다.
 *       수동 종료({@code close()})는 {@code ACTIVE} 링크에서만 성공하고, 이미 끝난 링크는
 *       사유별로 거절한다({@code CLOSED} → R011, {@code OUTDATED} → R012, {@code EXPIRED} → R014).</li>
 * </ul>
 */
@Entity
@Table(name = "review_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLink extends BaseTimeEntity {

    /** 링크 하나가 받을 수 있는 외부 검토 제출 상한. (스팸 최종 방어선) */
    public static final int MAX_SUBMISSIONS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_section_id", nullable = false)
    private ProjectSection projectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    /** 원문 토큰의 SHA-256 해시. 원문은 저장하지 않는다. */
    @Column(name = "token_hash", length = 64, unique = true, nullable = false)
    private String tokenHash;

    /** 발급 시점 섹션 제목 스냅샷. */
    @Column(name = "section_title_snapshot", length = 200)
    private String sectionTitleSnapshot;

    /** 발급 시점 본문 스냅샷. 외부 검토자는 항상 이 값을 읽는다. (발급 이후 불변) */
    @Column(name = "content_snapshot", columnDefinition = "TEXT", nullable = false)
    private String contentSnapshot;

    /**
     * 링크가 고정하는 발급 시점 본문 버전. (발급 이후 불변 — 만료 판단·추적용)
     *
     * <p>초안 없는 발급은 거부되므로(R009) 항상 존재하며, 이 링크의 모든 제출은
     * 이 버전을 검토한 것으로 해석한다.
     */
    @Column(name = "content_version", nullable = false)
    private Integer contentVersion;

    /** 발급 시점에 확정된 작성자 의도. 공개 검토 응답에는 절대 노출하지 않는다. */
    @Column(name = "author_intent_snapshot", length = 300)
    private String authorIntentSnapshot;

    /** 의도 snapshot의 원본 이력. 레거시 링크에는 없을 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_intent_id")
    private SectionAuthorIntent authorIntent;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ReviewLinkStatus status;

    /**
     * 링크가 살아 있는 <b>마지막 날</b>. (유효기간/이 날 끝까지 제출을 받는다)
     *
     * <p>{@code null} 이면 기간 제한이 없다. 유효 기간은 날짜 단위로만 지정하여
     * 시:분은 두지 않으며, 발급일 당일·과거 날짜는 발급 시 거부한다(422 {@code C002}).
     */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Builder
    private ReviewLink(ProjectSection projectSection, User createdBy, String tokenHash,
                       String sectionTitleSnapshot, String contentSnapshot, Integer contentVersion,
                       String authorIntentSnapshot, SectionAuthorIntent authorIntent,
                       ReviewLinkStatus status, LocalDate expiresOn) {
        this.projectSection = projectSection;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.sectionTitleSnapshot = sectionTitleSnapshot;
        this.contentSnapshot = contentSnapshot;
        this.contentVersion = contentVersion;
        this.authorIntentSnapshot = authorIntentSnapshot;
        this.authorIntent = authorIntent;
        this.status = status;
        this.expiresOn = expiresOn;
    }

    /** 유효 기간이 지났는지. (기간 미지정 링크는 언제나 {@code false}) */
    public boolean isPastDue(LocalDate today) {
        return expiresOn != null && today.isAfter(expiresOn);
    }

    /**
     * {@code today} 기준으로 이 링크를 어떻게 안내해야 하는지. (저장된 상태 + 유효 기간 판정)
     *
     * <p>기간이 지난 {@code ACTIVE} 링크는 저장된 상태를 아직 정리하지 못했더라도
     * {@link ReviewLinkStatus#EXPIRED} 로 읽힌다 — 조회 응답과 제출 거절 사유가 항상 일치한다.
     */
    public ReviewLinkStatus statusAsOf(LocalDate today) {
        return isPastDue(today) && status == ReviewLinkStatus.ACTIVE
                ? ReviewLinkStatus.EXPIRED
                : status;
    }

    /**
     * 유효 기간이 지난 {@code ACTIVE} 링크를 {@link ReviewLinkStatus#EXPIRED} 로 정리한다.
     *
     * <p>이미 끝난 링크(만료·종료)는 종결 사유를 덮지 않고 그대로 둔다.
     *
     * @return 이번 호출로 {@code EXPIRED} 가 됐으면 {@code true}
     */
    public boolean expireIfPastDue(LocalDate today) {
        if (status == ReviewLinkStatus.ACTIVE && isPastDue(today)) {
            this.status = ReviewLinkStatus.EXPIRED;
            return true;
        }
        return false;
    }

    /**
     * 본문이 수정돼 링크를 만료시킨다. (첫 실제 저장 시점에 호출)
     * 이미 만료·비활성인 링크는 상태를 유지한다.
     */
    public void markOutdated() {
        if (status == ReviewLinkStatus.ACTIVE) {
            this.status = ReviewLinkStatus.OUTDATED;
        }
    }

    /**
     * 링크를 종료한다. (재발급 시 기존 ACTIVE 링크 종료 · 팀장의 수동 종료)
     *
     * <p><b>{@code ACTIVE} 링크만 종료할 수 있다.</b> 이미 끝난 링크는 상태를 그대로 둔 채
     * 사유를 구분해 거절한다 — 종료({@code CLOSED})와 만료({@code OUTDATED}·{@code EXPIRED})는
     * 서로 다른 종결 상태이므로, 팀장이 "내가 방금 닫았다"고 오인하지 않도록 각각 다른 문구로 알린다.
     * <b>거절 경로에서는 상태를 건드리지 않는다</b> — 롤백에 기대지 않아야 참여 트랜잭션에서도
     * "거절 = 상태 유지"가 그대로 성립한다.
     *
     * <p>유효 기간 판정에는 {@code today} 를 쓴다({@link #statusAsOf}) — 저장된 상태가 아직
     * {@code ACTIVE} 라도 기간이 지났으면 종료가 아니라
     * {@link ErrorCode#REVIEW_LINK_CLOSE_ALREADY_EXPIRED} 로 거절한다.
     *
     * @throws BusinessException 이미 종료된 링크면 {@link ErrorCode#REVIEW_LINK_CLOSE_ALREADY_CLOSED},
     *                           본문 수정으로 만료된 링크면 {@link ErrorCode#REVIEW_LINK_CLOSE_ALREADY_OUTDATED},
     *                           유효 기간이 지나 만료된 링크면 {@link ErrorCode#REVIEW_LINK_CLOSE_ALREADY_EXPIRED}
     */
    public void close(LocalDate today) {
        switch (statusAsOf(today)) {
            case ACTIVE -> this.status = ReviewLinkStatus.CLOSED;
            case CLOSED -> throw new BusinessException(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_CLOSED);
            case OUTDATED -> throw new BusinessException(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_OUTDATED);
            case EXPIRED -> throw new BusinessException(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_EXPIRED);
        }
    }
}
