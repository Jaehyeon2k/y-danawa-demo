import api from "./api";

const cacheTtlMs = 5 * 60 * 1000;
const cache = new Map<string, { expiresAt: number; data: any }>();

const getCached = (key: string) => {
  const entry = cache.get(key);
  if (!entry) return undefined;
  if (Date.now() > entry.expiresAt) {
    cache.delete(key);
    return undefined;
  }
  return entry.data;
};

const setCached = (key: string, data: any, ttlMs = cacheTtlMs) => {
  cache.set(key, { expiresAt: Date.now() + ttlMs, data });
};

export const normalizeImageUrl = (raw?: string): string | undefined => {
  const v = (raw || "").trim();
  if (!v || v.length < 3) return undefined;
  if (v.startsWith("http://") || v.startsWith("https://")) return v;
  if (v.startsWith("/api/images/") || v.startsWith("/images/")) return v;
  if (/^[^/]+\.\w{2,5}$/.test(v)) {
    return `/api/images/by-name/${encodeURIComponent(v)}`;
  }
  return undefined;
};

export interface SearchBookItem {
  isbn13: string;
  title: string;
  author: string;
  publisher: string;
  thumbUrl: string;
}

export interface BookDetail {
  isbn13: string;
  title: string;
  author: string;
  publisher: string;
  coverUrl: string;
  vendors: {
    aladin: string;
    kyobo: string;
    yes24: string;
  };
  ebook: {
    title: string;
    found: boolean;
    totalHoldings: number;
    availableHoldings: number;
    statusText: string;
    deepLinkUrl: string;
  };
  library: {
    found: boolean;
    overallStatus: "대출가능" | "대출중" | "이용불가" | "정보없음";
    status: "AVAILABLE" | "ON_LOAN" | "RESERVED" | "UNAVAILABLE" | "NOT_OWNED" | "ERROR" | "UNKNOWN";
    statusText: string;
    recordTypePicked: "단행본" | "전자책" | null;
    matchedTitle: string | null;
    location: string;
    callNo: string;
    detailUrl: string;
    debugReason?: string;
  };
}

const normalizeIsbn13 = (value?: string) => {
  const digits = String(value || "").replace(/[^0-9]/g, "");
  if (/^97[89]\d{10}$/.test(digits)) return digits;
  return "";
};

export const getSearchBooks = async (keyword: string, signal?: AbortSignal): Promise<SearchBookItem[]> => {
  const key = `search:${keyword}`;
  const cached = getCached(key);
  if (cached) return cached;

  const response = await api.get<any[]>("/books/search", {
    params: { q: keyword },
    signal,
  });

  const rows = Array.isArray(response.data) ? response.data : [];
  const items = rows.map((row) => {
    const isbn13 = normalizeIsbn13(row?.isbn13);
    return {
      isbn13,
      title: String(row?.title || "").trim(),
      author: String(row?.author || "").trim(),
      publisher: String(row?.publisher || "").trim(),
      thumbUrl: normalizeImageUrl(row?.thumbUrl) || "https://placehold.co/300x440?text=No+Cover",
    } as SearchBookItem;
  });

  setCached(key, items, 60 * 1000);
  return items;
};

export const getBookDetail = async (isbn13: string, signal?: AbortSignal): Promise<BookDetail> => {
  const normalized = normalizeIsbn13(isbn13);
  if (!normalized) {
    throw new Error("invalid isbn13");
  }

  const key = `detail:${normalized}`;
  const cached = getCached(key);
  if (cached) return cached;

  const response = await api.get<any>(`/books/${normalized}`, { signal });
  const raw = response.data ?? {};
  const rawTitle = String(raw.title || "").trim();

  const detail: BookDetail = {
    isbn13: normalizeIsbn13(raw.isbn13) || normalized,
    title: rawTitle,
    author: String(raw.author || "").trim(),
    publisher: String(raw.publisher || "").trim(),
    coverUrl: normalizeImageUrl(raw.coverUrl) || "https://placehold.co/300x440?text=No+Cover",
    vendors: {
      aladin: String(raw?.vendors?.aladin || ""),
      kyobo: String(raw?.vendors?.kyobo || ""),
      yes24: String(raw?.vendors?.yes24 || ""),
    },
    ebook: {
      title: String(raw?.ebook?.title || rawTitle),
      found: raw?.ebook?.found === true,
      totalHoldings: Number(raw?.ebook?.totalHoldings || 0),
      availableHoldings: Number(raw?.ebook?.availableHoldings || 0),
      statusText: String(raw?.ebook?.statusText || "미소장"),
      deepLinkUrl: String(raw?.ebook?.deepLinkUrl || "https://ebook.yjc.ac.kr/"),
    },
    library: {
      found: raw?.library?.found === true,
      overallStatus: String(raw?.library?.overallStatus || "정보없음") as BookDetail["library"]["overallStatus"],
      status: String(raw?.library?.status || "UNKNOWN").toUpperCase() as BookDetail["library"]["status"],
      statusText: String(raw?.library?.statusText || "정보 없음"),
      recordTypePicked: (raw?.library?.recordTypePicked ? String(raw?.library?.recordTypePicked) : null) as BookDetail["library"]["recordTypePicked"],
      matchedTitle: raw?.library?.matchedTitle ? String(raw?.library?.matchedTitle) : null,
      location: String(raw?.library?.location || ""),
      callNo: String(raw?.library?.callNo || ""),
      detailUrl: String(raw?.library?.detailUrl || `https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/${normalized}`),
      debugReason: String(raw?.library?.debugReason || ""),
    },
  };

  setCached(key, detail, 2 * 60 * 1000);
  return detail;
};

// Legacy exports kept for compatibility with older pages.
export type LibraryAvailability = {
  found: boolean;
  available: boolean;
  holding: boolean;
  loanable: boolean;
  statusCode: "AVAILABLE" | "ON_LOAN" | "RESERVED" | "NOT_OWNED" | "UNKNOWN" | "ERROR";
  statusText: string;
  location?: string;
  callNumber?: string;
  detailUrl: string;
  errorMessage?: string;
};

export const getBooks = async (keyword: string, signal?: AbortSignal) => {
  return getSearchBooks(keyword, signal);
};

export const searchExternalBooks = async (_query: string, _source: "kakao" | "aladin" | "auto" = "auto") => {
  return [] as SearchBookItem[];
};

export const checkLibraryAvailability = async (
  isbn?: string,
  _title?: string,
  _author?: string,
  _publisher?: string,
  signal?: AbortSignal
): Promise<LibraryAvailability> => {
  const detail = await getBookDetail(normalizeIsbn13(isbn), signal);
  const status = detail.library.status;
  const holding = status === "AVAILABLE" || status === "ON_LOAN" || status === "RESERVED" || status === "UNAVAILABLE";
  return {
    found: holding,
    available: status === "AVAILABLE",
    holding,
    loanable: status === "AVAILABLE",
    statusCode: status,
    statusText: detail.library.statusText,
    location: detail.library.location,
    callNumber: detail.library.callNo,
    detailUrl: detail.library.detailUrl,
    errorMessage: detail.library.status === "ERROR" ? "library_error" : "",
  };
};

export interface BookPrice {
  store: string;
  storeName: string;
  price: number | null;
  url: string;
  deliveryInfo: string | null;
  available: boolean;
}

export const getBookPrices = async (_isbn?: string, _title?: string): Promise<BookPrice[]> => {
  return [];
};
