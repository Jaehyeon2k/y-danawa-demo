const BOOK_STORES = {
  yes24: (isbn) => `http://www.yes24.com/Product/Search?domain=BOOK&query=${isbn}`,
  kyobo: (isbn) => `https://search.kyobobook.co.kr/search?keyword=${isbn}`,
};

const LIBRARIES = {
  yjcEbook: (isbn) => `https://ebook.yjc.ac.kr/search/?srch_order=total&src_key=${isbn}`,
  yjcCentral: (isbn) => `https://lib.yjc.ac.kr/WebYJC/Aspx/search/searchotb.aspx?query=${isbn}`,
};

const getEnvValue = (key) => {
  if (typeof import.meta !== "undefined" && import.meta.env && key in import.meta.env) {
    return import.meta.env[key];
  }
  if (typeof process !== "undefined" && process.env && key in process.env) {
    return process.env[key];
  }
  return undefined;
};

const API_REQUESTS = {
  aladinItemLookup: (isbn, ttbKey) =>
    `https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx?ttbkey=${ttbKey}&itemIdType=ISBN&ItemId=${isbn}&output=js&Version=20131101`,
  kakaoBookSearch: (isbn) =>
    `https://dapi.kakao.com/v3/search/book?query=isbn:${isbn}&size=1`,
};

const normalizeIsbn = (raw) => raw.replace(/[^0-9Xx]/g, "").toUpperCase();

const isValidIsbnLength = (isbn) => isbn.length === 10 || isbn.length === 13;

const isValidIsbn10 = (isbn) => {
  if (isbn.length !== 10) return false;
  const chars = isbn.split("");
  const checksum = chars.reduce((sum, char, idx) => {
    const value = char === "X" ? 10 : Number(char);
    if (Number.isNaN(value)) return NaN;
    return sum + value * (10 - idx);
  }, 0);
  return Number.isFinite(checksum) && checksum % 11 === 0;
};

const isValidIsbn13 = (isbn) => {
  if (isbn.length !== 13 || /[^0-9]/.test(isbn)) return false;
  const digits = isbn.split("").map((d) => Number(d));
  const checksum = digits.reduce((sum, digit, idx) => {
    const weight = idx % 2 === 0 ? 1 : 3;
    return sum + digit * weight;
  }, 0);
  return checksum % 10 === 0;
};

const isValidIsbn = (isbn) => isValidIsbn10(isbn) || isValidIsbn13(isbn);

export const buildIsbnSearchPayload = (rawIsbn) => {
  const isbn = normalizeIsbn(rawIsbn);
  if (!isValidIsbnLength(isbn)) {
    throw new Error("ISBN은 10자리 또는 13자리여야 합니다.");
  }
  if (!isValidIsbn(isbn)) {
    throw new Error("ISBN 체크섬이 올바르지 않습니다.");
  }

  const aladinKey = getEnvValue("VITE_ALADIN_API_KEY") || getEnvValue("ALADIN_API_KEY") || "YOUR_ALADIN_TTB_KEY";
  const kakaoKey = getEnvValue("VITE_KAKAO_REST_API_KEY") || getEnvValue("KAKAO_REST_API_KEY") || "YOUR_KAKAO_REST_API_KEY";

  return {
    isbn,
    deepLinks: {
      yes24: BOOK_STORES.yes24(isbn),
      kyobo: BOOK_STORES.kyobo(isbn),
    },
    apiRequests: {
      aladinItemLookup: API_REQUESTS.aladinItemLookup(isbn, aladinKey),
      kakaoBookSearch: API_REQUESTS.kakaoBookSearch(isbn),
      kakaoAuthHeader: `Authorization: KakaoAK ${kakaoKey}`,
    },
    libraries: {
      yjcEbook: LIBRARIES.yjcEbook(isbn),
      yjcCentral: LIBRARIES.yjcCentral(isbn),
    },
  };
};

export const buildIsbnSearchPrompt = (rawIsbn) => {
  const payload = buildIsbnSearchPayload(rawIsbn);
  return {
    role: "도서관 및 서점 통합 검색 시스템의 백엔드 로직 설계자",
    input: payload.isbn,
    output: payload,
  };
};
