package yju.danawa.com.repository;

import yju.danawa.com.domain.ClickLog;
import yju.danawa.com.dto.BookClickCountDto;
import yju.danawa.com.dto.ChannelSliderStatsDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {

    @Query("SELECT new yju.danawa.com.dto.ChannelSliderStatsDto(cl.targetChannel, AVG(cl.sliderValue), COUNT(cl)) " +
            "FROM yju.danawa.com.domain.ClickLog cl " +
            "WHERE cl.createdAt BETWEEN :start AND :end " +
            "GROUP BY cl.targetChannel " +
            "ORDER BY AVG(cl.sliderValue) DESC")
    List<ChannelSliderStatsDto> findChannelSliderStatsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT new yju.danawa.com.dto.BookClickCountDto(cl.isbn, COUNT(cl)) " +
            "FROM yju.danawa.com.domain.ClickLog cl " +
            "WHERE cl.createdAt BETWEEN :start AND :end " +
            "GROUP BY cl.isbn " +
            "ORDER BY COUNT(cl) DESC")
    List<BookClickCountDto> findTopBooksByClicksBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
