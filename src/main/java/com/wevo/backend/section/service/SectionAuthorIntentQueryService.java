package com.wevo.backend.section.service;

import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionAuthorIntentStatus;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** review 도메인이 확정 의도 snapshot을 만들 때 사용하는 section 공개 조회 서비스. */
@Service
@Transactional(readOnly = true)
public class SectionAuthorIntentQueryService {

    private final SectionAuthorIntentRepository repository;

    public SectionAuthorIntentQueryService(SectionAuthorIntentRepository repository) {
        this.repository = repository;
    }

    public Optional<SectionAuthorIntent> findConfirmed(Long sectionId, int contentVersion) {
        return repository.findByProjectSection_IdAndContentVersionAndStatus(
                sectionId, contentVersion, SectionAuthorIntentStatus.CONFIRMED);
    }
}
