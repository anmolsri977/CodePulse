package com.codepulse.repository;

import com.codepulse.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByStudentId(Long studentId);

    List<Submission> findByChallengeId(Long challengeId);

    List<Submission> findByStudentIdAndChallengeId(Long studentId, Long challengeId);
}
