<template>
  <section data-testid="admin-dashboard-view">
    <header class="admin-page-heading">
      <p>Administration</p>
      <h1>Dashboard</h1>
    </header>
    <nav
      class="resource-directory"
      aria-label="Resources"
    >
      <RouterLink
        v-for="resource in resources"
        :key="resource.to"
        :to="resource.to"
      >
        <strong>{{ resource.label }}</strong>
        <span>{{ resource.action }}</span>
      </RouterLink>
    </nav>
  </section>
</template>

<script setup lang="ts">
const resources = [
  { to: '/admin/articles', label: 'Articles', action: 'Open' },
  { to: '/admin/categories', label: 'Categories', action: 'Open' },
  { to: '/admin/tags', label: 'Tags', action: 'Open' },
  { to: '/admin/portfolio/projects', label: 'Projects', action: 'Open' },
  { to: '/admin/portfolio/profile', label: 'Profile', action: 'Open' },
  { to: '/admin/portfolio/skills', label: 'Skills', action: 'Open' },
  { to: '/admin/portfolio/experiences', label: 'Experience', action: 'Open' },
] as const
</script>

<style scoped>
.admin-page-heading p {
  margin: 0 0 6px;
  color: #2f7758;
  font-size: 12px;
  font-weight: 750;
  text-transform: uppercase;
}

.admin-page-heading h1 {
  margin: 0 0 28px;
  font-size: 30px;
  overflow-wrap: anywhere;
}

.resource-directory {
  display: grid;
  border-top: 1px solid #d8dfda;
}

.resource-directory a {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 4px;
  border-bottom: 1px solid #d8dfda;
  color: #202622;
  text-decoration: none;
}

.resource-directory span {
  color: #2f7758;
  font-size: 13px;
}
</style>
