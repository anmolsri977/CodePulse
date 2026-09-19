package com.codepulse.repository;

import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    List<Room> findByTeacherId(Long teacherId);

    List<Room> findByStatus(RoomStatus status);
}
