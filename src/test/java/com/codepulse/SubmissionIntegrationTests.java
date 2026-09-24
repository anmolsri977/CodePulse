package com.codepulse;

import com.codepulse.dto.AuthResponse;
import com.codepulse.dto.CreateChallengeRequest;
import com.codepulse.dto.CreateRoomRequest;
import com.codepulse.dto.GeminiReviewResult;
import com.codepulse.dto.RegisterRequest;
import com.codepulse.dto.RoomResponse;
import com.codepulse.dto.SubmitCodeRequest;
import com.codepulse.entity.Challenge;
import com.codepulse.entity.Role;
import com.codepulse.entity.Room;
import com.codepulse.entity.RoomStatus;
import com.codepulse.entity.Submission;
import com.codepulse.entity.User;
import com.codepulse.repository.ChallengeRepository;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.UserRepository;
import com.codepulse.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SubmissionIntegrationTests {

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

    private String teacherToken;
    private String studentToken;
    private Long activeChallengeId;
    private String roomCode;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        challengeRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        // Register Teacher and Student
        teacherToken = registerUser("Teacher Lupin", "lupin@codepulse.com", "boggartPassword1", Role.TEACHER);
        studentToken = registerUser("Student Harry", "harry.potter@codepulse.com", "patronusPassword2", Role.STUDENT);

        // Create Room
        roomCode = createRoom(teacherToken, "Defense Class").getRoomCode();

        // Create Challenge
        User teacher = userRepository.findByEmail("lupin@codepulse.com").orElseThrow();
        Room room = roomRepository.findByRoomCode(roomCode).orElseThrow();
        Challenge challenge = challengeRepository.save(Challenge.builder()
                .title("Factorial Function")
                .description("Compute n! recursively or iteratively.")
                .skeleton("public int factorial(int n) { return 0; }")
                .timeLimit(20)
                .room(room)
                .build());
        activeChallengeId = challenge.getId();
    }

    @Test
    @DisplayName("1. Student submits valid code: Gemini reviews, score and feedback saved")
    void testStudentSubmitsValidCode() throws Exception {
        // Mock Gemini response
        when(geminiService.reviewCode(any(), any(), any()))
                .thenReturn(new GeminiReviewResult(92, "Well written recursive solution with correct base case."));

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return (n <= 1) ? 1 : n * factorial(n - 1); }")
                .build();

        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.score").value(92))
                .andExpect(jsonPath("$.feedback").value("Well written recursive solution with correct base case."))
                .andExpect(jsonPath("$.challengeId").value(activeChallengeId))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        // Verify database persistence
        Submission savedSubmission = submissionRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(savedSubmission);
        assertEquals(92, savedSubmission.getScore());
        User student = userRepository.findByEmail("harry.potter@codepulse.com").orElseThrow();
        assertEquals(student.getId(), savedSubmission.getStudent().getId());
        assertEquals(activeChallengeId, savedSubmission.getChallenge().getId());

        verify(geminiService).reviewCode(any(), any(), any());
    }

    @Test
    @DisplayName("2. Teacher cannot submit code (403 Forbidden)")
    void testTeacherCannotSubmitCode() throws Exception {
        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Unauthenticated submission rejected (401 Unauthorized)")
    void testUnauthenticatedSubmissionRejected() throws Exception {
        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Nonexistent challenge rejected (404 Not Found)")
    void testNonexistentChallengeRejected() throws Exception {
        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        mockMvc.perform(post("/api/challenges/999999/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. Submission to closed-room challenge rejected (400 Bad Request)")
    void testSubmissionToClosedRoomChallengeRejected() throws Exception {
        // Teacher closes room
        mockMvc.perform(patch("/api/rooms/" + roomCode + "/close")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        // Student tries to submit
        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Gemini service failure is handled safely without failing submission persistence")
    void testGeminiFailureHandledSafely() throws Exception {
        // Simulate Gemini API network exception
        when(geminiService.reviewCode(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated Gemini API timeout or 503 Service Unavailable"));

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return (n <= 1) ? 1 : n * factorial(n - 1); }")
                .build();

        // Request still succeeds with 201 Created and graceful fallback message
        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.feedback").value("AI review is currently unavailable. Your code has been saved successfully."))
                .andExpect(jsonPath("$.challengeId").value(activeChallengeId));

        // Verify the code WAS saved in the database
        Submission savedSubmission = submissionRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(savedSubmission, "Submission record must still be persisted on AI failure");
        assertEquals("public int factorial(int n) { return (n <= 1) ? 1 : n * factorial(n - 1); }", savedSubmission.getCode());
        assertNotNull(savedSubmission.getAiFeedback());
    }

    @Test
    @DisplayName("7. Teacher who owns the room can view submitted code (GET /api/submissions/{id})")
    void testTeacherWhoOwnsRoomCanViewSubmittedCode() throws Exception {
        when(geminiService.reviewCode(any(), any(), any()))
                .thenReturn(new GeminiReviewResult(88, "Good effort"));

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return n <= 1 ? 1 : n * factorial(n-1); }")
                .build();

        MvcResult result = mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Teacher who owns the room accesses the submission
        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId))
                .andExpect(jsonPath("$.code").value("public int factorial(int n) { return n <= 1 ? 1 : n * factorial(n-1); }"))
                .andExpect(jsonPath("$.score").value(88))
                .andExpect(jsonPath("$.aiFeedback").value("Good effort"))
                .andExpect(jsonPath("$.studentName").value("Student Harry"))
                .andExpect(jsonPath("$.studentEmail").value("harry.potter@codepulse.com"))
                .andExpect(jsonPath("$.challengeTitle").value("Factorial Function"));
    }

    @Test
    @DisplayName("8. Unrelated teacher gets 403 Forbidden when accessing submission")
    void testUnrelatedTeacherCannotViewSubmittedCode() throws Exception {
        String otherTeacherToken = registerUser("Teacher Snape", "snape@codepulse.com", "potionsPassword3", Role.TEACHER);

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        MvcResult result = mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Submitting student can view own submission (GET /api/submissions/{id})")
    void testSubmittingStudentCanViewOwnSubmission() throws Exception {
        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        MvcResult result = mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId))
                .andExpect(jsonPath("$.code").value("public int factorial(int n) { return 1; }"));
    }

    @Test
    @DisplayName("10. Another student gets 403 Forbidden when accessing peer's submission")
    void testOtherStudentCannotViewSubmittedCode() throws Exception {
        String otherStudentToken = registerUser("Student Ron", "ron.weasley@codepulse.com", "scabbersPassword4", Role.STUDENT);

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        MvcResult result = mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. Submission before challenge deadline succeeds")
    void testSubmissionBeforeDeadlineSucceeds() throws Exception {
        // Set challenge started 5 minutes ago with 20 minute limit (15 mins remaining)
        Challenge challenge = challengeRepository.findById(activeChallengeId).orElseThrow();
        challenge.setStartedAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(5));
        challenge.setTimeLimit(20);
        challengeRepository.save(challenge);

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("12. Submission after challenge deadline is rejected with 400 Bad Request")
    void testSubmissionAfterDeadlineRejected() throws Exception {
        // Set challenge started 30 minutes ago with 20 minute limit (expired 10 mins ago)
        Challenge challenge = challengeRepository.findById(activeChallengeId).orElseThrow();
        challenge.setStartedAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(30));
        challenge.setTimeLimit(20);
        challengeRepository.save(challenge);

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("13. Submission to manually ended challenge is rejected with 400 Bad Request")
    void testSubmissionToEndedChallengeRejected() throws Exception {
        // Active challenge, but teacher manually ended it
        Challenge challenge = challengeRepository.findById(activeChallengeId).orElseThrow();
        challenge.setStartedAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2));
        challenge.setTimeLimit(20);
        challenge.setEndedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        challengeRepository.save(challenge);

        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .code("public int factorial(int n) { return 1; }")
                .build();

        mockMvc.perform(post("/api/challenges/" + activeChallengeId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isBadRequest());
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
