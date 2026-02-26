package yju.danawa.com.repository;

import yju.danawa.com.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByStudentId(String studentId);

    @Query("select u.password from User u")
    List<String> findAllPasswordHashes();
}
