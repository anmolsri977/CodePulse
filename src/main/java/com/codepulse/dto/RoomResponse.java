package com.codepulse.dto;

import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
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
public class RoomResponse {

    private Long id;
    private String roomCode;
    private String title;
    private RoomStatus status;
    private String teacherName;
    private String teacherEmail;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;

    public static RoomResponse fromEntity(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .title(room.getTitle())
                .status(room.getStatus())
                .teacherName(room.getTeacher() != null ? room.getTeacher().getName() : null)
                .teacherEmail(room.getTeacher() != null ? room.getTeacher().getEmail() : null)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
