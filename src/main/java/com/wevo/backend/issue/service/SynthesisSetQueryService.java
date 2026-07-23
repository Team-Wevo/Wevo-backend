package com.wevo.backend.issue.service;

import com.wevo.backend.issue.repository.SynthesisSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 정리 결과 존재 여부만 확인하도록 공개하는 읽기 경계. */
@Service
@Transactional(readOnly = true)
public class SynthesisSetQueryService {

    private final SynthesisSetRepository synthesisSetRepository;

    public SynthesisSetQueryService(SynthesisSetRepository synthesisSetRepository) {
        this.synthesisSetRepository = synthesisSetRepository;
    }

    public boolean existsForSection(Long projectSectionId) {
        return synthesisSetRepository.existsByProjectSectionId(projectSectionId);
    }
}
