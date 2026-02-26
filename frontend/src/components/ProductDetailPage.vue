<template>
  <section class="mx-auto w-full max-w-6xl space-y-6 px-4 py-6">
    <div class="grid gap-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:grid-cols-[260px_1fr]">
      <div class="overflow-hidden rounded-xl bg-slate-100 shadow-xl">
        <img
          :src="coverSrc"
          :alt="bookInfo.title"
          class="h-full w-full object-cover"
          loading="lazy"
          referrerpolicy="no-referrer"
          @error="onCoverError"
        />
      </div>

      <div class="space-y-4">
        <h1 class="text-2xl font-extrabold text-slate-900">{{ bookInfo.title }}</h1>
        <p v-if="cleanAuthorPublisher" class="text-sm text-slate-600">{{ cleanAuthorPublisher }}</p>
        <p v-if="isbn13" class="text-xs text-slate-500">ISBN-13: {{ isbn13 }}</p>

        <div v-if="bookInfo.bestPrice > 0" class="flex flex-wrap items-end gap-3">
          <s v-if="bookInfo.listPrice > 0 && bookInfo.listPrice !== bookInfo.bestPrice" class="text-sm text-slate-400">{{ formatPrice(bookInfo.listPrice) }}</s>
          <span class="text-2xl font-extrabold text-emerald-600">{{ formatPrice(bookInfo.bestPrice) }}</span>
        </div>

        <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div class="mb-2 flex items-center justify-between text-xs text-slate-600">
            <span>Time-Money Slider</span>
            <span>{{ sliderLabel }}</span>
          </div>
          <input v-model.number="timeMoneySlider" type="range" min="0" max="1" step="0.01" class="w-full" />
        </div>
      </div>
    </div>

    <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <table class="w-full table-fixed text-sm">
        <thead class="bg-slate-100 text-left text-xs uppercase tracking-wide text-slate-600">
          <tr>
            <th class="px-4 py-3">梨꾨꼸</th>
            <th class="px-4 py-3">?곹깭</th>
            <th class="px-4 py-3">媛寃?/th>
            <th class="px-4 py-3">?≪뀡</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in visibleSortedChannels"
            :key="item.channel"
            class="border-t border-slate-100 transition-colors hover:bg-slate-50"
          >
            <td class="px-4 py-3 font-semibold text-slate-800">{{ item.logo }}</td>
            <td class="px-4 py-3">
              <span
                v-if="item.channel === 'LIBRARY'"
                :class="item.status === '?異?媛?? ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'"
                class="rounded-full px-2 py-1 text-xs font-bold"
              >
                {{ item.status }}
              </span>
              <span v-else class="text-slate-600">{{ item.status }}</span>
            </td>
            <td class="px-4 py-3 font-bold text-slate-900">{{ formatPriceIfVisible(item.price) }}</td>
            <td class="px-4 py-3">
              <button
                class="rounded-lg bg-slate-900 px-3 py-2 text-xs font-semibold text-white hover:bg-slate-700"
                @click="handleChannelClick(item)"
              >
                {{ item.channel === "LIBRARY" ? "?꾩꽌愿 ?대룞" : "援щℓ ?대룞" }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import api from "../api/api";
import { normalizeImageUrl } from "../api/bookApi";

interface BookInfo {
  isbn13: string;
  title: string;
  author: string;
  publisher: string;
  coverImage?: string;
  listPrice: number;
  bestPrice: number;
  libraryAvailable?: boolean;
  kyoboPrice?: number;
  yes24Price?: number;
  aladinPrice?: number;
}

interface ChannelRow {
  channel: "LIBRARY" | "KYOBO" | "YES24" | "ALADIN";
  logo: string;
  status: string;
  price: number | string | null;
  timeScore: number;
  url: string;
}

const props = defineProps<{
  bookInfo: BookInfo;
}>();

const fallbackCover =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='320' height='460' viewBox='0 0 320 460'%3E%3Crect width='100%25' height='100%25' fill='%23e2e8f0'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%2364758b' font-family='sans-serif' font-size='18'%3ENo Image%3C/text%3E%3C/svg%3E";

const coverSrc = ref(normalizeImageUrl(props.bookInfo.coverImage) || fallbackCover);

const onCoverError = () => {
  if (coverSrc.value !== fallbackCover) {
    coverSrc.value = fallbackCover;
  }
};

const timeMoneySlider = ref(0.5);

const isbn13 = computed(() => {
  const onlyDigits = (props.bookInfo.isbn13 || "").replace(/\D/g, "");
  return /^\d{13}$/.test(onlyDigits) ? onlyDigits : "";
});

const sliderLabel = computed(() => {
  if (timeMoneySlider.value < 0.34) return "?쒓컙 ?곗꽑";
  if (timeMoneySlider.value > 0.66) return "鍮꾩슜 ?곗꽑";
  return "洹좏삎";
});

const baseChannels = computed<ChannelRow[]>(() => {
  const id = isbn13.value;
  return [
    {
      channel: "LIBRARY",
      logo: "?곸쭊?꾨Ц? ?꾩꽌愿",
      status: props.bookInfo.libraryAvailable === false ? "?異?以? : "?異?媛??,
      price: 0,
      timeScore: 1,
      url: `https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/${id}`,
    },
    {
      channel: "KYOBO",
      logo: "援먮낫臾멸퀬",
      status: "援щℓ 媛??,
      price: props.bookInfo.kyoboPrice ?? props.bookInfo.bestPrice,
      timeScore: 4,
      url: `https://search.kyobobook.co.kr/search?keyword=${id}&vPstrKeyWord=${id}`,
    },
    {
      channel: "YES24",
      logo: "YES24",
      status: "援щℓ 媛??,
      price: props.bookInfo.yes24Price ?? props.bookInfo.bestPrice,
      timeScore: 3,
      url: `https://www.yes24.com/Product/Search?domain=BOOK&query=${id}`,
    },
    {
      channel: "ALADIN",
      logo: "?뚮씪??,
      status: "援щℓ 媛??,
      price: props.bookInfo.aladinPrice ?? props.bookInfo.bestPrice,
      timeScore: 3,
      url: `https://www.aladin.co.kr/search/wsearchresult.aspx?SearchTarget=Book&SearchWord=${id}`,
    },
  ];
});

const hasVisiblePrice = (value: unknown): boolean => {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") {
    const normalized = value.trim();
    if (!normalized || normalized === "0원" || normalized === "가격 정보 없음") return false;
    const digits = normalized.replace(/[^0-9]/g, "");
    return !!digits && Number(digits) > 0;
  }
  if (typeof value === "number") {
    return value > 0;
  }
  return false;
};

