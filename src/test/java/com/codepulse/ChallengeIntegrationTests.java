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
}
