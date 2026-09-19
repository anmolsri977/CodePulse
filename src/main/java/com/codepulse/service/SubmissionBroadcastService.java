package com.codepulse.service;

import com.codepulse.dto.SubmissionStatusMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SubmissionBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public SubmissionBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastSubmissionStatus(String roomCode, SubmissionStatusMessage message) {
        if (roomCode != null && !roomCode.trim().isEmpty() && message != null) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode.trim().toUpperCase() + "/submissions",
                    message
            );
        }
    }
}
