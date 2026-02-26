package yju.danawa.com.web;

import yju.danawa.com.domain.User;
import yju.danawa.com.service.JwtService;
import yju.danawa.com.service.UserProfileService;
import yju.danawa.com.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserService userService,
                          UserProfileService userProfileService,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @GetMapping("/validate")
    public java.util.Map<String, Object> validate(@RequestParam(required = false) String username,
                                                   @RequestParam(required = false) String studentId) {
        boolean usernameAvailable = username == null || username.isBlank() || !userService.existsByUsername(username);
        boolean studentIdAvailable = studentId == null || studentId.isBlank() || !userService.existsByStudentId(studentId);
        return java.util.Map.of(
                "usernameAvailable", usernameAvailable,
                "studentIdAvailable", studentIdAvailable
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        boolean exists = userService.existsByUsername(request.username());
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_EXISTS");
        }
        if (userService.existsByStudentId(request.studentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "STUDENT_ID_EXISTS");
        }
        if (userService.isPasswordInUse(request.password(), passwordEncoder)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PASSWORD_IN_USE");
        }

        LocalDateTime now = LocalDateTime.now();
        User saved = userService.save(new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.fullName(),
                request.department(),
                request.studentId(),
                request.phone(),
                true,
                false,
                0,
                null,
                now,
                now
        ));
        String token = jwtService.generateToken(saved.getUsername());
        return new LoginResponse(saved.getUsername(), "ok", token, userProfileService.getRoleNames(saved.getUserId()));
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (Boolean.FALSE.equals(user.getIsEnabled()) || Boolean.TRUE.equals(user.getIsLocked())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed");
        }

        String token = jwtService.generateToken(user.getUsername());
        return new LoginResponse(user.getUsername(), "ok", token, userProfileService.getRoleNames(user.getUserId()));
    }
}
