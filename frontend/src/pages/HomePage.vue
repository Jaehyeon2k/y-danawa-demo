<template>
  <div class="min-h-screen bg-white text-slate-900">
    <header class="mx-auto flex w-full max-w-6xl items-center justify-between gap-3 px-6 py-6">
      <h1 class="text-xl font-extrabold tracking-[0.2em] text-brand-green">Y-DANAWA</h1>
      <div class="text-xs text-slate-500">다나와식 책 검색</div>
    </header>

    <main class="mx-auto w-full max-w-6xl px-6 pb-20">
      <section class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div class="flex items-center gap-2">
          <input
            v-model="query"
            placeholder="도서명 또는 ISBN13 검색"
            class="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none"
            @keyup.enter="submitSearch"
          />
          <button class="rounded-xl bg-brand-green px-4 py-3 text-sm font-semibold text-white" @click="submitSearch">검색</button>
        </div>
        <div class="mt-3 text-xs text-slate-500">
          <span v-if="loading">검색 중...</span>
          <span v-else-if="searched">총 {{ items.length }}건</span>
        </div>
      </section>

      <p v-if="error" class="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-700">{{ error }}</p>

      <section class="mt-5 flex flex-col gap-3">
        <article
          v-for="book in pagedItems"
          :key="bookKey(book)"
          class="flex w-full gap-3 rounded-2xl border border-slate-200 bg-white p-3"
        >
          <img :src="book.thumbUrl" :alt="book.title" class="h-28 w-20 rounded-lg object-cover" loading="lazy" @error="onImgError" />
          <div class="flex min-w-0 flex-1 flex-col gap-1">
            <h3 class="line-clamp-2 text-sm font-semibold">{{ book.title || "제목 없음" }}</h3>
            <p v-if="book.author || book.publisher" class="line-clamp-2 text-xs text-slate-500">
              {{ formatMeta(book.author, book.publisher) }}
            </p>
            <p class="text-[11px] text-slate-400">ISBN13: {{ book.isbn13 || "없음" }}</p>
            <button
              class="mt-auto rounded-lg px-3 py-2 text-xs font-semibold"
              :class="book.isbn13 ? 'bg-brand-blue text-white' : 'bg-slate-200 text-slate-500 cursor-not-allowed'"
              :disabled="!book.isbn13"
              @click="openDetail(book)"
            >
              상세보기
            </button>
          </div>
        </article>
      </section>

      <div v-if="searched && !loading && items.length === 0" class="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600">
        검색 결과가 없습니다.
      </div>

      <section v-if="searched && !loading && totalPages > 1" class="mt-6 flex items-center justify-center gap-2">
        <button
          class="rounded-lg border border-slate-300 px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="currentPage === 1"
          @click="goToPage(currentPage - 1)"
        >
          이전
        </button>
        <button
          v-for="p in visiblePages"
          :key="p"
          class="rounded-lg border px-3 py-1 text-xs"
          :class="p === currentPage ? 'border-brand-green bg-brand-green text-white' : 'border-slate-300 text-slate-700'"
          @click="goToPage(p)"
        >
          {{ p }}
        </button>
        <button
          class="rounded-lg border border-slate-300 px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="currentPage === totalPages"
          @click="goToPage(currentPage + 1)"
        >
          다음
        </button>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { getSearchBooks, type SearchBookItem } from "../api/bookApi";

const props = defineProps<{
  initialQuery?: string;
  onNavigateDetail?: (isbn13: string) => void;
  onNavigateSearch?: (query: string) => void;
}>();

const query = ref(props.initialQuery || "");
const loading = ref(false);
const searched = ref(false);
const error = ref("");
const items = ref<SearchBookItem[]>([]);
const currentPage = ref(1);
const pageSize = 10;
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let activeController: AbortController | null = null;

const bookKey = (book: SearchBookItem) => (book.isbn13 ? `isbn13:${book.isbn13}` : `noisbn:${book.title}`);

const formatMeta = (author?: string, publisher?: string) => {
  const a = String(author || "").trim();
  const p = String(publisher || "").trim();
  if (a && p) return `${a} / ${p}`;
  return a || p;
};

const runSearch = async () => {
  const keyword = query.value.trim();
  props.onNavigateSearch?.(keyword);

  if (!keyword) {
    searched.value = false;
    items.value = [];
    error.value = "";
    currentPage.value = 1;
    return;
  }

  if (activeController) activeController.abort();
  activeController = new AbortController();
  const signal = activeController.signal;

  loading.value = true;
  searched.value = true;
  error.value = "";
  currentPage.value = 1;

  try {
    const result = await getSearchBooks(keyword, signal);
    if (signal.aborted) return;
    items.value = result;
  } catch (e: any) {
    if (signal.aborted) return;
    error.value = e?.message || "검색 중 오류가 발생했습니다.";
    items.value = [];
  } finally {
    if (!signal.aborted) loading.value = false;
  }
};

const submitSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    void runSearch();
  }, 300);
};

const openDetail = (book: SearchBookItem) => {
  if (!book.isbn13) return;
  props.onNavigateDetail?.(book.isbn13);
};

const onImgError = (event: Event) => {
  const img = event.target as HTMLImageElement | null;
  if (!img) return;
  img.src = "https://placehold.co/300x440?text=No+Cover";
};

const totalPages = computed(() => {
  const total = items.value.length;
  return total === 0 ? 1 : Math.ceil(total / pageSize);
});

const pagedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return items.value.slice(start, start + pageSize);
});

const visiblePages = computed(() => {
  const pages: number[] = [];
  const start = Math.max(1, currentPage.value - 2);
  const end = Math.min(totalPages.value, start + 4);
  for (let p = start; p <= end; p += 1) pages.push(p);
  return pages;
});

const goToPage = (page: number) => {
  if (page < 1 || page > totalPages.value) return;
  currentPage.value = page;
  window.scrollTo({ top: 0, behavior: "smooth" });
};

watch(
  () => props.initialQuery,
  (next) => {
    if (typeof next === "string") {
      query.value = next;
    }
  }
);

onMounted(() => {
  if (query.value.trim()) {
    void runSearch();
  }
});
</script>
