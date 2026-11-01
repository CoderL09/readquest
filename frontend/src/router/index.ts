import { createRouter, createWebHistory } from 'vue-router'
import type { Pinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
export function createAppRouter(pinia: Pinia) {
  const router = createRouter({
    history: createWebHistory(),
    scrollBehavior: () => ({ top: 0 }),
    routes: [
      {
        path: '/',
        component: () => import('../pages/LandingPage.vue'),
        meta: { title: 'ReadQuest' },
      },
      { path: '/login', component: () => import('../pages/AuthPage.vue'), meta: { title: '登录' } },
      {
        path: '/register',
        component: () => import('../pages/AuthPage.vue'),
        meta: { title: '注册' },
      },
      {
        path: '/app',
        component: () => import('../layouts/AppLayout.vue'),
        meta: { requiresAuth: true },
        children: [
          {
            path: '',
            component: () => import('../pages/HomePage.vue'),
            meta: { title: '发现阅读' },
          },
          {
            path: 'library',
            component: () => import('../pages/LibraryPage.vue'),
            meta: { title: '我的书架' },
          },
          {
            path: 'upload',
            component: () => import('../pages/UploadPage.vue'),
            meta: { title: '上传书籍' },
          },
          {
            path: 'sources',
            component: () => import('../pages/SourcesPage.vue'),
            meta: { title: '添加阅读' },
          },
          {
            path: 'vocabulary',
            component: () => import('../pages/VocabularyPage.vue'),
            meta: { title: '生词' },
          },
          {
            path: 'notes',
            component: () => import('../pages/NotesPage.vue'),
            meta: { title: '阅读笔记' },
          },
          {
            path: 'groups',
            component: () => import('../pages/GroupsPage.vue'),
            meta: { title: '读书小组' },
          },
          {
            path: 'groups/:bookId',
            component: () => import('../pages/GroupRoomPage.vue'),
            meta: { title: '小组讨论' },
          },
          {
            path: 'profile',
            component: () => import('../pages/ProfilePage.vue'),
            meta: { title: '个人资料' },
          },
          {
            path: 'billing',
            component: () => import('../pages/BillingPage.vue'),
            meta: { title: '会员与激活' },
          },
          {
            path: 'journey',
            component: () => import('../pages/JourneyPage.vue'),
            meta: { title: '阅读成长' },
          },
          {
            path: 'passport',
            component: () => import('../pages/PassportPage.vue'),
            meta: { title: '阅读护照' },
          },
          {
            path: 'calibration',
            component: () => import('../pages/CalibrationPage.vue'),
            meta: { title: '阅读校准' },
          },
          {
            path: 'review',
            component: () => import('../pages/ReviewPage.vue'),
            meta: { title: '今日复习' },
          },
          {
            path: 'path',
            component: () => import('../pages/ReadingPathPage.vue'),
            meta: { title: '继续阅读' },
          },
          {
            path: 'reader/:stageId',
            component: () => import('../pages/ReaderPage.vue'),
            meta: { title: '阅读' },
          },
          {
            path: 'read/:stageId',
            redirect: (to) => ({ path: `/app/reader/${to.params.stageId}`, query: to.query }),
          },
          { path: 'discover', redirect: '/app' },
          { path: 'progress', redirect: '/app/journey' },
        ],
      },
      ...[
        'library',
        'sources',
        'vocabulary',
        'notes',
        'groups',
        'groups/:bookId',
        'profile',
        'billing',
        'journey',
        'passport',
        'calibration',
        'review',
        'upload',
        'path',
        'reader/:stageId',
        'read/:stageId',
      ].map((path) => ({
        path: `/${path}`,
        redirect: (to: { fullPath: string }) => `/app${to.fullPath}`,
      })),
      { path: '/:pathMatch(.*)*', redirect: '/' },
    ],
  })
  router.beforeEach(async (to) => {
    const auth = useAuthStore(pinia)
    await auth.initialize()
    if (to.meta.requiresAuth && !auth.authenticated)
      return { path: '/login', query: { redirect: to.fullPath } }
    if (auth.authenticated && ['/login', '/register'].includes(to.path)) return '/app'
    return true
  })
  router.afterEach((to) => {
    document.title = to.meta.title ? `${String(to.meta.title)} · ReadQuest` : 'ReadQuest'
  })
  return router
}
