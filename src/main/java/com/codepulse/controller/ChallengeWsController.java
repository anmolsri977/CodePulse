package com.codepulse.controller;

import com.codepulse.dto.ChallengeResponse;
import com.codepulse.dto.StartChallengeMessage;
import com.codepulse.service.ChallengeService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChallengeWsController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChallengeService challengeService;

    public ChallengeWsController(SimpMessagingTemplate messagingTemplate, ChallengeService challengeService) {
        this.messagingTemplate = messagingTemplate;
        this.challengeService = challengeService;
    }

    @MessageMapping("/room/{roomCode}/challenge")
    public void broadcastChallenge(
            @DestinationVariable String roomCode,
            @Payload StartChallengeMessage message,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthorized: No authenticated principal found");
        }

        if (message == null || message.getChallengeId() == null) {
            throw new AccessDeniedException("Invalid message: challengeId is required");
        }

        // Server-side verification: authenticated TEACHER, owner of room, room is ACTIVE, challenge exists and belongs to room
        ChallengeResponse challenge = challengeService.getChallengeForBroadcast(
                principal.getName(),
                roomCode,
                message.getChallengeId()
        );

        messagingTemplate.convertAndSend("/topic/room/" + roomCode.trim().toUpperCase() + "/challenge", challenge);
    }
}
