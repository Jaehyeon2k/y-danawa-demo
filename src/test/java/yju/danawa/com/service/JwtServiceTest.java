package yju.danawa.com.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void generatesAndValidatesToken() {
        JwtService jwtService = new JwtService("test-secret-key-change-me-32bytes!!", 5);
        String token = jwtService.generateToken("tester");
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals("tester", jwtService.extractUsername(token));
    }
}
