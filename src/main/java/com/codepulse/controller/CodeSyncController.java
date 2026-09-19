package com.codepulse.controller;

import com.codepulse.dto.CodeSyncMessage;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import com.codepulse.entity.User;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CodeSyncController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public CodeSyncController(
            SimpMessagingTemplate messagingTemplate,
            RoomRepository roomRepository,
            UserRepository userRepository
    ) {
        this.messagingTemplate = messagingTemplate;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @MessageMapping("/room/{roomCode}/editor")
    public void syncCode(
            @DestinationVariable String roomCode,
            @Payload CodeSyncMessage message,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthorized: No authenticated principal found");
        }

        String email = principal.getName();
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new AccessDeniedException("User not found: " + email));

        if (user.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Forbidden: Only teachers can broadcast code updates");
        }

        Room room = roomRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new AccessDeniedException("Room not found: " + roomCode));

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new AccessDeniedException("Cannot broadcast to a closed room");
        }

        if (!room.getTeacher().getId().equals(user.getId())) {
            throw new AccessDeniedException("Forbidden: You are not the owner of this room");
        }

        CodeSyncMessage outbound = CodeSyncMessage.builder()
                .code(message.getCode())
                .senderEmail(email)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomCode.trim().toUpperCase() + "/editor", outbound);
    }
}
