<script setup lang="ts">
import { BookOpen, Users, ArrowUpRight } from '@lucide/vue'
import { mediaUrl } from '../lib/api'
import { getJoinedStudyGroups } from '../features/study-groups/groupApi'
import { useRequest } from '../composables/useRequest'
import RequestState from '../components/RequestState.vue'
import type { StudyGroupSummary } from '../features/study-groups/contracts'
const request = useRequest<StudyGroupSummary[]>()
const load = () => request.execute(getJoinedStudyGroups)
void load()
</script>
<template>
  <section class="page groups-page">
    <header class="page-heading">
      <div>
        <h1>读书小组</h1>
        <p class="muted">围绕同一本书，交换不同的理解。</p>
      </div>
      <RouterLink to="/app/library" class="button secondary">从书架加入小组</RouterLink>
    </header>
    <RequestState
      :loading="request.loading.value"
      :error="request.error.value"
      :empty="!request.data.value?.length"
      @retry="load"
      ><div class="card-grid">
        <RouterLink
          v-for="group in request.data.value"
          :key="group.bookId"
          :to="`/app/groups/${group.bookId}`"
          class="panel stack group-card"
          ><div class="group-cover">
            <img
              v-if="group.coverUrl"
              :src="mediaUrl(group.coverUrl)"
              :alt="`${group.title} 封面`"
            /><BookOpen v-else :size="36" />
          </div>
          <h2>{{ group.title }}</h2>
          <p class="muted">{{ group.author }}</p>
          <span class="group-card-footer"
            ><Users :size="18" />{{ group.memberCount }} 位共读者<ArrowUpRight :size="19" /></span
        ></RouterLink></div
    ></RequestState>
  </section>
</template>
