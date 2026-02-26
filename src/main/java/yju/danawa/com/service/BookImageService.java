package yju.danawa.com.service;

import yju.danawa.com.domain.BookImage;
import yju.danawa.com.repository.BookImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BookImageService {

    private final BookImageRepository bookImageRepository;

    public BookImageService(BookImageRepository bookImageRepository) {
        this.bookImageRepository = bookImageRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BookImage> findById(Long id) {
        return bookImageRepository.findById(id);
    }
}
