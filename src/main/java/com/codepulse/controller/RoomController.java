package com.codepulse.controller;

import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Principal principal
    ) {
        RoomResponse response = roomService.createRoom(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<RoomResponse>> getMyRooms(Principal principal) {
        List<RoomResponse> rooms = roomService.getMyRooms(principal.getName());
        return ResponseEntity.ok(rooms);
    }

    @PostMapping("/join/{roomCode}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<RoomResponse> joinRoom(
            @PathVariable String roomCode,
            Principal principal
    ) {
        RoomResponse response = roomService.joinRoom(principal.getName(), roomCode);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{roomCode}/close")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<RoomResponse> closeRoom(
            @PathVariable String roomCode,
            Principal principal
    ) {
        RoomResponse response = roomService.closeRoom(principal.getName(), roomCode);
        return ResponseEntity.ok(response);
    }
}
