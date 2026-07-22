package com.wevo.backend.issue.repository;

import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SynthesisInheritedGapAnswerRepository
        extends JpaRepository<SynthesisInheritedGapAnswer, Long> {

    List<SynthesisInheritedGapAnswer> findAllBySynthesisSet_Id(Long synthesisSetId);
}
