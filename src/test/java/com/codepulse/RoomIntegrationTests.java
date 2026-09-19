package com.codepulse;

import com.codepulse.dto.AuthResponse;
import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.RegisterRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RoomIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private com.codepulse.repository.ChallengeRepository challengeRepository;

    @Autowired
    private com.codepulse.repository.SubmissionRepository submissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String teacherToken1;
    private String teacherToken2;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        challengeRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        // Register Teacher 1
        teacherToken1 = registerUser("Prof McGonagall", "mcgonagall@codepulse.com", "transfiguration1", Role.TEACHER);

        // Register Teacher 2
        teacherToken2 = registerUser("Prof Flitwick", "flitwick@codepulse.com", "charmsMaster2", Role.TEACHER);

        // Register Student
        studentToken = registerUser("Ron Weasley", "ron@codepulse.com", "spiderHater3", Role.STUDENT);
    }

    private String registerUser(String name, String email, String password, Role role) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name(name)
                .email(email)
                .password(password)
                .role(role)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
        return authResponse.getToken();
    }

    @Test
    @DisplayName("Teacher successfully creates a room with unique 6-char roomCode")
    void testTeacherCreateRoom() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .title("Advanced Transfiguration 101")
                .build();

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + teacherToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Advanced Transfiguration 101"))
                .andExpect(jsonPath("$.roomCode", matchesPattern("^[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.teacherName").value("Prof McGonagall"))
                .andExpect(jsonPath("$.teacherEmail").value("mcgonagall@codepulse.com"))
                .andReturn();

        RoomResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), RoomResponse.class);
        assertEquals(6, response.getRoomCode().length());

        Room savedRoom = roomRepository.findByRoomCode(response.getRoomCode()).orElse(null);
        assertNotNull(savedRoom);
        assertEquals("Advanced Transfiguration 101", savedRoom.getTitle());
    }

    @Test
    @DisplayName("Teacher fetches their created rooms via GET /api/rooms/my")
    void testTeacherGetMyRooms() throws Exception {
        // Create two rooms under Teacher 1
        createRoom(teacherToken1, "Room Alpha");
        createRoom(teacherToken1, "Room Beta");

        // Create one room under Teacher 2
        createRoom(teacherToken2, "Room Gamma");

        // Teacher 1 should only see their 2 rooms
        mockMvc.perform(get("/api/rooms/my")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].teacherEmail").value("mcgonagall@codepulse.com"))
                .andExpect(jsonPath("$[1].teacherEmail").value("mcgonagall@codepulse.com"));
    }

    @Test
    @DisplayName("Student joins an ACTIVE room using roomCode")
    void testStudentJoinActiveRoom() throws Exception {
        RoomResponse created = createRoom(teacherToken1, "Charms Practical");

        mockMvc.perform(post("/api/rooms/join/" + created.getRoomCode())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(created.getRoomCode()))
                .andExpect(jsonPath("$.title").value("Charms Practical"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Student cannot join a CLOSED room")
    void testStudentJoinClosedRoomFails() throws Exception {
        RoomResponse created = createRoom(teacherToken1, "Closed Study Group");

        // Close the room
        mockMvc.perform(patch("/api/rooms/" + created.getRoomCode() + "/close")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Attempt join by student
        mockMvc.perform(post("/api/rooms/join/" + created.getRoomCode())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Student cannot join a nonexistent room")
    void testStudentJoinNonexistentRoomFails() throws Exception {
        mockMvc.perform(post("/api/rooms/join/NOEXST")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unauthorized access: requests without JWT are rejected with 401")
    void testUnauthenticatedAccessFails() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder().title("Secret Room").build();

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rooms/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/rooms/join/ANY123"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/rooms/ANY123/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Role enforcement: Student cannot create room, Teacher cannot join room")
    void testRoleEnforcement() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder().title("Student Hack").build();

        // Student tries to create room -> 403
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Teacher 1 creates room
        RoomResponse room = createRoom(teacherToken1, "Lecture Hall A");

        // Teacher tries to join room -> 403
        mockMvc.perform(post("/api/rooms/join/" + room.getRoomCode())
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teacher closes their room successfully")
    void testTeacherClosesRoom() throws Exception {
        RoomResponse created = createRoom(teacherToken1, "Room To Close");

        mockMvc.perform(patch("/api/rooms/" + created.getRoomCode() + "/close")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.roomCode").value(created.getRoomCode()));

        Room roomInDb = roomRepository.findByRoomCode(created.getRoomCode()).orElseThrow();
        assertEquals(RoomStatus.CLOSED, roomInDb.getStatus());
    }

    @Test
    @DisplayName("Teacher cannot close another teacher's room (403 Forbidden)")
    void testTeacherCannotCloseAnotherTeacherRoom() throws Exception {
        // Created by Teacher 1
        RoomResponse created = createRoom(teacherToken1, "Teacher 1 Exclusive");

        // Teacher 2 tries to close Teacher 1's room
        mockMvc.perform(patch("/api/rooms/" + created.getRoomCode() + "/close")
                        .header("Authorization", "Bearer " + teacherToken2))
                .andExpect(status().isForbidden());

        // Verify status remains ACTIVE
        Room roomInDb = roomRepository.findByRoomCode(created.getRoomCode()).orElseThrow();
        assertEquals(RoomStatus.ACTIVE, roomInDb.getStatus());
    }

    @Test
    @DisplayName("Closing nonexistent room returns 404 Not Found")
    void testCloseNonexistentRoomReturns404() throws Exception {
        mockMvc.perform(patch("/api/rooms/ZZZZZZ/close")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isNotFound());
    }

    private RoomResponse createRoom(String teacherToken, String title) throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder().title(title).build();

        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), RoomResponse.class);
    }
}
