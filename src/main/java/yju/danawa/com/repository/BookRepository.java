package yju.danawa.com.repository;

import yju.danawa.com.domain.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {

    Optional<Book> findByIsbn(String isbn);

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Query(value = "select * from books b " +
            "where b.isbn ilike concat('%', :q, '%') " +
            "or b.title ilike concat('%', :q, '%') " +
            "or b.author ilike concat('%', :q, '%') " +
            "or b.publisher ilike concat('%', :q, '%')", nativeQuery = true)
    List<Book> searchByKeyword(@Param("q") String q);

    /**
     * 무한 스크롤을 위한 첫 페이지 조회
     * ISBN 오름차순으로 limit개 반환
     */
    @Query(value = "SELECT * FROM books ORDER BY isbn ASC LIMIT :limit", nativeQuery = true)
    List<Book> findTopBooks(@Param("limit") int limit);

    /**
     * 무한 스크롤을 위한 다음 페이지 조회
     * cursor(ISBN)보다 큰 ISBN을 가진 도서를 limit개 반환
     */
    @Query(value = "SELECT * FROM books WHERE isbn > :cursor ORDER BY isbn ASC LIMIT :limit", nativeQuery = true)
    List<Book> findBooksAfterCursor(@Param("cursor") String cursor, @Param("limit") int limit);
}
