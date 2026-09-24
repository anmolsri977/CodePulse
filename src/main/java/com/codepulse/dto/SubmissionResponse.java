package com.codepulse.dto;

import com.codepulse.entity.Submission;
import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
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
