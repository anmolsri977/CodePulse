package com.codepulse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/test")
public class TestProtectedController {

    @GetMapping("/protected")
    public ResponseEntity<String> protectedEndpoint(Principal principal) {
        String username = (principal != null) ? principal.getName() : "Anonymous";
        return ResponseEntity.ok("Protected endpoint accessed successfully by " + username);
    }
}
