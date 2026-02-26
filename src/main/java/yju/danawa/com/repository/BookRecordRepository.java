package yju.danawa.com.repository;

import yju.danawa.com.domain.BookRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRecordRepository extends JpaRepository<BookRecord, Long> {

    interface BookFileImageRow {
        Long getBookId();
        String getImageUrl();
    }

    @Query(
            value = "SELECT * FROM book " +
                    "WHERE title_norm ILIKE concat('%', :q, '%') " +
                    "ORDER BY similarity(title_norm, :q) DESC, id DESC",
            countQuery = "SELECT COUNT(*) FROM book WHERE title_norm ILIKE concat('%', :q, '%')",
            nativeQuery = true)
    Page<BookRecord> searchByTitleNorm(@Param("q") String q, Pageable pageable);

    @Query(value = """
            SELECT
                b.id AS bookId,
                bs.image_url AS imageUrl
            FROM book b
            JOIN books bs ON bs.isbn = b.isbn13
            WHERE b.id IN (:bookIds)
              AND bs.image_url IS NOT NULL
              AND bs.image_url <> ''
            """, nativeQuery = true)
    List<BookFileImageRow> findFileImageUrlsByBookIds(@Param("bookIds") List<Long> bookIds);
}
