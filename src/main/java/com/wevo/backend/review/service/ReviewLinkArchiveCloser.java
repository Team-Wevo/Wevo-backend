package com.wevo.backend.review.service;

import com.wevo.backend.project.domain.ProjectArchivedEvent;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트가 보관(삭제)되면 그 프로젝트의 활성 외부 검토 링크를 모두 닫는다. (#230)
 *
 * <p>{@code ReviewLink} 는 review 도메인의 것이라, project 가 이를 직접 만지지 않도록 이벤트로 갈라
 * 둔다(CLAUDE.md §6). review → project 의존 방향만 남는다.
 *
 * <p>{@code @EventListener} 라 발행 시점에 <b>같은 스레드·같은 트랜잭션</b>에서 실행된다. 링크 종료가
 * 실패하면 보관도 함께 롤백되는 편이, 외부 검토 링크가 살아 있는 채 삭제만 성사돼 외부인이 계속
 * 열람·제출하는 것보다 안전하다. ({@code WithdrawnUserLeaseCleaner} 와 같은 트랜잭션·같은 원칙)
 */
@Service
public class ReviewLinkArchiveCloser {

    /** 시간 값은 배포 서버 시간대와 무관하게 KST로 고정한다. (CLAUDE.md §5.4) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReviewLinkRepository reviewLinkRepository;

    public ReviewLinkArchiveCloser(ReviewLinkRepository reviewLinkRepository) {
        this.reviewLinkRepository = reviewLinkRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(ProjectArchivedEvent event) {
        LocalDate today = LocalDate.now(KST);
        List<ReviewLink> activeLinks = reviewLinkRepository
                .findByProjectIdAndStatusForUpdate(event.projectId(), ReviewLinkStatus.ACTIVE);
        for (ReviewLink link : activeLinks) {
            // 유효 기간이 이미 지난 링크는 CLOSED 가 아니라 EXPIRED 로 정리해 종결 사유를 보존한다.
            // (issueExternalLink 의 기존 링크 정리와 같은 규칙)
            if (!link.expireIfPastDue(today)) {
                link.close(today);
            }
        }
    }
}
