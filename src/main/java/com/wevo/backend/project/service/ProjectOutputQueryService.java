package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과물 조립에 필요한 프로젝트 정보의 <b>조회 전용 공개 창구</b>.
 *
 * <p>타 도메인(export 등)이 {@link Project} 엔티티를 직접 참조하지 않고 이 서비스로만 접근하도록
 * 한다. 반환 타입도 엔티티가 아니라 값 객체({@link ProjectOutputHeader})라, 프로젝트의 내부 구조가
 * 바뀌어도 호출측이 영향받지 않는다. (CLAUDE.md §6)
 *
 * <p><b>권한 검사는 하지 않는다</b> — 호출측이 자신의 접근 규칙(존재 숨김 등)을 적용한 뒤 호출한다.
 * 이 계약은 주석이 아니라 타입으로 강제된다: 프로젝트 ID 대신 {@link VerifiedProjectAccess}를
 * 받으며, 이 값은 {@link ProjectAccessGuard}만 만들 수 있으므로 멤버십 검사를 건너뛴 호출은
 * 애초에 컴파일되지 않는다. (section 도메인의 {@code SectionConfirmationQueryService}와 같은 형태)
 */
@Service
@Transactional(readOnly = true)
public class ProjectOutputQueryService {

    /**
     * 결과물 머리말에 쓸 프로젝트 정보를 반환한다.
     *
     * <p>프로젝트는 멤버십 조회 시 함께 로딩되므로 추가 쿼리가 나가지 않는다.
     *
     * @param access 멤버십 검사를 통과했다는 증거 (호출측이 인가를 수행했음을 타입으로 보장)
     */
    public ProjectOutputHeader getOutputHeader(VerifiedProjectAccess access) {
        Project project = access.project();

        return new ProjectOutputHeader(
                project.getId(), project.getTitle(), project.getResultType());
    }
}
