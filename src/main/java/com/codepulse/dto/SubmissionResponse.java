package com.codepulse.dto;

import com.codepulse.entity.Submission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private Long id;
    private Integer score;
    private String feedback;
    private LocalDateTime submittedAt;
    private Long challengeId;

    public static SubmissionResponse fromEntity(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .score(submission.getScore())
                .feedback(submission.getAiFeedback())
                .submittedAt(submission.getSubmittedAt())
                .challengeId(submission.getChallenge() != null ? submission.getChallenge().getId() : null)
                .build();
    }
}
