package com.codepulse.controller;

import com.codepulse.dto.ChallengeResponse;
import com.codepulse.dto.CreateChallengeRequest;
import com.codepulse.service.ChallengeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rooms/{roomCode}/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ChallengeResponse> createChallenge(
            @PathVariable String roomCode,
            @Valid @RequestBody CreateChallengeRequest request,
            Principal principal
    ) {
        ChallengeResponse response = challengeService.createChallenge(principal.getName(), roomCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    public ResponseEntity<List<ChallengeResponse>> getRoomChallenges(
            @PathVariable String roomCode,
            Principal principal
    ) {
        List<ChallengeResponse> challenges = challengeService.getRoomChallenges(principal.getName(), roomCode);
        return ResponseEntity.ok(challenges);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{challengeId}/end")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ChallengeResponse> endChallenge(
            @PathVariable String roomCode,
            @PathVariable Long challengeId,
            Principal principal
    ) {
        ChallengeResponse response = challengeService.endChallenge(principal.getName(), challengeId);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{challengeId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteChallenge(
            @PathVariable String roomCode,
            @PathVariable Long challengeId,
            Principal principal
    ) {
        challengeService.deleteChallenge(principal.getName(), challengeId);
        return ResponseEntity.noContent().build();
    }
}
