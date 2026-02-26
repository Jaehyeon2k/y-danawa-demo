<template>
  <div class="skeleton-card">
    <div class="skeleton-image"></div>
    <div class="skeleton-info">
      <div class="skeleton-title"></div>
      <div class="skeleton-author"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 스켈레톤 카드 - 로딩 상태 표시
</script>

<style scoped>
.skeleton-card {
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.skeleton-image {
  width: 100%;
  padding-top: 140%; /* 7:10 비율 */
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-info {
  padding: 12px;
}

.skeleton-title {
  height: 16px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 8px;
}

.skeleton-author {
  height: 12px;
  width: 60%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
