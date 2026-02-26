package yju.danawa.com.config;

import yju.danawa.com.domain.User;
import yju.danawa.com.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;

@Configuration
@ConditionalOnProperty(name = "app.auth.seed-enabled", havingValue = "true")
public class UserSeedConfig {

    @Bean
    public CommandLineRunner seedDefaultUser(UserService userService, PasswordEncoder passwordEncoder) {
        return args -> userService.findByUsername("admin")
                .orElseGet(() -> userService.save(new User(
                        "admin",
                        passwordEncoder.encode("admin1234"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        false,
                        0,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));
    }
}
