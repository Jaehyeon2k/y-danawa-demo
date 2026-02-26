package yju.danawa.com.web;

import yju.danawa.com.dto.BookDto;
import yju.danawa.com.service.ExternalBookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/external/books")
public class ExternalBookController {

    private final ExternalBookService externalBookService;

    public ExternalBookController(ExternalBookService externalBookService) {
        this.externalBookService = externalBookService;
    }

    @GetMapping
    public List<BookDto> searchExternalBooks(
            @RequestParam("query") String query,
            @RequestParam(value = "source", required = false) String source
    ) {
        return externalBookService.search(query, source);
    }
}
