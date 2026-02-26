package yju.danawa.com.repository;

import yju.danawa.com.domain.BookImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookImageRepository extends JpaRepository<BookImage, Long> {

    interface BookImageIdRow {
        Long getId();
        Long getBookId();
    }

    interface BookImageByIsbnRow {
        Long getId();
        String getIsbn13();
    }

    @Query(
            value = "SELECT id, book_id AS bookId FROM book_image WHERE book_id IN (:bookIds) ORDER BY id ASC",
            nativeQuery = true)
    List<BookImageIdRow> findImageIdsByBookIds(@Param("bookIds") List<Long> bookIds);

    @Query(value = """
            SELECT DISTINCT ON (b.isbn13)
                bi.id AS id,
                b.isbn13 AS isbn13
            FROM book_image bi
            JOIN book b ON b.id = bi.book_id
            WHERE b.isbn13 IN (:isbn13List)
            ORDER BY b.isbn13, bi.id ASC
            """, nativeQuery = true)
    List<BookImageByIsbnRow> findFirstImageByIsbn13In(@Param("isbn13List") List<String> isbn13List);
}
