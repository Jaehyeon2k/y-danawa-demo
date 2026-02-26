package yju.danawa.com.repository;

import yju.danawa.com.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserUserId(Long userId);
}
