<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section data-testid="admin-section-view">
    <p class="admin-kicker">
      Administration
    </p>
    <h1>{{ title }}</h1>
  </section>
</template>

<style scoped>
.admin-kicker { margin: 0 0 6px; color: #2f7758; font-size: 12px; font-weight: 750; text-transform: uppercase; }
h1 { margin: 0; font-size: 30px; overflow-wrap: anywhere; }
</style>
