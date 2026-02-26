CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Main app table (Book entity)
CREATE TABLE IF NOT EXISTS books (
    isbn VARCHAR(32) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255),
    publisher VARCHAR(255),
    image_url VARCHAR(2048),
    published_date DATE,
    price DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_books_title ON books(title);
CREATE INDEX IF NOT EXISTS idx_books_author ON books(author);

-- Image-search pipeline tables
CREATE TABLE IF NOT EXISTS book (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    title_norm VARCHAR(500) NOT NULL,
    isbn13 VARCHAR(20) UNIQUE,
    authors VARCHAR(500),
    publisher VARCHAR(200),
    published_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS book_image (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    kind VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    content_type VARCHAR(100),
    width INTEGER,
    height INTEGER,
    sha256 VARCHAR(64) NOT NULL,
    bytes BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (book_id, sha256)
);

CREATE INDEX IF NOT EXISTS idx_book_title_norm_trgm
    ON book USING gin (title_norm gin_trgm_ops);
