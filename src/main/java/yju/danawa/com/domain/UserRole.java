package yju.danawa.com.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_roles", indexes = {
        @Index(name = "idx_user_roles_user_id", columnList = "user_id")
})
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "user_id")
    private User user;

    @Column(name = "role_name", nullable = false, length = 255)
    private String roleName;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime createdAt;

    public UserRole() {
    }

    public UserRole(User user, String roleName, LocalDateTime createdAt) {
        this.user = user;
        this.roleName = roleName;
        this.createdAt = createdAt;
    }

    public Long getRoleId() {
        return roleId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
