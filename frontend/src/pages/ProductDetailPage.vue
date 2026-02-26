<template>
  <ProductDetailPageComponent :book-info="bookInfo" />
</template>

<script setup lang="ts">
import ProductDetailPageComponent from "../components/ProductDetailPage.vue";

const bookInfo = {
  isbn13: "9791162243770",
  title: "혼자 공부하는 자바",
  author: "신용권",
  publisher: "한빛미디어",
  coverImage: "https://image.aladin.co.kr/product/26675/3/cover500/k992730124_1.jpg",
  listPrice: 25000,
  bestPrice: 22000,
  libraryAvailable: true,
  kyoboPrice: 22500,
  yes24Price: 22100,
  aladinPrice: 22000,
};
</script>
