package yju.danawa.com.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "click_logs", indexes = {
        @Index(name = "idx_clicklog_isbn", columnList = "isbn"),
        @Index(name = "idx_clicklog_target_channel", columnList = "target_channel")
})
public class ClickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "click_id")
    private Long clickId;

    @Column(length = 32)
    private String isbn;

    @Column(name = "target_channel")
    private String targetChannel;

    @Column(name = "slider_value")
    private Double sliderValue;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;

    public ClickLog() {
    }

    public ClickLog(String isbn, String targetChannel, Double sliderValue, LocalDateTime createdAt) {
        this.isbn = isbn;
        this.targetChannel = targetChannel;
        this.sliderValue = sliderValue;
        this.createdAt = createdAt;
    }

    public Long getClickId() {
        return clickId;
    }

    public void setClickId(Long clickId) {
        this.clickId = clickId;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTargetChannel() {
        return targetChannel;
    }

    public void setTargetChannel(String targetChannel) {
        this.targetChannel = targetChannel;
    }

    public Double getSliderValue() {
        return sliderValue;
    }

    public void setSliderValue(Double sliderValue) {
        this.sliderValue = sliderValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