const visibleSortedChannels = computed(() => {
  const rows = [...baseChannels.value];
  const visibleRows = rows.filter((r) => r.channel === "LIBRARY" || hasVisiblePrice(r.price));
  const numericPrices = visibleRows
    .map((r) => (typeof r.price === "number" ? r.price : Number(String(r.price).replace(/[^0-9]/g, ""))))
    .filter((v) => Number.isFinite(v) && v > 0);
  const maxPrice = Math.max(...numericPrices, 1);
  const maxTime = Math.max(...rows.map((r) => r.timeScore), 1);

  return visibleRows.sort((a, b) => {
    const priceA = typeof a.price === "number" ? a.price : Number(String(a.price).replace(/[^0-9]/g, "")) || 0;
    const priceB = typeof b.price === "number" ? b.price : Number(String(b.price).replace(/[^0-9]/g, "")) || 0;
    const scoreA = timeMoneySlider.value * (priceA / maxPrice) + (1 - timeMoneySlider.value) * (a.timeScore / maxTime);
    const scoreB = timeMoneySlider.value * (priceB / maxPrice) + (1 - timeMoneySlider.value) * (b.timeScore / maxTime);
    return scoreA - scoreB;
  });
});

const formatPrice = (value: number) => {
  if (!value || value <= 0) return "";
  return `${value.toLocaleString("ko-KR")}원`;
};


const formatPriceIfVisible = (value: unknown) => {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") {
    const normalized = value.trim();
    if (!normalized || normalized === "0원" || normalized === "가격 정보 없음") return "";
    const digits = normalized.replace(/[^0-9]/g, "");
    if (!digits || Number(digits) <= 0) return "";
    return `${Number(digits).toLocaleString("ko-KR")}원`;
  }
  if (typeof value === "number") {
    return value > 0 ? formatPrice(value) : "";
  }
  return "";
};
const cleanText = (value?: string): string => {
  if (!value) return "";
  const trimmed = value.trim().replace(/^\/+|\/+$/g, "").trim();
  const invalidTokens = new Set(["/", "-", "?뺣낫 ?놁쓬", "????뺣낫 ?놁쓬", "異쒗뙋???뺣낫 ?놁쓬"]);
  if (!trimmed || invalidTokens.has(trimmed)) return "";
  if (/^[\/\-\s]+$/.test(trimmed)) return "";
  return trimmed;
};

const cleanAuthorPublisher = computed(() => {
  const a = cleanText(props.bookInfo.author);
  const p = cleanText(props.bookInfo.publisher);
  if (a && p) return `${a} | ${p}`;
  if (a) return a;
  if (p) return p;
  return "";
});

const logClick = async (isbn: string, targetChannel: string) => {
  await api.post("/logs/click", {
    isbn,
    target_channel: targetChannel,
    slider_value: timeMoneySlider.value,
  });
};

const handleChannelClick = async (item: ChannelRow) => {
  if (!isbn13.value || !item.url) return;
  try {
    await logClick(isbn13.value, item.channel);
  } catch (error) {
    console.error("click log failed", error);
  }
  window.open(item.url, "_blank", "noopener,noreferrer");
};
</script>

