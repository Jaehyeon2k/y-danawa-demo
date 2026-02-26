import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import axios from "axios";
import * as cheerio from "cheerio";
import pg from "pg";

const { Client } = pg;

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

const IMAGE_DIR = path.resolve(process.cwd(), "public", "images");
const INPUT_CSV = process.argv[2] || path.resolve(process.cwd(), "scripts", "books.csv");
const DB_URL =
  process.env.CRAWLER_DB_URL || "postgresql://root:0910@localhost:5433/ydanawa_db";

function ensureDirOnce(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }
}

function safeFileName(name) {
  return (name || "untitled")
    .replace(/[\\/:*?"<>|]/g, "_")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 160);
}

function readCsv(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`CSV file not found: ${filePath}`);
  }
  const raw = fs.readFileSync(filePath, "utf8");
  const lines = raw.split(/\r?\n/).filter(Boolean);
  if (lines.length === 0) return [];
  const header = lines[0].toLowerCase();
  const hasHeader = header.includes("isbn") || header.includes("title");

  if (!hasHeader) {
    return lines.map((line) => {
      const isbn = line.split(",")[0]?.trim() || "";
      return { category: "", title: "", isbn, kyoboUrl: "", aladinUrl: "" };
    });
  }

  const cols = lines[0].split(",").map((c) => c.trim().toLowerCase());
  const idx = (name, alt = []) => {
    const all = [name, ...alt];
    return cols.findIndex((c) => all.includes(c));
  };
  const categoryIdx = idx("category");
  const titleIdx = idx("title");
  const isbnIdx = idx("isbn", ["isbn13"]);
  const kyoboIdx = idx("kyobo_url", ["detail_url"]);
  const aladinIdx = idx("aladin_url");

  return lines.slice(1).map((line) => {
    const cells = line.split(",").map((c) => c.trim());
    return {
      category: categoryIdx >= 0 ? cells[categoryIdx] || "" : "",
      title: titleIdx >= 0 ? cells[titleIdx] || "" : "",
      isbn: isbnIdx >= 0 ? cells[isbnIdx] || "" : "",
      kyoboUrl: kyoboIdx >= 0 ? cells[kyoboIdx] || "" : "",
      aladinUrl: aladinIdx >= 0 ? cells[aladinIdx] || "" : "",
    };
  });
}

function toAbsoluteUrl(url, base = "") {
  if (!url) return "";
  if (url.startsWith("//")) return `https:${url}`;
  if (url.startsWith("http://")) return `https://${url.slice(7)}`;
  if (url.startsWith("https://")) return url;
  if (url.startsWith("/")) {
    try {
      const u = new URL(base);
      return `${u.protocol}//${u.host}${url}`;
    } catch {
      return "";
    }
  }
  return url;
}

function generateFallbackCandidates(url) {
  if (!url) return [];
  const set = new Set();
  const push = (u) => {
    if (u) set.add(u);
  };
  push(url);

  const expand = (u) => {
    push(u.replace(/_s(\.|\/|\?)/g, "_l$1"));
    push(u.replace(/_s(\.|\/|\?)/g, "_m$1"));
    push(u.replace(/_m(\.|\/|\?)/g, "_l$1"));
    push(u.replace(/_m(\.|\/|\?)/g, "_s$1"));
    push(u.replace(/_l(\.|\/|\?)/g, "_m$1"));
    push(u.replace(/_l(\.|\/|\?)/g, "_s$1"));
    push(u.replace(/fit-in\/\d+x\d+/g, "fit-in/1500x0"));
    push(u.replace(/fit-in\/\d+x\d+/g, "fit-in/900x0"));
    push(u.replace(/fit-in\/\d+x\d+/g, "fit-in/600x0"));
    push(u.replace(/contents\.kyobobook\.co\.kr/g, "img.kyobobook.co.kr"));
    push(u.replace(/img\.kyobobook\.co\.kr/g, "contents.kyobobook.co.kr"));
  };
  expand(url);

  return [...set].sort((a, b) => {
    const score = (u) => (u.includes("_l") ? 0 : u.includes("_m") ? 1 : u.includes("_s") ? 2 : 3);
    return score(a) - score(b);
  });
}

function extractImageCandidates(html, baseUrl) {
  const $ = cheerio.load(html);
  const out = [];
  const push = (v) => {
    const abs = toAbsoluteUrl(v, baseUrl);
    if (abs) out.push(abs);
  };

  push($('meta[property="og:image"]').attr("content"));

  // Kyobo + Aladin + lazy-load generic selectors
  const selectors = [
    ".portrait_img_box img",
    ".prod_thumb img",
    ".prod_img img",
    "#CoverMainImage",
    ".cover img",
    "#EreProdBookCover img",
    "img[data-src]",
    "img[data-original]",
    "img",
  ];
  for (const selector of selectors) {
    $(selector).each((_, el) => {
      const src = $(el).attr("src");
      const dataSrc = $(el).attr("data-src");
      const dataOriginal = $(el).attr("data-original");
      const srcset = $(el).attr("srcset");
      push(src);
      push(dataSrc);
      push(dataOriginal);
      if (srcset) {
        for (const part of srcset.split(",")) {
          push(part.trim().split(" ")[0]);
        }
      }
    });
  }

  const uniq = [...new Set(out)].filter((u) => /\.(jpg|jpeg|png|webp)(\?|$)/i.test(u) || u.includes("/cover/"));
  const expanded = [];
  for (const u of uniq) {
    expanded.push(...generateFallbackCandidates(u));
  }
  return [...new Set(expanded)];
}

