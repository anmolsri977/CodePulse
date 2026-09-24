package com.codepulse.service;

import com.codepulse.dto.GeminiReviewResult;
import com.codepulse.dto.SubmissionDetailResponse;
import com.codepulse.dto.SubmissionResponse;
import com.codepulse.dto.SubmitCodeRequest;
import com.codepulse.entity.Challenge;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import com.codepulse.entity.Submission;
import com.codepulse.entity.User;
import com.codepulse.repository.ChallengeRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.UserRepository;
import com.codepulse.dto.SubmissionStatusMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final SubmissionBroadcastService submissionBroadcastService;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            ChallengeRepository challengeRepository,
            UserRepository userRepository,
            GeminiService geminiService,
            SubmissionBroadcastService submissionBroadcastService
    ) {
        this.submissionRepository = submissionRepository;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.geminiService = geminiService;
        this.submissionBroadcastService = submissionBroadcastService;
    }

    @Transactional
    public SubmissionResponse submitCode(String studentEmail, Long challengeId, SubmitCodeRequest request) {
        User student = userRepository.findByEmail(studentEmail.toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can submit code for challenges");
        }

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found with ID: " + challengeId));

        if (challenge.getRoom() == null || challenge.getRoom().getStatus() == RoomStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot submit code to a closed room challenge");
        }

        // Enforce challenge manual termination
        if (challenge.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge has been ended by the teacher. Submissions are closed.");
        }

        // Enforce challenge deadline if started and timeLimit is set
        if (challenge.getStartedAt() != null && challenge.getTimeLimit() != null && challenge.getTimeLimit() > 0) {
            LocalDateTime deadline = challenge.getStartedAt().plusMinutes(challenge.getTimeLimit());
            // Allow 10-second grace period for network transmission latency
            if (LocalDateTime.now(ZoneOffset.UTC).isAfter(deadline.plusSeconds(10))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge time limit has expired. Submissions are closed.");
            }
        }

        // Call Gemini AI for review; gracefully handles failures and returns fallback if unavailable
        GeminiReviewResult reviewResult;
        try {
            reviewResult = geminiService.reviewCode(
                    challenge.getTitle(),
                    challenge.getDescription(),
                    request.getCode()
            );
        } catch (Exception e) {
            reviewResult = new GeminiReviewResult(null, "AI review is currently unavailable. Your code has been saved successfully.");
        }

        Submission submission = Submission.builder()
                .code(request.getCode())
                .score(reviewResult != null ? reviewResult.getScore() : null)
                .aiFeedback(reviewResult != null ? reviewResult.getFeedback() : "Submission recorded.")
                .student(student)
                .challenge(challenge)
                .build();

        Submission savedSubmission = submissionRepository.save(submission);

        // Broadcast submission status event to /topic/room/{roomCode}/submissions after successful persistence
        if (challenge.getRoom() != null && challenge.getRoom().getRoomCode() != null) {
            SubmissionStatusMessage statusMessage = SubmissionStatusMessage.builder()
                    .submissionId(savedSubmission.getId())
                    .studentId(student.getId())
                    .studentName(student.getName())
                    .challengeId(challenge.getId())
                    .score(savedSubmission.getScore())
                    .submittedAt(savedSubmission.getSubmittedAt())
                    .build();
            submissionBroadcastService.broadcastSubmissionStatus(challenge.getRoom().getRoomCode(), statusMessage);
        }

        return SubmissionResponse.fromEntity(savedSubmission);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmissionById(String userEmail, Long submissionId) {
        User user = userRepository.findByEmail(userEmail.toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found with ID: " + submissionId));

        if (user.getRole() == Role.TEACHER) {
            Room room = submission.getChallenge().getRoom();
            if (room == null || !room.getTeacher().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You are not the owner of the classroom for this submission");
            }
        } else if (user.getRole() == Role.STUDENT) {
            if (!submission.getStudent().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You can only view your own submission");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Unauthorized role");
        }

        return SubmissionDetailResponse.fromEntity(submission);
    }
}
