<template>
  <div
    class="admin-layout"
    data-testid="admin-layout"
  >
    <aside class="admin-sidebar">
      <RouterLink
        class="admin-brand"
        to="/admin"
      >
        <span class="admin-brand-mark">SS</span>
        <span>Administration</span>
      </RouterLink>
      <el-menu
        class="admin-nav"
        aria-label="Administration"
        router
        :default-active="$route.path"
      >
        <el-menu-item
          v-for="item in navigation"
          :key="item.to"
          :index="item.to"
          data-testid="admin-nav-link"
        >
          {{ item.label }}
        </el-menu-item>
      </el-menu>
      <RouterLink
        class="admin-exit"
        data-testid="admin-exit-link"
        to="/"
      >
        Back to site
      </RouterLink>
    </aside>
    <main class="admin-workspace">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
const navigation = [
  { to: '/admin', label: 'Dashboard' },
  { to: '/admin/articles', label: 'Articles' },
  { to: '/admin/categories', label: 'Categories' },
  { to: '/admin/tags', label: 'Tags' },
  { to: '/admin/portfolio/projects', label: 'Projects' },
  { to: '/admin/portfolio/profile', label: 'Profile' },
  { to: '/admin/portfolio/skills', label: 'Skills' },
  { to: '/admin/portfolio/experiences', label: 'Experience' },
] as const
</script>

<style scoped>
.admin-layout {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f5f7f5;
}

.admin-sidebar {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 20px 14px;
  border-right: 1px solid #d8dfda;
  background: #202622;
  color: #f7faf8;
}

.admin-brand {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 0 8px 20px;
  color: #ffffff;
  font-weight: 750;
  text-decoration: none;
  overflow-wrap: anywhere;
}

.admin-brand-mark {
  display: inline-grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border: 1px solid #718078;
  font-size: 11px;
}

.admin-nav {
  display: grid;
  gap: 3px;
  border-right: 0;
  background: transparent;
}

.admin-nav :deep(.el-menu-item),
.admin-exit {
  min-width: 0;
  height: auto;
  min-height: 38px;
  line-height: 1.35;
  padding: 9px 10px;
  color: #cbd4ce;
  font-size: 14px;
  text-decoration: none;
  overflow-wrap: anywhere;
}

.admin-nav :deep(.el-menu-item.is-active) {
  background: #354139;
  color: #ffffff;
}

.admin-nav :deep(.el-menu-item:hover),
.admin-nav :deep(.el-menu-item:focus) {
  background: #2c352f;
  color: #ffffff;
}

.admin-exit {
  margin-top: auto;
  border-top: 1px solid #465149;
  padding-top: 16px;
}

.admin-workspace {
  min-width: 0;
  padding: 28px clamp(18px, 4vw, 52px) 64px;
  overflow-x: hidden;
}

@media (max-width: 760px) {
  .admin-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .admin-sidebar {
    position: static;
    padding: 14px 16px;
    border-right: 0;
    border-bottom: 1px solid #d8dfda;
  }

  .admin-brand {
    padding-bottom: 12px;
  }

  .admin-nav {
    display: flex;
    max-width: 100%;
    gap: 4px;
    overflow-x: auto;
    overscroll-behavior-inline: contain;
  }

  .admin-nav :deep(.el-menu-item) {
    flex: 0 0 auto;
    white-space: nowrap;
  }

  .admin-exit {
    margin-top: 10px;
    padding: 10px;
    border-top: 0;
  }

  .admin-workspace {
    padding: 22px 16px 48px;
  }
}
</style>
