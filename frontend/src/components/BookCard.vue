<template>
  <div class="book-card" @click="$emit('click', book)">
    <div class="book-image-container">
      <img
        :src="getImageUrl()"
        :alt="book.title"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="handleImageError($event)"
        class="book-image"
      />
    </div>
    <div class="book-info">
      <h3 class="book-title">{{ book.title }}</h3>
      <p class="book-author" v-if="cleanText(book.author)">{{ cleanText(book.author) }}</p>
      <p class="book-publisher" v-if="cleanText(book.publisher)">{{ cleanText(book.publisher) }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { normalizeImageUrl } from '../api/bookApi';

interface Book {
  isbn: string;
  title: string;
  author: string;
  publisher?: string;
  imageUrl?: string;
  backupImageUrl?: string;
  publishedDate?: string;
  price?: number;
}

interface Props {
  book: Book;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'click', book: Book): void;
}>();

const cleanText = (value?: string): string => {
  if (!value) return "";
  const trimmed = value.trim().replace(/^\/+|\/+$/g, "").trim();
  const invalidTokens = new Set(["/", "-", "정보 없음", "저자 정보 없음", "출판사 정보 없음"]);
  if (!trimmed || invalidTokens.has(trimmed)) return "";
  if (/^[\/\-\s]+$/.test(trimmed)) return "";
  return trimmed;
};

const failoverIndex = ref(0);

const fallbackCover =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='480' height='696' viewBox='0 0 480 696'%3E%3Cdefs%3E%3ClinearGradient id='grad' x1='0%25' y1='0%25' x2='100%25' y2='100%25'%3E%3Cstop offset='0%25' style='stop-color:%23e2e8f0;stop-opacity:1'/%3E%3Cstop offset='100%25' style='stop-color:%23cbd5e1;stop-opacity:1'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='100%25' height='100%25' fill='url(%23grad)'/%3E%3Cpath d='M240 280 L200 320 L200 420 L280 420 L280 320 Z' fill='%2394a3b8' opacity='0.3'/%3E%3Ccircle cx='240' cy='250' r='30' fill='%2394a3b8' opacity='0.3'/%3E%3Ctext x='50%25' y='55%25' dominant-baseline='middle' text-anchor='middle' fill='%2364748b' font-family='system-ui, -apple-system, sans-serif' font-size='16' font-weight='500'%3E이미지 없음%3C/text%3E%3C/svg%3E";

const imageCandidates = () => {
  const urls: string[] = [];
  const primary = normalizeImageUrl(props.book.imageUrl);
  const backup = normalizeImageUrl(props.book.backupImageUrl);
  if (primary) urls.push(primary);
  if (backup && backup !== primary) urls.push(backup);
  // No OpenLibrary fallback here — it returns 404 for missing covers.
  // The SVG placeholder is a better user experience.
  return urls;
};

const getImageUrl = () => {
  const candidates = imageCandidates();
  return candidates[failoverIndex.value] || fallbackCover;
};

const handleImageError = (event: Event) => {
  const target = event.target as HTMLImageElement | null;
  if (!target) return;
  const candidates = imageCandidates();
  if (failoverIndex.value + 1 < candidates.length) {
    failoverIndex.value += 1;
    target.src = candidates[failoverIndex.value];
    return;
  }
  // All candidates exhausted — use fallback and stop retrying
  if (target.src !== fallbackCover) {
    target.src = fallbackCover;
  }
};
</script>

<style scoped>
.book-card {
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.book-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.book-image-container {
  position: relative;
  width: 100%;
  padding-top: 140%; /* 7:10 비율 (도서 표지) */
  overflow: hidden;
  background: #f5f5f5;
}

.book-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.book-card:hover .book-image {
  transform: scale(1.05);
}

.book-info {
  padding: 12px;
}

.book-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  color: #333;
}

.book-author {
  font-size: 12px;
  color: #666;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-publisher {
  font-size: 11px;
  color: #999;
  margin: 4px 0 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
