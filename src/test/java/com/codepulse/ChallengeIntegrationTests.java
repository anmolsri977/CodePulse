package com.codepulse;

import com.codepulse.dto.AuthResponse;
import com.codepulse.dto.ChallengeResponse;
import com.codepulse.dto.CreateChallengeRequest;
import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.RegisterRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.dto.StartChallengeMessage;
import com.codepulse.entity.Challenge;
import com.codepulse.entity.Role;
import com.codepulse.repository.ChallengeRepository;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class ChallengeIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private com.codepulse.service.ChallengeService challengeService;

    @Autowired
    private com.codepulse.repository.SubmissionRepository submissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private final List<StompSession> activeSessions = new ArrayList<>();

    private String teacherToken1;
    private String teacherToken2;
    private String studentToken;
    private String roomCode1;
    private String roomCode2;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        challengeRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        // Register Teacher 1, Teacher 2, and Student
        teacherToken1 = registerUser("Teacher Quirrell", "quirrell@codepulse.com", "darkArts101", Role.TEACHER);
        teacherToken2 = registerUser("Teacher Sprout", "sprout@codepulse.com", "herbology202", Role.TEACHER);
        studentToken = registerUser("Student Neville", "neville@codepulse.com", "remembrall303", Role.STUDENT);

        // Create Room for Teacher 1 and Room for Teacher 2
        roomCode1 = createRoom(teacherToken1, "Defense Against Dark Arts").getRoomCode();
        roomCode2 = createRoom(teacherToken2, "Herbology Greenhouse").getRoomCode();
    }

    @AfterEach
    void tearDown() {
        for (StompSession session : activeSessions) {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
        activeSessions.clear();
        stompClient.stop();
    }

    @Test
    @DisplayName("1. Teacher creates challenge for their active room")
    void testTeacherCreatesChallenge() throws Exception {
        CreateChallengeRequest request = CreateChallengeRequest.builder()
                .title("Reverse a String")
                .description("Write a function to reverse a string in-place.")
                .skeleton("public String reverse(String str) { return null; }")
                .timeLimit(30)
                .build();

        mockMvc.perform(post("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + teacherToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Reverse a String"))
                .andExpect(jsonPath("$.description").value("Write a function to reverse a string in-place."))
                .andExpect(jsonPath("$.skeleton").value("public String reverse(String str) { return null; }"))
                .andExpect(jsonPath("$.timeLimit").value(30))
                .andExpect(jsonPath("$.roomCode").value(roomCode1));
    }

    @Test
    @DisplayName("2. Teacher and student retrieve room challenges")
    void testTeacherAndStudentRetrieveRoomChallenges() throws Exception {
        // Create 2 challenges in room 1
        createChallenge(teacherToken1, roomCode1, "Challenge A", 15);
        createChallenge(teacherToken1, roomCode1, "Challenge B", 25);

        // Teacher retrieves challenges
        mockMvc.perform(get("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Challenge A"))
                .andExpect(jsonPath("$[1].title").value("Challenge B"));

        // Student retrieves challenges
        mockMvc.perform(get("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Challenge A"));
    }

    @Test
    @DisplayName("3. Student cannot create challenge (403 Forbidden)")
    void testStudentCannotCreateChallenge() throws Exception {
        CreateChallengeRequest request = CreateChallengeRequest.builder()
                .title("Student Challenge")
                .description("Attempted by student")
                .build();

        mockMvc.perform(post("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Teacher cannot create challenge in another teacher's room (403 Forbidden)")
    void testTeacherCannotCreateChallengeInAnotherTeacherRoom() throws Exception {
        CreateChallengeRequest request = CreateChallengeRequest.builder()
                .title("Intrusion Challenge")
                .description("Teacher 2 invading Teacher 1's room")
                .build();

        mockMvc.perform(post("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + teacherToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Student receives challenge through WebSocket broadcast")
    void testStudentReceivesBroadcastChallenge() throws Exception {
        // Teacher creates challenge
        ChallengeResponse challenge = createChallenge(teacherToken1, roomCode1, "Binary Search", 45);

        // Connect Teacher 1 and Student via STOMP
        StompSession teacherSession = connectWithToken(teacherToken1);
        StompSession studentSession = connectWithToken(studentToken);

        BlockingQueue<ChallengeResponse> receivedQueue = new LinkedBlockingDeque<>();

        // Student subscribes to /topic/room/{roomCode}/challenge
        studentSession.subscribe("/topic/room/" + roomCode1 + "/challenge", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChallengeResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedQueue.offer((ChallengeResponse) payload);
            }
        });

        Thread.sleep(500);

        // Teacher broadcasts challenge by ID only
        StartChallengeMessage message = StartChallengeMessage.builder()
                .challengeId(challenge.getId())
                .build();

        teacherSession.send("/app/room/" + roomCode1 + "/challenge", message);

        // Verify student receives full ChallengeResponse from server
        ChallengeResponse received = receivedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "Student must receive the broadcast challenge");
        assertEquals(challenge.getId(), received.getId());
        assertEquals("Binary Search", received.getTitle());
        assertEquals(45, received.getTimeLimit());
        assertEquals(roomCode1, received.getRoomCode());
        assertNotNull(received.getStartedAt(), "startedAt must be present in broadcast response");

        // Verify startedAt is persisted in database
        Challenge persisted = challengeRepository.findById(challenge.getId()).orElseThrow();
        assertNotNull(persisted.getStartedAt(), "startedAt must be persisted in database");
    }

    @Test
    @DisplayName("6. Unauthorized/invalid requests are rejected")
    void testUnauthorizedAndInvalidRequestsRejected() throws Exception {
        // 6a. Unauthenticated REST requests -> 401
        CreateChallengeRequest request = CreateChallengeRequest.builder().title("Unauth").build();
        mockMvc.perform(post("/api/rooms/" + roomCode1 + "/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rooms/" + roomCode1 + "/challenges"))
                .andExpect(status().isUnauthorized());

        // 6b. Creating challenge in CLOSED room -> 400 Bad Request
        mockMvc.perform(patch("/api/rooms/" + roomCode1 + "/close")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + teacherToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // 6c. Nonexistent room -> 404 Not Found
        mockMvc.perform(post("/api/rooms/NONEX1/challenges")
                        .header("Authorization", "Bearer " + teacherToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        // 6d. Student attempting to broadcast challenge over WebSocket -> rejected, no broadcast
        ChallengeResponse challenge2 = createChallenge(teacherToken2, roomCode2, "Herbology Quiz", 20);

        StompSession studentSession = connectWithToken(studentToken);
        StompSession teacher2Session = connectWithToken(teacherToken2);

        BlockingQueue<ChallengeResponse> receivedQueue = new LinkedBlockingDeque<>();
        studentSession.subscribe("/topic/room/" + roomCode2 + "/challenge", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChallengeResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedQueue.offer((ChallengeResponse) payload);
            }
        });

        Thread.sleep(500);

        // Student tries to send broadcast message
        studentSession.send("/app/room/" + roomCode2 + "/challenge", StartChallengeMessage.builder().challengeId(challenge2.getId()).build());
        ChallengeResponse shouldBeNull = receivedQueue.poll(2, TimeUnit.SECONDS);
        assertNull(shouldBeNull, "Student must not be allowed to broadcast challenges");

        // Teacher 1 tries to broadcast challenge in Teacher 2's room -> rejected
        StompSession teacher1Session = connectWithToken(teacherToken1);
        teacher1Session.send("/app/room/" + roomCode2 + "/challenge", StartChallengeMessage.builder().challengeId(challenge2.getId()).build());
        ChallengeResponse shouldBeNull2 = receivedQueue.poll(2, TimeUnit.SECONDS);
        assertNull(shouldBeNull2, "Teacher 1 must not be allowed to broadcast in Teacher 2's room");
    }

    private StompSession connectWithToken(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }

        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        future.complete(session);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        future.completeExceptionally(exception);
                    }

                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                        exception.printStackTrace();
                    }
                }
        );

        StompSession session = future.get(5, TimeUnit.SECONDS);
        activeSessions.add(session);
        return session;
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

    private ChallengeResponse createChallenge(String teacherToken, String roomCode, String title, int timeLimit) throws Exception {
        CreateChallengeRequest request = CreateChallengeRequest.builder()
                .title(title)
                .description("Solve " + title)
                .skeleton("// skeleton for " + title)
                .timeLimit(timeLimit)
                .build();

        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomCode + "/challenges")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), ChallengeResponse.class);
    }

    @Test
    @DisplayName("7. New challenge starts with fresh startedAt and serializes with explicit UTC Z indicator")
    void testNewChallengeStartsAndSerializesExplicitUtc() throws Exception {
        // Teacher creates challenge
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Merge Sort", 20);
        assertNull(created.getStartedAt(), "startedAt should be null upon initial creation");

        // Teacher starts the challenge
        ChallengeResponse broadcast = challengeService.getChallengeForBroadcast(
                "quirrell@codepulse.com",
                roomCode1,
                created.getId()
        );

        assertNotNull(broadcast.getStartedAt(), "startedAt must be populated when challenge is started");
        assertNotNull(broadcast.getCreatedAt(), "createdAt must be present");

        // Verify JSON serialization produces explicit UTC 'Z' suffix
        String json = objectMapper.writeValueAsString(broadcast);
        assertTrue(json.contains("\"startedAt\":"), "JSON must contain startedAt field");
        assertTrue(json.matches(".*\"startedAt\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\".*"),
                "startedAt JSON must explicitly end with UTC 'Z' indicator: " + json);
        assertTrue(json.matches(".*\"createdAt\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\".*"),
                "createdAt JSON must explicitly end with UTC 'Z' indicator: " + json);
    }

    @Test
    @DisplayName("8. Expired challenge restarted by teacher receives a new startedAt")
    void testExpiredChallengeRestartedByTeacherReceivesNewStartedAt() throws Exception {
        // Teacher creates challenge with 10 minute time limit
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Dijkstra Algorithm", 10);

        // Manually simulate that this challenge was started 40 minutes ago (expired)
        LocalDateTime oldStartedAt = LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(40).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Challenge challengeEntity = challengeRepository.findById(created.getId()).orElseThrow();
        challengeEntity.setStartedAt(oldStartedAt);
        challengeRepository.save(challengeEntity);

        // Teacher restarts/broadcasts the challenge again
        ChallengeResponse restarted = challengeService.getChallengeForBroadcast(
                "quirrell@codepulse.com",
                roomCode1,
                created.getId()
        );

        assertNotNull(restarted.getStartedAt());
        assertTrue(restarted.getStartedAt().isAfter(oldStartedAt.plusMinutes(10)),
                "Restarted challenge must receive a brand new startedAt later than the expired deadline");

        // Verify updated startedAt is persisted in repository
        Challenge reloaded = challengeRepository.findById(created.getId()).orElseThrow();
        assertTrue(reloaded.getStartedAt().isAfter(oldStartedAt.plusMinutes(10)),
                "Persisted entity must also have the new startedAt");
    }

    @Test
    @DisplayName("9. Non-expired active challenge does not unexpectedly reset its startedAt on re-broadcast")
    void testNonExpiredChallengeDoesNotResetStartedAt() throws Exception {
        // Teacher creates challenge with 30 minute time limit
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Graph BFS", 30);

        // Simulate challenge started 5 minutes ago (still 25 minutes remaining, not expired)
        LocalDateTime existingStartedAt = LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(5).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Challenge challengeEntity = challengeRepository.findById(created.getId()).orElseThrow();
        challengeEntity.setStartedAt(existingStartedAt);
        challengeRepository.save(challengeEntity);

        // Teacher re-broadcasts the challenge to inform late-joining students
        ChallengeResponse rebroadcast = challengeService.getChallengeForBroadcast(
                "quirrell@codepulse.com",
                roomCode1,
                created.getId()
        );

        assertEquals(existingStartedAt, rebroadcast.getStartedAt(),
                "Non-expired challenge must preserve its existing startedAt upon re-broadcast");
    }

    @Test
    @DisplayName("10. 2-minute challenge under IST JVM timezone calculates ~120s remaining, not 332 minutes")
    void testTwoMinuteChallengeUnderIstJvmTimezone() throws Exception {
        java.util.TimeZone originalJvmTz = java.util.TimeZone.getDefault();
        try {
            // Explicitly set JVM timezone to Asia/Kolkata (IST = UTC+05:30)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));

            ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Quick Sort", 2);
            ChallengeResponse broadcast = challengeService.getChallengeForBroadcast(
                    "quirrell@codepulse.com",
                    roomCode1,
                    created.getId()
            );

            assertNotNull(broadcast.getStartedAt());
            assertEquals(2, broadcast.getTimeLimit());

            // Serialize to JSON and parse as UTC Instant
            String json = objectMapper.writeValueAsString(broadcast);
            assertTrue(json.contains("\"startedAt\":"));

            // Parse startedAt as UTC Instant
            java.time.Instant startedAtInstant = broadcast.getStartedAt().toInstant(java.time.ZoneOffset.UTC);
            long nowEpochMilli = java.time.Instant.now().toEpochMilli();
            long deadlineEpochMilli = startedAtInstant.toEpochMilli() + (2 * 60 * 1000L);
            long remainingSeconds = (deadlineEpochMilli - nowEpochMilli) / 1000L;

            // Must be within ~115 to 120 seconds, definitely NOT 330+ minutes (~19920 seconds)
            assertTrue(remainingSeconds >= 115 && remainingSeconds <= 120,
                    "Remaining seconds must be ~120s, but was: " + remainingSeconds + " (should not have +5h30m offset)");

            // Verify backend expiry logic also evaluates active
            LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
            boolean isExpired = nowUtc.isAfter(broadcast.getStartedAt().plusMinutes(broadcast.getTimeLimit()));
            org.junit.jupiter.api.Assertions.assertFalse(isExpired, "2-minute challenge just started should NOT be expired");
        } finally {
            java.util.TimeZone.setDefault(originalJvmTz);
        }
    }

    @Test
    @DisplayName("11. Teacher ends active challenge successfully via PATCH /api/challenges/{id}/end")
    void testTeacherEndsChallengeSuccessfully() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Binary Search", 15);
        challengeService.getChallengeForBroadcast("quirrell@codepulse.com", roomCode1, created.getId());

        MvcResult result = mockMvc.perform(patch("/api/challenges/" + created.getId() + "/end")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.endedAt").isNotEmpty())
                .andReturn();

        ChallengeResponse endedResponse = objectMapper.readValue(result.getResponse().getContentAsString(), ChallengeResponse.class);
        assertNotNull(endedResponse.getEndedAt());

        // Verify persisted entity has endedAt set
        Challenge reloaded = challengeRepository.findById(created.getId()).orElseThrow();
        assertNotNull(reloaded.getEndedAt());
    }

    @Test
    @DisplayName("12. Student cannot end challenge (403 Forbidden)")
    void testStudentCannotEndChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Stack Implementation", 15);

        mockMvc.perform(patch("/api/challenges/" + created.getId() + "/end")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("13. Teacher cannot end challenge in another teacher's room (403 Forbidden)")
    void testTeacherCannotEndAnotherTeacherChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Queue Implementation", 15);

        // Teacher 2 tries to end Teacher 1's challenge
        mockMvc.perform(patch("/api/challenges/" + created.getId() + "/end")
                        .header("Authorization", "Bearer " + teacherToken2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("14. Teacher restarting an ended challenge resets endedAt and sets new startedAt")
    void testRestartingEndedChallengeResetsEndedAt() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Heap Sort", 10);
        challengeService.getChallengeForBroadcast("quirrell@codepulse.com", roomCode1, created.getId());

        // End the challenge
        challengeService.endChallenge("quirrell@codepulse.com", created.getId());
        Challenge endedEntity = challengeRepository.findById(created.getId()).orElseThrow();
        assertNotNull(endedEntity.getEndedAt());

        // Restart the challenge
        ChallengeResponse restarted = challengeService.getChallengeForBroadcast("quirrell@codepulse.com", roomCode1, created.getId());
        assertNull(restarted.getEndedAt(), "restarted challenge must have null endedAt");
        assertNotNull(restarted.getStartedAt(), "restarted challenge must have fresh startedAt");

        Challenge reloaded = challengeRepository.findById(created.getId()).orElseThrow();
        assertNull(reloaded.getEndedAt(), "persisted entity must have null endedAt");
    }

    @Test
    @DisplayName("15. Teacher can delete own ended challenge")
    void testTeacherCanDeleteEndedChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Bubble Sort", 10);
        challengeService.getChallengeForBroadcast("quirrell@codepulse.com", roomCode1, created.getId());
        challengeService.endChallenge("quirrell@codepulse.com", created.getId());

        mockMvc.perform(delete("/api/challenges/" + created.getId())
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isNoContent());

        assertTrue(challengeRepository.findById(created.getId()).isEmpty(), "Challenge must be deleted from repository");
    }

    @Test
    @DisplayName("16. Teacher can delete own expired challenge")
    void testTeacherCanDeleteExpiredChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Insertion Sort", 10);
        Challenge challengeEntity = challengeRepository.findById(created.getId()).orElseThrow();
        challengeEntity.setStartedAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(25));
        challengeRepository.save(challengeEntity);

        mockMvc.perform(delete("/api/challenges/" + created.getId())
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isNoContent());

        assertTrue(challengeRepository.findById(created.getId()).isEmpty(), "Expired challenge must be deleted");
    }

    @Test
    @DisplayName("17. Teacher cannot delete active challenge (400 Bad Request)")
    void testTeacherCannotDeleteActiveChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Active QuickSort", 30);
        // Start challenge (active)
        challengeService.getChallengeForBroadcast("quirrell@codepulse.com", roomCode1, created.getId());

        mockMvc.perform(delete("/api/challenges/" + created.getId())
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isBadRequest());

        assertTrue(challengeRepository.findById(created.getId()).isPresent(), "Active challenge must NOT be deleted");
    }

    @Test
    @DisplayName("18. Student receives 403 Forbidden when attempting to delete challenge")
    void testStudentCannotDeleteChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Radix Sort", 10);
        challengeService.endChallenge("quirrell@codepulse.com", created.getId());

        mockMvc.perform(delete("/api/challenges/" + created.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        assertTrue(challengeRepository.findById(created.getId()).isPresent(), "Challenge must still exist");
    }

    @Test
    @DisplayName("19. Teacher cannot delete another teacher's challenge (403 Forbidden)")
    void testTeacherCannotDeleteAnotherTeacherChallenge() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Counting Sort", 10);
        challengeService.endChallenge("quirrell@codepulse.com", created.getId());

        // Teacher 2 attempts to delete Teacher 1's challenge
        mockMvc.perform(delete("/api/challenges/" + created.getId())
                        .header("Authorization", "Bearer " + teacherToken2))
                .andExpect(status().isForbidden());

        assertTrue(challengeRepository.findById(created.getId()).isPresent(), "Challenge must still exist");
    }

    @Test
    @DisplayName("20. Associated submissions handled safely on challenge delete and challenge removed from list")
    void testSubmissionsDeletedWithChallengeAndChallengeListUpdated() throws Exception {
        ChallengeResponse created = createChallenge(teacherToken1, roomCode1, "Bucket Sort", 10);
        challengeService.getChallengeForBroadcast("quirrell@codepulse.com", roomCode1, created.getId());

        // Submit code as student Neville
        var studentUser = userRepository.findByEmail("neville@codepulse.com").orElseThrow();
        Challenge challengeEntity = challengeRepository.findById(created.getId()).orElseThrow();
        com.codepulse.entity.Submission submission = com.codepulse.entity.Submission.builder()
                .code("class Solution {}")
                .challenge(challengeEntity)
                .student(studentUser)
                .score(85)
                .aiFeedback("Good job")
                .build();
        submissionRepository.save(submission);

        assertEquals(1, submissionRepository.findByChallengeId(created.getId()).size(), "Precondition: 1 submission exists");

        // Teacher ends challenge
        challengeService.endChallenge("quirrell@codepulse.com", created.getId());

        // Delete the challenge
        mockMvc.perform(delete("/api/challenges/" + created.getId())
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isNoContent());

        // Verify challenge is deleted
        assertTrue(challengeRepository.findById(created.getId()).isEmpty());
        // Verify associated submissions are cleanly deleted without orphan or FK errors
        assertTrue(submissionRepository.findByChallengeId(created.getId()).isEmpty());

        // Verify challenge no longer appears in getRoomChallenges
        mockMvc.perform(get("/api/rooms/" + roomCode1 + "/challenges")
                        .header("Authorization", "Bearer " + teacherToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
