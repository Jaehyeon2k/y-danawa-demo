-- Verify index usage and similarity ordering for title_norm search.
ANALYZE book;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title, title_norm
FROM book
WHERE title_norm ILIKE '%query%'
ORDER BY similarity(title_norm, 'query') DESC, id DESC
LIMIT 30;

-- Optional: refresh index stats if query plans look stale.
-- REINDEX INDEX idx_book_title_norm_trgm;
