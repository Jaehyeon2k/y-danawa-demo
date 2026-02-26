package yju.danawa.com.repository;

import yju.danawa.com.domain.SearchLog;
import yju.danawa.com.dto.DeptSearchCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    long countByUserDeptAndSearchTimeBetween(String userDept, LocalDateTime start, LocalDateTime end);

    @Query("SELECT new yju.danawa.com.dto.DeptSearchCountDto(sl.userDept, COUNT(sl)) " +
            "FROM yju.danawa.com.domain.SearchLog sl " +
            "WHERE sl.searchTime BETWEEN :start AND :end " +
            "GROUP BY sl.userDept " +
            "ORDER BY COUNT(sl) DESC")
    List<DeptSearchCountDto> findSearchCountsByDeptBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
