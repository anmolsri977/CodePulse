package com.codepulse;

import com.codepulse.dto.AuthResponse;
import com.codepulse.dto.CreateChallengeRequest;
import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.GeminiReviewResult;
import com.codepulse.dto.RegisterRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.dto.SubmissionResponse;
import com.codepulse.dto.SubmissionStatusMessage;
import com.codepulse.dto.SubmitCodeRequest;
import com.codepulse.entity.Challenge;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.User;
import com.codepulse.repository.ChallengeRepository;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.UserRepository;
import com.codepulse.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class SubmissionWsIntegrationTests {

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
    private SubmissionRepository submissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GeminiService geminiService;

    private WebSocketStompClient stompClient;
    private final List<StompSession> activeSessions = new ArrayList<>();

    private String teacherToken1;
    private String teacherToken2;
    private String studentToken;
    private String roomCode1;
    private String roomCode2;
    private Long challengeId1;
    private Long challengeId2;
    private User savedStudent;

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

        // Register Teacher 1 and Room 1
        teacherToken1 = registerUser("Teacher Albus", "albus@codepulse.com", "lemon_drop_123", Role.TEACHER);
        roomCode1 = createRoom(teacherToken1, "Transfiguration Advanced").getRoomCode();

        // Register Teacher 2 and Room 2
        teacherToken2 = registerUser("Teacher Minerva", "minerva@codepulse.com", "gryffindor_456", Role.TEACHER);
        roomCode2 = createRoom(teacherToken2, "Animagus Study").getRoomCode();

        // Register Student
        studentToken = registerUser("Student Harry", "harry@codepulse.com", "stag_patronus_789", Role.STUDENT);
        savedStudent = userRepository.findByEmail("harry@codepulse.com").orElseThrow();

        // Create Challenge 1 for Room 1
        Room room1 = roomRepository.findByRoomCode(roomCode1).orElseThrow();
        Challenge challenge1 = Challenge.builder()
                .title("Teacup to Gerbil")
                .description("Transform a porcelain teacup into a live gerbil.")
                .skeleton("// transformation code here")
                .timeLimit(30)
                .room(room1)
                .build();
        challengeId1 = challengeRepository.save(challenge1).getId();

        // Create Challenge 2 for Room 2
        Room room2 = roomRepository.findByRoomCode(roomCode2).orElseThrow();
        Challenge challenge2 = Challenge.builder()
                .title("Cat Transformation")
                .description("Demonstrate feline animagus form.")
                .skeleton("// animagus code here")
                .timeLimit(45)
                .room(room2)
                .build();
        challengeId2 = challengeRepository.save(challenge2).getId();
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
                        // ignore or handle
                    }
                }
        );

        StompSession session = future.get(5, TimeUnit.SECONDS);
        activeSessions.add(session);
        return session;
    }

    @Test
    @DisplayName("1 & 2. Student submission causes a WebSocket event containing persisted submission data")
    void testStudentSubmissionProducesPersistedWebSocketEvent() throws Exception {
        when(geminiService.reviewCode(anyString(), anyString(), anyString()))
                .thenReturn(new GeminiReviewResult(92, "Superb wandwork and concise transformation logic."));

        StompSession teacherSession = connectWithToken(teacherToken1);
        BlockingQueue<SubmissionStatusMessage> queue = new LinkedBlockingDeque<>();

        teacherSession.subscribe("/topic/room/" + roomCode1 + "/submissions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SubmissionStatusMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((SubmissionStatusMessage) payload);
            }
        });

        Thread.sleep(500);

        // Student submits code via REST
        SubmitCodeRequest request = SubmitCodeRequest.builder()
                .code("public class Transform { public void morph() { System.out.println(\"Gerbil\"); } }")
                .build();

        MvcResult result = mockMvc.perform(post("/api/challenges/" + challengeId1 + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        SubmissionResponse restResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                SubmissionResponse.class
        );

        // Verify WebSocket event received
        SubmissionStatusMessage event = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Teacher should receive real-time submission status via WebSocket");
        assertEquals(restResponse.getId(), event.getSubmissionId());
        assertEquals(savedStudent.getId(), event.getStudentId());
        assertEquals("Student Harry", event.getStudentName());
        assertEquals(challengeId1, event.getChallengeId());
        assertEquals(92, event.getScore());
        assertNotNull(event.getSubmittedAt());
    }

    @Test
    @DisplayName("3. Clients cannot SEND fake events directly to broker destinations (/topic/**)")
    void testClientsCannotSendDirectlyToTopicDestinations() throws Exception {
        StompSession teacherSession = connectWithToken(teacherToken1);
        StompSession studentSession = connectWithToken(studentToken);

        BlockingQueue<SubmissionStatusMessage> queue = new LinkedBlockingDeque<>();
        teacherSession.subscribe("/topic/room/" + roomCode1 + "/submissions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SubmissionStatusMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((SubmissionStatusMessage) payload);
            }
        });

        Thread.sleep(500);

        // Rogue student attempts to send fake submission event directly to broker topic
        SubmissionStatusMessage fakeMessage = SubmissionStatusMessage.builder()
                .submissionId(9999L)
                .studentId(savedStudent.getId())
                .studentName("Impostor")
                .challengeId(challengeId1)
                .score(100)
                .submittedAt(LocalDateTime.now(java.time.ZoneOffset.UTC))
                .build();

        try {
            studentSession.send("/topic/room/" + roomCode1 + "/submissions", fakeMessage);
        } catch (Exception ignored) {
        }

        // Verify no message is broadcast on the topic
        SubmissionStatusMessage received = queue.poll(2, TimeUnit.SECONDS);
        assertNull(received, "Broker destination must reject direct client SEND frames; no fake event broadcast");
    }

    @Test
    @DisplayName("4. Events are isolated to the correct room topic")
    void testEventsAreIsolatedToCorrectRoomTopic() throws Exception {
        when(geminiService.reviewCode(anyString(), anyString(), anyString()))
                .thenReturn(new GeminiReviewResult(85, "Good attempt."));

        StompSession teacher1Session = connectWithToken(teacherToken1);
        StompSession teacher2Session = connectWithToken(teacherToken2);

        BlockingQueue<SubmissionStatusMessage> room1Queue = new LinkedBlockingDeque<>();
        BlockingQueue<SubmissionStatusMessage> room2Queue = new LinkedBlockingDeque<>();

        teacher1Session.subscribe("/topic/room/" + roomCode1 + "/submissions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SubmissionStatusMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                room1Queue.offer((SubmissionStatusMessage) payload);
            }
        });

        teacher2Session.subscribe("/topic/room/" + roomCode2 + "/submissions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SubmissionStatusMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                room2Queue.offer((SubmissionStatusMessage) payload);
            }
        });

        Thread.sleep(500);

        // Student submits code for Challenge 1 (which belongs to Room 1)
        SubmitCodeRequest request = SubmitCodeRequest.builder()
                .code("public class Code { }")
                .build();

        mockMvc.perform(post("/api/challenges/" + challengeId1 + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Room 1 receives the event
        SubmissionStatusMessage room1Event = room1Queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(room1Event, "Room 1 should receive the submission event");
        assertEquals(challengeId1, room1Event.getChallengeId());

        // Room 2 must NOT receive any event
        SubmissionStatusMessage room2Event = room2Queue.poll(2, TimeUnit.SECONDS);
        assertNull(room2Event, "Room 2 must NOT receive submission events from Room 1");
    }

    @Test
    @DisplayName("5. Gemini fallback submission still broadcasts event with persisted values")
    void testGeminiFallbackSubmissionBroadcastsCorrectly() throws Exception {
        // Mock Gemini failure
        when(geminiService.reviewCode(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini service timeout"));

        StompSession teacherSession = connectWithToken(teacherToken1);
        BlockingQueue<SubmissionStatusMessage> queue = new LinkedBlockingDeque<>();

        teacherSession.subscribe("/topic/room/" + roomCode1 + "/submissions", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SubmissionStatusMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((SubmissionStatusMessage) payload);
            }
        });

        Thread.sleep(500);

        // Student submits code
        SubmitCodeRequest request = SubmitCodeRequest.builder()
                .code("public class FallbackTest { }")
                .build();

        MvcResult result = mockMvc.perform(post("/api/challenges/" + challengeId1 + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        SubmissionResponse restResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                SubmissionResponse.class
        );

        // Verify WebSocket event is still broadcast with persisted fallback data (score = null)
        SubmissionStatusMessage event = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Fallback submission should still trigger WebSocket broadcast");
        assertEquals(restResponse.getId(), event.getSubmissionId());
        assertEquals(savedStudent.getId(), event.getStudentId());
        assertEquals("Student Harry", event.getStudentName());
        assertEquals(challengeId1, event.getChallengeId());
        assertNull(event.getScore(), "Score should be null for fallback submission");
        assertNotNull(event.getSubmittedAt());
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
}
