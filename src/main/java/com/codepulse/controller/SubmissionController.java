package com.codepulse.controller;

import com.codepulse.dto.SubmissionResponse;
import com.codepulse.dto.SubmitCodeRequest;
import com.codepulse.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
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
}
