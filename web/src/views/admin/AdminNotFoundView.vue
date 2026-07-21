<template>
  <section data-testid="admin-not-found-view">
    <p class="admin-kicker">
      Administration
    </p>
    <h1>Page not found</h1>
    <RouterLink to="/admin">
      Return to dashboard
    </RouterLink>
  </section>
</template>

<style scoped>
.admin-kicker { color: #2f7758; font-size: 12px; font-weight: 750; text-transform: uppercase; }
h1 { margin: 8px 0 20px; font-size: 30px; overflow-wrap: anywhere; }
</style>
