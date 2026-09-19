package com.codepulse.service;

import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import com.codepulse.entity.User;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;

@Service
public class RoomService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RoomService(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RoomResponse createRoom(String teacherEmail, CreateRoomRequest request) {
        User teacher = getUserByEmail(teacherEmail);
        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can create rooms");
        }

        String roomCode = generateUniqueRoomCode();

        Room room = Room.builder()
                .title(request.getTitle().trim())
                .roomCode(roomCode)
                .teacher(teacher)
                .status(RoomStatus.ACTIVE)
                .build();

        Room savedRoom = roomRepository.save(room);
        return RoomResponse.fromEntity(savedRoom);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(String teacherEmail) {
        User teacher = getUserByEmail(teacherEmail);
        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can view their rooms");
        }

        List<Room> rooms = roomRepository.findByTeacherId(teacher.getId());
        return rooms.stream()
                .map(RoomResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse joinRoom(String studentEmail, String roomCode) {
        User student = getUserByEmail(studentEmail);
        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can join rooms");
        }

        Room room = getRoomByCode(roomCode);
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join room. Room is closed.");
        }

        return RoomResponse.fromEntity(room);
    }

    @Transactional
    public RoomResponse closeRoom(String teacherEmail, String roomCode) {
        User teacher = getUserByEmail(teacherEmail);
        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers can close rooms");
        }

        Room room = getRoomByCode(roomCode);
        if (!room.getTeacher().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to close this room");
        }

        room.setStatus(RoomStatus.CLOSED);
        Room savedRoom = roomRepository.save(room);
        return RoomResponse.fromEntity(savedRoom);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Room getRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found with code: " + roomCode));
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                int index = secureRandom.nextInt(ROOM_CODE_CHARS.length());
                sb.append(ROOM_CODE_CHARS.charAt(index));
            }
            String code = sb.toString();
            if (!roomRepository.existsByRoomCode(code)) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate unique room code");
    }
}
