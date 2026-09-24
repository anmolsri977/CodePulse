package com.codepulse.dto;

import com.codepulse.entity.Challenge;
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
public class ChallengeResponse {

    private Long id;
    private String title;
    private String description;
    private String skeleton;
    private Integer timeLimit;
    private String roomCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime startedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime endedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;

    public static ChallengeResponse fromEntity(Challenge challenge) {
        return ChallengeResponse.builder()
                .id(challenge.getId())
                .title(challenge.getTitle())
                .description(challenge.getDescription())
                .skeleton(challenge.getSkeleton())
                .timeLimit(challenge.getTimeLimit())
                .roomCode(challenge.getRoom() != null ? challenge.getRoom().getRoomCode() : null)
                .startedAt(challenge.getStartedAt())
                .endedAt(challenge.getEndedAt())
                .createdAt(challenge.getCreatedAt())
                .build();
    }
}
