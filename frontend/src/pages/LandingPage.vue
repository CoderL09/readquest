<script setup lang="ts">
import { BookOpen, ArrowUpRight, NotebookPen, Users, AudioLines } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
</script>
<template>
  <div class="landing">
    <header class="landing-header">
      <RouterLink to="/" class="brand"><BookOpen />ReadQuest</RouterLink>
      <nav>
        <RouterLink to="/login">登录</RouterLink
        ><RouterLink class="button small" :to="auth.authenticated ? '/app' : '/register'"
          >开始阅读</RouterLink
        >
      </nav>
    </header>
    <section class="landing-hero">
      <div>
        <p class="muted">给每一个想读懂原文的人</p>
        <h1>不止读过，<br />更要读进去。</h1>
        <p class="hero-description">
          从一本真正想读的书开始。遇到不懂的句子，得到恰到好处的帮助；读过的词句，慢慢变成自己的表达。
        </p>
        <RouterLink class="button" :to="auth.authenticated ? '/app' : '/register'"
          >开启我的阅读旅程 <ArrowUpRight :size="18"
        /></RouterLink>
        <p class="hero-footnote">原文阅读 · 语境辅助 · 间隔复习</p>
      </div>
      <div class="reading-illustration" aria-label="阅读体验预览">
        <div class="illustration-book">
          <small>A moment by the river</small>
          <p>We sat by the river<br />and watched<br />the morning light.</p>
          <span>Read at your own pace.</span>
        </div>
        <div class="illustration-note">
          <NotebookPen :size="19" /><strong>把故事读成自己的记忆</strong>
          <p>生词、感想和理解，都有地方安放。</p>
        </div>
      </div>
    </section>
    <section class="landing-features">
      <article>
        <BookOpen />
        <h2>把阅读交还给故事</h2>
        <p>上传 EPUB、PDF 或 TXT，也可以从公开网页开始。保留章节、图片和阅读进度。</p>
      </article>
      <article>
        <AudioLines />
        <h2>在语境里理解</h2>
        <p>自然语块、原文朗读和阅读对话，帮助你理解正在读的句子。</p>
      </article>
      <article>
        <Users />
        <h2>一起读得更远</h2>
        <p>在读书小组交流，用复习巩固词句，看见一点一滴的成长。</p>
      </article>
    </section>
    <footer class="app-footer">ReadQuest · Read beyond the page.</footer>
  </div>
</template>
