package yju.danawa.com.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_logs", indexes = {
        @Index(name = "idx_searchlog_user_dept", columnList = "user_dept"),
        @Index(name = "idx_searchlog_search_time", columnList = "search_time")
})
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    private String keyword;

    @Column(name = "user_dept")
    private String userDept;

    @Column(name = "search_time", columnDefinition = "TIMESTAMP")
    private LocalDateTime searchTime;

    public SearchLog() {
    }

    public SearchLog(String keyword, String userDept, LocalDateTime searchTime) {
        this.keyword = keyword;
        this.userDept = userDept;
        this.searchTime = searchTime;
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getUserDept() {
        return userDept;
    }

    public void setUserDept(String userDept) {
        this.userDept = userDept;
    }

    public LocalDateTime getSearchTime() {
        return searchTime;
    }

    public void setSearchTime(LocalDateTime searchTime) {
        this.searchTime = searchTime;
    }
}
