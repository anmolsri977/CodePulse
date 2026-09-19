package com.codepulse;

import com.codepulse.dto.AuthResponse;
import com.codepulse.dto.LoginRequest;
import com.codepulse.dto.RegisterRequest;
import com.codepulse.entity.Role;
import com.codepulse.entity.User;
import com.codepulse.repository.ChallengeRepository;
import com.codepulse.repository.RoomRepository;
import com.codepulse.repository.SubmissionRepository;
import com.codepulse.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthEndToEndTests {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        submissionRepository.deleteAll();
        challengeRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Case 1: POST /api/auth/register with valid TEACHER data")
    void test1_registerTeacherSuccess() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Professor Minerva")
                .email("minerva@codepulse.com")
                .password("securePassword123")
                .role(Role.TEACHER)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.email").value("minerva@codepulse.com"))
                .andExpect(jsonPath("$.name").value("Professor Minerva"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        // Verify that BCrypt hashed password is saved in DB and plain text is NOT stored
        User savedUser = userRepository.findByEmail("minerva@codepulse.com").orElse(null);
        assertNotNull(savedUser, "User should be saved in repository");
        assertNotEquals("securePassword123", savedUser.getPassword(), "Password must not be stored in plain text");
        assertTrue(passwordEncoder.matches("securePassword123", savedUser.getPassword()), "Stored password must match BCrypt hash");
    }

    @Test
    @Order(2)
    @DisplayName("Case 2: POST /api/auth/login with correct credentials")
    void test2_loginSuccess() throws Exception {
        // Register user first
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Professor Albus")
                .email("albus@codepulse.com")
                .password("phoenixPassword456")
                .role(Role.TEACHER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Perform login
        LoginRequest loginRequest = LoginRequest.builder()
                .email("albus@codepulse.com")
                .password("phoenixPassword456")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.email").value("albus@codepulse.com"))
                .andExpect(jsonPath("$.name").value("Professor Albus"))
                .andExpect(jsonPath("$.role").value("TEACHER"));
    }

    @Test
    @Order(3)
    @DisplayName("Case 3: POST /api/auth/login with wrong password")
    void test3_loginWrongPassword() throws Exception {
        // Register user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Student Harry")
                .email("harry@codepulse.com")
                .password("correctPassword789")
                .role(Role.STUDENT)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Login with wrong password
        LoginRequest loginRequest = LoginRequest.builder()
                .email("harry@codepulse.com")
                .password("wrongPasswordXYZ")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("Case 4: Access a protected endpoint without JWT")
    void test4_accessProtectedEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("Case 5: Access a protected endpoint with a valid JWT")
    void test5_accessProtectedEndpointWithValidJwt() throws Exception {
        // Register user and obtain token
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Student Hermione")
                .email("hermione@codepulse.com")
                .password("libraryPassword101")
                .role(Role.STUDENT)
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String jwtToken = authResponse.getToken();
        assertNotNull(jwtToken, "JWT token must not be null");

        // Access protected endpoint with valid JWT
        mockMvc.perform(get("/api/test/protected")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Protected endpoint accessed successfully by hermione@codepulse.com")));

        // Access protected endpoint with invalid JWT should be rejected
        mockMvc.perform(get("/api/test/protected")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("Case 6: CORS preflight OPTIONS request from allowed origin receives appropriate headers")
    void test6_corsPreflightOptionsRequest() throws Exception {
        // Preflight for POST /api/auth/register from http://localhost:5173
        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("OPTIONS")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));

        // Preflight for POST /api/auth/login from http://localhost:5173
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        // Preflight from http://localhost:3000
        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        // Preflight from unauthorized origin
        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", "http://untrusted-site.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
