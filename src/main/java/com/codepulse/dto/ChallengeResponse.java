package com.codepulse.dto;

import com.codepulse.entity.Challenge;
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
    private LocalDateTime startedAt;
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
                .createdAt(challenge.getCreatedAt())
                .build();
    }
}
