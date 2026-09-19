package com.codepulse.service;

import com.codepulse.dto.ChallengeResponse;
import com.codepulse.dto.CreateChallengeRequest;
import com.codepulse.entity.Challenge;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import com.codepulse.entity.User;
import com.codepulse.repository.ChallengeRepository;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public ChallengeService(
            ChallengeRepository challengeRepository,
            RoomRepository roomRepository,
            UserRepository userRepository
    ) {
        this.challengeRepository = challengeRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChallengeResponse createChallenge(String teacherEmail, String roomCode, CreateChallengeRequest request) {
        User teacher = getUserByEmail(teacherEmail);
        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can create challenges");
        }

        Room room = getRoomByCode(roomCode);
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create challenges in a closed room");
        }

        if (!room.getTeacher().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to create challenges for this room");
        }

        Challenge challenge = Challenge.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .skeleton(request.getSkeleton())
                .timeLimit(request.getTimeLimit())
                .room(room)
                .build();

        Challenge savedChallenge = challengeRepository.save(challenge);
        return ChallengeResponse.fromEntity(savedChallenge);
    }

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getRoomChallenges(String userEmail, String roomCode) {
        // Verify user exists and room exists
        getUserByEmail(userEmail);
        Room room = getRoomByCode(roomCode);

        List<Challenge> challenges = challengeRepository.findByRoomId(room.getId());
        return challenges.stream()
                .map(ChallengeResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ChallengeResponse getChallengeForBroadcast(String teacherEmail, String roomCode, Long challengeId) {
        if (challengeId == null) {
            throw new AccessDeniedException("Challenge ID cannot be null");
        }

        User teacher = userRepository.findByEmail(teacherEmail.toLowerCase().trim())
                .orElseThrow(() -> new AccessDeniedException("Teacher not found: " + teacherEmail));

        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Forbidden: Only teachers can broadcast challenges");
        }

        Room room = roomRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new AccessDeniedException("Room not found: " + roomCode));

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new AccessDeniedException("Forbidden: Cannot broadcast challenge in a closed room");
        }

        if (!room.getTeacher().getId().equals(teacher.getId())) {
            throw new AccessDeniedException("Forbidden: You are not the owner of this room");
        }

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new AccessDeniedException("Challenge not found with ID: " + challengeId));

        if (!challenge.getRoom().getId().equals(room.getId())) {
            throw new AccessDeniedException("Forbidden: Challenge does not belong to room " + roomCode);
        }

        if (challenge.getStartedAt() == null) {
            challenge.setStartedAt(LocalDateTime.now());
            challenge = challengeRepository.save(challenge);
        }

        return ChallengeResponse.fromEntity(challenge);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Room getRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found with code: " + roomCode));
    }
}
