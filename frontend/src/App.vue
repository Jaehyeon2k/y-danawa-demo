<template>
  <HomePage
    v-if="route.kind === 'search'"
    :initialQuery="route.query"
    :onNavigateDetail="navigateDetail"
    :onNavigateSearch="updateSearchQuery"
  />
  <BookDetailPage
    v-else
    :isbn13="route.isbn13"
    :onNavigateSearch="navigateSearch"
  />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import HomePage from "./pages/HomePage.vue";
import BookDetailPage from "./pages/BookDetailPage.vue";

type RouteState =
  | { kind: "search"; query: string }
  | { kind: "detail"; isbn13: string };

const parseRoute = (): RouteState => {
  const path = window.location.pathname;
  const searchParams = new URLSearchParams(window.location.search);

  if (path.startsWith("/book/")) {
    const isbn13 = path.replace("/book/", "").replace(/[^0-9]/g, "");
    return { kind: "detail", isbn13 };
  }

  const q = searchParams.get("q") || "";
  return { kind: "search", query: q };
};

const route = ref<RouteState>(parseRoute());

const onPopState = () => {
  route.value = parseRoute();
};

const navigateDetail = (isbn13: string) => {
  const normalized = String(isbn13 || "").replace(/[^0-9]/g, "");
  if (!/^97[89]\d{10}$/.test(normalized)) return;
  history.pushState({}, "", `/book/${normalized}`);
  route.value = { kind: "detail", isbn13: normalized };
};

const navigateSearch = () => {
  const query = route.value.kind === "search" ? route.value.query : "";
  const qs = query ? `?q=${encodeURIComponent(query)}` : "";
  history.pushState({}, "", `/search${qs}`);
  route.value = { kind: "search", query };
};

const updateSearchQuery = (query: string) => {
  if (route.value.kind !== "search") return;
  const normalized = String(query || "").trim();
  const qs = normalized ? `?q=${encodeURIComponent(normalized)}` : "";
  history.replaceState({}, "", `/search${qs}`);
  route.value = { kind: "search", query: normalized };
};

onMounted(() => {
  window.addEventListener("popstate", onPopState);
  if (window.location.pathname === "/") {
    history.replaceState({}, "", "/search");
    route.value = { kind: "search", query: "" };
  }
});

onUnmounted(() => {
  window.removeEventListener("popstate", onPopState);
});
</script>
