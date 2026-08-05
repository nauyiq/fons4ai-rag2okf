<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import Rag2OkfAppShell from './layouts/Rag2OkfAppShell.vue'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const isPublicPage = computed(() => route.meta.public === true)

sessionStore.configureExpiryHandler(() => {
  if (route.name !== 'login') router.replace({ name: 'login', query: { expired: '1' } })
})
</script>

<template>
  <RouterView v-if="isPublicPage" />
  <Rag2OkfAppShell v-else><RouterView /></Rag2OkfAppShell>
</template>
