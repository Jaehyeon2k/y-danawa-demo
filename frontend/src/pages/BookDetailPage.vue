<template>
  <div class="min-h-screen bg-white text-slate-900">
    <header class="mx-auto flex w-full max-w-5xl items-center justify-between px-6 py-6">
      <h1 class="text-xl font-extrabold tracking-[0.2em] text-brand-green">Y-DANAWA</h1>
      <button class="rounded-lg border border-slate-200 px-3 py-2 text-xs" @click="goBack">검색으로</button>
    </header>

    <main class="mx-auto w-full max-w-5xl px-6 pb-16">
      <p v-if="error" class="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-700">{{ error }}</p>
      <div v-if="loading" class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-6 text-sm text-slate-500">상세 정보를 불러오는 중...</div>

      <section v-else-if="detail" class="grid gap-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:grid-cols-[220px_1fr]">
        <img :src="detail.coverUrl" :alt="detail.title" class="h-[320px] w-[220px] rounded-xl object-cover" @error="onCoverError" />

        <div class="flex flex-col gap-4">
          <div>
            <h2 class="text-2xl font-semibold">{{ detail.title || "제목 없음" }}</h2>
            <p class="mt-2 text-sm text-slate-500">{{ formatMeta(detail.author, detail.publisher) }}</p>
            <p class="mt-1 text-xs text-slate-400">ISBN13: {{ detail.isbn13 }}</p>
          </div>

          <section class="rounded-xl border border-slate-200 p-4">
            <h3 class="mb-3 text-sm font-semibold">서점 링크</h3>
            <div class="flex flex-wrap gap-2">
              <a :href="detail.vendors.aladin" target="_blank" rel="noopener noreferrer" class="rounded-lg border border-slate-200 px-3 py-2 text-xs">알라딘</a>
              <a :href="detail.vendors.kyobo" target="_blank" rel="noopener noreferrer" class="rounded-lg border border-slate-200 px-3 py-2 text-xs">교보문고</a>
              <a :href="detail.vendors.yes24" target="_blank" rel="noopener noreferrer" class="rounded-lg border border-slate-200 px-3 py-2 text-xs">YES24</a>
            </div>
          </section>

          <section class="rounded-xl border p-4" :class="libraryClass(detail.library.status, detail.library.found)">
            <div class="mb-2 flex items-center justify-between">
              <h3 class="text-sm font-semibold">학교 도서관 대출 여부</h3>
              <a
                :href="detail.library.detailUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="rounded-md border border-slate-300 px-2 py-1 text-[11px] hover:bg-slate-100"
              >
                학교 사이트 이동
              </a>
            </div>
            <p class="text-sm font-semibold">{{ detail.library.found ? detail.library.overallStatus : "정보 없음" }}</p>
            <p v-if="detail.library.recordTypePicked" class="mt-1 text-xs">선택 자료유형: {{ detail.library.recordTypePicked }}</p>
            <p v-if="detail.library.location" class="mt-1 text-xs">위치: {{ detail.library.location }}</p>
            <p v-if="detail.library.callNo" class="text-xs">청구기호: {{ detail.library.callNo }}</p>
          </section>

          <section class="rounded-xl border p-4" :class="ebookClass(detail.ebook.found)">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold">전자책 도서관</p>
                <p class="mt-1 text-xs font-semibold">{{ detail.ebook.statusText }}</p>
              </div>
              <a
                :href="detail.ebook.deepLinkUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="rounded-lg border border-emerald-400 bg-white px-3 py-2 text-xs font-semibold text-emerald-700"
              >
                전자책 사이트 이동
              </a>
            </div>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { getBookDetail, type BookDetail } from "../api/bookApi";

const props = defineProps<{
  isbn13: string;
  onNavigateSearch?: () => void;
}>();

const loading = ref(false);
const error = ref("");
const detail = ref<BookDetail | null>(null);
let activeController: AbortController | null = null;

const load = async () => {
  if (!props.isbn13) {
    detail.value = null;
    return;
  }

  if (activeController) activeController.abort();
  activeController = new AbortController();
  const signal = activeController.signal;

  loading.value = true;
  error.value = "";
  try {
    const data = await getBookDetail(props.isbn13, signal);
    if (signal.aborted) return;
    detail.value = data;
  } catch (e: any) {
    if (signal.aborted) return;
    error.value = e?.message || "상세 조회에 실패했습니다.";
    detail.value = null;
  } finally {
    if (!signal.aborted) loading.value = false;
  }
};

const libraryClass = (status: string, found: boolean) => {
  if (!found) return "border-blue-200 bg-blue-50 text-blue-700";
  if (status === "AVAILABLE") return "border-green-200 bg-green-50 text-green-700";
  if (status === "ON_LOAN") return "border-amber-200 bg-amber-50 text-amber-700";
  if (status === "RESERVED") return "border-violet-200 bg-violet-50 text-violet-700";
  if (status === "UNAVAILABLE") return "border-rose-200 bg-rose-50 text-rose-700";
  if (status === "NOT_OWNED") return "border-slate-200 bg-slate-50 text-slate-700";
  return "border-blue-200 bg-blue-50 text-blue-700";
};

const ebookClass = (found: boolean) =>
  found
    ? "border-emerald-200 bg-emerald-50 text-emerald-900"
    : "border-slate-200 bg-slate-50 text-slate-700";

const goBack = () => props.onNavigateSearch?.();

const formatMeta = (author?: string, publisher?: string) => {
  const a = String(author || "").trim();
  const p = String(publisher || "").trim();
  if (a && p) return `${a} / ${p}`;
  return a || p || "정보 없음";
};

const onCoverError = (event: Event) => {
  const img = event.target as HTMLImageElement | null;
  if (!img) return;
  img.src = "https://placehold.co/300x440?text=No+Cover";
};

watch(() => props.isbn13, () => {
  void load();
});

onMounted(() => {
  void load();
});
</script>
