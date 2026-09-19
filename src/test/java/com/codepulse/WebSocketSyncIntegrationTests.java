package com.codepulse;

import com.codepulse.dto.AuthResponse;
import com.codepulse.dto.CodeSyncMessage;
import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.RegisterRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.entity.Role;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class WebSocketSyncIntegrationTests {

    @LocalServerPort
    private int port;

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

    private WebSocketStompClient stompClient;
    private final List<StompSession> activeSessions = new ArrayList<>();

    private String teacherToken1;
    private String teacherToken2;
    private String studentToken;
    private String roomCode1;

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

        // 1. Register Teacher 1
        teacherToken1 = registerUser("Teacher Severus", "severus@codepulse.com", "potions123", Role.TEACHER);

        // 2. Register Teacher 2
        teacherToken2 = registerUser("Teacher Remus", "remus@codepulse.com", "defense456", Role.TEACHER);

        // 3. Register Student
        studentToken = registerUser("Student Draco", "draco@codepulse.com", "slytherin789", Role.STUDENT);

        // 4. Create Room for Teacher 1
        roomCode1 = createRoom(teacherToken1, "Potions Masterclass").getRoomCode();
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

    private StompSession connectWithToken(String token) throws ExecutionException, InterruptedException, TimeoutException {
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
                }
        );

        StompSession session = future.get(5, TimeUnit.SECONDS);
        activeSessions.add(session);
        return session;
    }

    @Test
    @DisplayName("1. WebSocket Connection: Valid JWT connects successfully, unauthenticated connection is rejected")
    void testWebSocketConnectionAuthentication() throws Exception {
        // Valid JWT connection succeeds
        StompSession authenticatedSession = connectWithToken(teacherToken1);
        assertNotNull(authenticatedSession);
        assertEquals(true, authenticatedSession.isConnected());

        // Unauthenticated STOMP connection is rejected (no token)
        assertThrows(Exception.class, () -> connectWithToken(null));

        // Invalid token connection is rejected
        assertThrows(Exception.class, () -> connectWithToken("invalid.malformed.token"));
    }

    @Test
    @DisplayName("2 & 3. Teacher publishes code to their room, subscribed student receives the updated code")
    void testTeacherPublishesCodeAndStudentReceives() throws Exception {
        StompSession teacherSession = connectWithToken(teacherToken1);
        StompSession studentSession = connectWithToken(studentToken);

        BlockingQueue<CodeSyncMessage> receivedQueue = new LinkedBlockingDeque<>();

        // Student subscribes to room topic
        studentSession.subscribe("/topic/room/" + roomCode1 + "/editor", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CodeSyncMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedQueue.offer((CodeSyncMessage) payload);
            }
        });

        // Small delay to ensure subscription is registered in broker
        Thread.sleep(500);

        // Teacher sends code update
        CodeSyncMessage update = CodeSyncMessage.builder()
                .code("public class Solution { public static void main(String[] args) {} }")
                .build();

        teacherSession.send("/app/room/" + roomCode1 + "/editor", update);

        // Verify student receives the message
        CodeSyncMessage received = receivedQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "Student should receive the broadcasted code update");
        assertEquals("public class Solution { public static void main(String[] args) {} }", received.getCode());
        assertEquals("severus@codepulse.com", received.getSenderEmail());
    }

    @Test
    @DisplayName("4. Student cannot publish teacher code updates (message rejected, not broadcast)")
    void testStudentCannotPublishCodeUpdates() throws Exception {
        StompSession studentSession = connectWithToken(studentToken);

        BlockingQueue<CodeSyncMessage> receivedQueue = new LinkedBlockingDeque<>();

        // Subscribe to verify whether any message is broadcast
        studentSession.subscribe("/topic/room/" + roomCode1 + "/editor", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CodeSyncMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedQueue.offer((CodeSyncMessage) payload);
            }
        });

        Thread.sleep(500);

        // Student attempts to publish code to the room
        CodeSyncMessage studentUpdate = CodeSyncMessage.builder()
                .code("// Student trying to overwrite teacher editor")
                .build();

        studentSession.send("/app/room/" + roomCode1 + "/editor", studentUpdate);

        // Verify NO message is received on the topic
        CodeSyncMessage received = receivedQueue.poll(2, TimeUnit.SECONDS);
        assertNull(received, "Student must not be allowed to broadcast editor updates");
    }

    @Test
    @DisplayName("5. Teacher cannot publish to another teacher's room")
    void testTeacherCannotPublishToAnotherTeacherRoom() throws Exception {
        // Teacher 2 connects
        StompSession teacher2Session = connectWithToken(teacherToken2);
        StompSession studentSession = connectWithToken(studentToken);

        BlockingQueue<CodeSyncMessage> receivedQueue = new LinkedBlockingDeque<>();

        studentSession.subscribe("/topic/room/" + roomCode1 + "/editor", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CodeSyncMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedQueue.offer((CodeSyncMessage) payload);
            }
        });

        Thread.sleep(500);

        // Teacher 2 attempts to publish to Teacher 1's room (roomCode1)
        CodeSyncMessage rogueTeacherUpdate = CodeSyncMessage.builder()
                .code("// Teacher 2 attempting to overwrite Teacher 1 room")
                .build();

        teacher2Session.send("/app/room/" + roomCode1 + "/editor", rogueTeacherUpdate);

        // Verify NO message is broadcast
        CodeSyncMessage received = receivedQueue.poll(2, TimeUnit.SECONDS);
        assertNull(received, "Teacher 2 must not be allowed to broadcast to Teacher 1's room");
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