async function getHtml(url, referer) {
  const resp = await axios.get(url, {
    timeout: 15000,
    headers: {
      "User-Agent": USER_AGENT,
      Referer: referer || url,
      Accept: "text/html,application/xhtml+xml",
      "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    },
    validateStatus: () => true,
  });
  return resp.status >= 200 && resp.status < 300 ? resp.data : "";
}

async function findKyoboDetailUrl({ isbn, title }) {
  const query = encodeURIComponent(isbn || title);
  if (!query) return "";
  const searchUrl = `https://search.kyobobook.co.kr/search?keyword=${query}`;
  const html = await getHtml(searchUrl, "https://search.kyobobook.co.kr");
  if (!html) return "";
  const match = html.match(/https:\/\/product\.kyobobook\.co\.kr\/detail\/S\d{12}/);
  return match ? match[0] : "";
}

async function findAladinDetailUrl({ isbn, title }) {
  const query = encodeURIComponent(isbn || title);
  if (!query) return "";
  const searchUrl = `https://www.aladin.co.kr/search/wsearchresult.aspx?SearchTarget=Book&SearchWord=${query}`;
  const html = await getHtml(searchUrl, "https://www.aladin.co.kr");
  if (!html) return "";
  const match = html.match(/https?:\/\/www\.aladin\.co\.kr\/shop\/wproduct\.aspx\?ItemId=\d+/);
  return match ? match[0].replace("http://", "https://") : "";
}

async function downloadWithFallback(candidates, outputPath, referer) {
  for (const url of candidates) {
    try {
      const res = await axios.get(url, {
        responseType: "arraybuffer",
        timeout: 15000,
        headers: {
          "User-Agent": USER_AGENT,
          Referer: referer || url,
          Accept: "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
          "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        },
        validateStatus: () => true,
      });
      if (res.status < 200 || res.status >= 300) continue;
      const contentType = String(res.headers["content-type"] || "").toLowerCase();
      const data = Buffer.from(res.data);
      if (!contentType.startsWith("image/")) continue;
      if (data.length < 1024) continue;
      fs.writeFileSync(outputPath, data);
      return { ok: true, selectedUrl: url, size: data.length };
    } catch {
      // continue fallback
    }
  }
  return { ok: false, selectedUrl: "", size: 0 };
}

async function updateDbImagePath(client, row, relativePath) {
  if (row.isbn) {
    const r = await client.query("UPDATE books SET image_url = $1 WHERE isbn = $2", [relativePath, row.isbn]);
    if (r.rowCount > 0) return;
  }
  if (row.title) {
    await client.query("UPDATE books SET image_url = $1 WHERE lower(title) = lower($2)", [relativePath, row.title]);
  }
}

async function crawlOne(row, client) {
  const kyoboUrl = row.kyoboUrl || (await findKyoboDetailUrl(row));
  const aladinUrl = row.aladinUrl || (await findAladinDetailUrl(row));
  const detailUrls = [kyoboUrl, aladinUrl].filter(Boolean);

  let candidates = [];
  for (const detail of detailUrls) {
    const html = await getHtml(detail, detail);
    if (!html) continue;
    candidates.push(...extractImageCandidates(html, detail));
  }
  candidates = [...new Set(candidates)];

  const baseName = safeFileName(`${row.category || "일반"}_${row.title || row.isbn || "book"}_${row.isbn || "unknown"}`);
  const fileName = `${baseName}.jpg`;
  const absolutePath = path.join(IMAGE_DIR, fileName);
  const relativePath = `/images/${fileName}`;

  const downloaded = await downloadWithFallback(candidates, absolutePath, kyoboUrl || aladinUrl);
  if (!downloaded.ok) {
    return { ...row, ok: false, relativePath: "", selectedUrl: "" };
  }

  await updateDbImagePath(client, row, relativePath);
  return { ...row, ok: true, relativePath, selectedUrl: downloaded.selectedUrl };
}

async function main() {
  ensureDirOnce(IMAGE_DIR);
  const rows = readCsv(INPUT_CSV);
  if (rows.length === 0) {
    console.log("No rows to crawl.");
    return;
  }

  const client = new Client({ connectionString: DB_URL });
  await client.connect();
  try {
    const results = [];
    for (const row of rows) {
      const r = await crawlOne(row, client);
      results.push(r);
      const status = r.ok ? "OK" : "FAIL";
      console.log(`[${status}] ${row.title || row.isbn} ${r.relativePath || ""}`);
    }
    const okCount = results.filter((r) => r.ok).length;
    console.log(`Done. total=${results.length} success=${okCount} fail=${results.length - okCount}`);
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
