package com.codepulse.controller;

import com.codepulse.dto.ChallengeResponse;
import com.codepulse.dto.SubmissionResponse;
import com.codepulse.dto.SubmitCodeRequest;
import com.codepulse.service.ChallengeService;
import com.codepulse.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/challenges")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final ChallengeService challengeService;

    public SubmissionController(SubmissionService submissionService, ChallengeService challengeService) {
        this.submissionService = submissionService;
        this.challengeService = challengeService;
    }

    @PostMapping("/{challengeId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> submitCode(
            @PathVariable Long challengeId,
            @Valid @RequestBody SubmitCodeRequest request,
            Principal principal
    ) {
        SubmissionResponse response = submissionService.submitCode(principal.getName(), challengeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{challengeId}/end")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ChallengeResponse> endChallenge(
            @PathVariable Long challengeId,
            Principal principal
    ) {
        ChallengeResponse response = challengeService.endChallenge(principal.getName(), challengeId);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{challengeId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteChallenge(
            @PathVariable Long challengeId,
            Principal principal
    ) {
        challengeService.deleteChallenge(principal.getName(), challengeId);
        return ResponseEntity.noContent().build();
    }
}
