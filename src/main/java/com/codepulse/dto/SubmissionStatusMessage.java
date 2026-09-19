package com.codepulse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionStatusMessage {

    private Long submissionId;
    private Long studentId;
    private String studentName;
    private Long challengeId;
    private Integer score;
    private LocalDateTime submittedAt;
}
