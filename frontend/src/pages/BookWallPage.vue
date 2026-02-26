<template>
  <div class="book-grid-container">
    <h1 class="page-title">교보문고 도서 그리드</h1>
    
    <!-- 도서 그리드 -->
    <div class="book-grid">
      <BookCard
        v-for="book in books"
        :key="book.isbn"
        :book="book"
        @click="handleBookClick"
      />
      
      <!-- 로딩 스켈레톤 -->
      <SkeletonCard v-for="i in skeletonCount" :key="'skeleton-' + i" v-if="loading" />
    </div>
    
    <!-- 무한 스크롤 트리거 -->
    <div ref="loadMoreTrigger" class="load-more-trigger"></div>
    
    <!-- 더 이상 없음 메시지 -->
    <div v-if="!hasMore && books.length > 0" class="no-more">
      모든 도서를 불러왔습니다
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import BookCard from '../components/BookCard.vue';
import SkeletonCard from '../components/SkeletonCard.vue';
import { normalizeImageUrl } from '../api/bookApi';

interface Book {
  isbn: string;
  title: string;
  author: string;
  publisher?: string;
  imageUrl?: string;
  publishedDate?: string;
  price?: number;
}

interface InfiniteScrollResponse {
  data: Book[];
  nextCursor: string | null;
  hasMore: boolean;
}

const books = ref<Book[]>([]);
const loading = ref(false);
const hasMore = ref(true);
const nextCursor = ref<string | null>(null);
const skeletonCount = 30;

let observer: IntersectionObserver | null = null;
const loadMoreTrigger = ref<HTMLElement | null>(null);

// 도서 데이터 로드
const loadBooks = async () => {
  if (loading.value || !hasMore.value) return;
  
  loading.value = true;
  
  try {
    const params = new URLSearchParams({
      limit: '30'
    });
    
    if (nextCursor.value) {
      params.append('cursor', nextCursor.value);
    }
    
    const response = await fetch(`/api/books/infinite?${params}`);
    
    if (!response.ok) {
      throw new Error('Failed to load books');
    }
    
    const data: InfiniteScrollResponse = await response.json();

    const normalized = data.data.map((b) => ({
      ...b,
      imageUrl: normalizeImageUrl(b.imageUrl) || undefined,
    }));
    books.value.push(...normalized);
    nextCursor.value = data.nextCursor;
    hasMore.value = data.hasMore;
    
  } catch (error) {
    console.error('Error loading books:', error);
  } finally {
    loading.value = false;
  }
};

// 도서 클릭 핸들러
const handleBookClick = (book: Book) => {
  // TODO: 도서 상세 페이지로 이동
  console.log('Book clicked:', book);
};

// Intersection Observer 설정
onMounted(() => {
  // 초기 로드
  loadBooks();
  
  // Intersection Observer 생성
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        loadBooks();
      }
    },
    {
      rootMargin: '200px' // 화면 끝에서 200px 전에 미리 로드
    }
  );
  
  // 트리거 요소 관찰 시작
  if (loadMoreTrigger.value) {
    observer.observe(loadMoreTrigger.value);
  }
});

// 컴포넌트 언마운트 시 정리
onUnmounted(() => {
  if (observer) {
    observer.disconnect();
  }
});
</script>

<style scoped>
.book-grid-container {
  max-width: 1920px;
  margin: 0 auto;
  padding: 32px 24px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 32px 0;
  color: #111;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 24px;
  margin-bottom: 40px;
}

/* 반응형 그리드 */
@media (min-width: 1920px) {
  .book-grid {
    grid-template-columns: repeat(6, 1fr);
  }
}

@media (min-width: 1280px) and (max-width: 1919px) {
  .book-grid {
    grid-template-columns: repeat(5, 1fr);
  }
}

@media (min-width: 1024px) and (max-width: 1279px) {
  .book-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (min-width: 768px) and (max-width: 1023px) {
  .book-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
}

@media (max-width: 767px) {
  .book-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  
  .book-grid-container {
    padding: 16px 12px;
  }
  
  .page-title {
    font-size: 24px;
    margin-bottom: 16px;
  }
}

.load-more-trigger {
  height: 1px;
  margin: 20px 0;
}

.no-more {
  text-align: center;
  padding: 40px;
  color: #999;
  font-size: 14px;
}
</style>
