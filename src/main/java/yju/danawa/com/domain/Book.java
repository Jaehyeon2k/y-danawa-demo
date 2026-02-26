package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "books", indexes = {
        @Index(name = "idx_books_title", columnList = "title"),
        @Index(name = "idx_books_author", columnList = "author")
})
public class Book {

    @Id
    @Column(length = 32, nullable = false)
    private String isbn; // PK (ISBN)

    @Column(nullable = false)
    private String title;

    private String author;

    private String publisher;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    private Double price;

    public Book() {
    }

    public Book(String isbn, String title, String author, String publisher, String imageUrl, LocalDate publishedDate, Double price) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.imageUrl = imageUrl;
        this.publishedDate = publishedDate;
        this.price = price;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
