package com.codepulse.controller;

import com.codepulse.dto.SubmissionDetailResponse;
import com.codepulse.service.SubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionQueryController {

    private final SubmissionService submissionService;

    public SubmissionQueryController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<SubmissionDetailResponse> getSubmission(
            @PathVariable Long submissionId,
            Principal principal
    ) {
        SubmissionDetailResponse response = submissionService.getSubmissionById(principal.getName(), submissionId);
        return ResponseEntity.ok(response);
    }
}
