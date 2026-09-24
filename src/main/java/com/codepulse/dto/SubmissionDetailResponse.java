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
public class SubmissionDetailResponse {

    private Long id;
    private Long challengeId;
    private String challengeTitle;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private String code;
    private Integer score;
    private String aiFeedback;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime submittedAt;

    public static SubmissionDetailResponse fromEntity(Submission submission) {
        return SubmissionDetailResponse.builder()
                .id(submission.getId())
                .challengeId(submission.getChallenge() != null ? submission.getChallenge().getId() : null)
                .challengeTitle(submission.getChallenge() != null ? submission.getChallenge().getTitle() : null)
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .studentName(submission.getStudent() != null ? submission.getStudent().getName() : null)
                .studentEmail(submission.getStudent() != null ? submission.getStudent().getEmail() : null)
                .code(submission.getCode())
                .score(submission.getScore())
                .aiFeedback(submission.getAiFeedback())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
}
