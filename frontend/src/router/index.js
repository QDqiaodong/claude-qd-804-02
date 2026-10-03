import { ref } from 'vue'
import { createWebHistory } from 'vue-router'
import BayMapView from '../views/BayMapView.vue'
import WorkTicketView from '../views/WorkTicketView.vue'
import OperationView from '../views/OperationView.vue'
import DefectView from '../views/DefectView.vue'

/**
 * 本仓不走 vue-router 插件式安装（main.js 不在可改动清单里），
 * 这里用 vue-router 的 history 对象自己驱动一层轻量路由：
 * 地址栏路径 ↔ 当前页面组件，下方 NavView 组件即可直接渲染。
 *
 * currentQuery 承载站内通知跳转参数，例如
 * /operations?ticketId=4&stepNo=4 —— 从通知进入操作票并定位到失败步骤；
 * 刷新页面后参数仍在，定位与通知/步骤/票面进度以服务端数据重新对齐。
 */
const history = createWebHistory()

export const routes = [
  { path: '/bays', name: 'bays', title: '间隔平面示意图', component: BayMapView },
  { path: '/work-tickets', name: 'work-tickets', title: '两票票面 · 三审签字', component: WorkTicketView },
  { path: '/operations', name: 'operations', title: '倒闸操作 · 步骤勾选', component: OperationView },
  { path: '/defects', name: 'defects', title: '缺陷超期排行 · 消缺闭环', component: DefectView }
]

const DEFAULT_PATH = '/bays'

export const currentPath = ref(normalize(window.location.pathname))
export const currentQuery = ref(parseQuery(window.location.search))

function normalize(path) {
  const clean = (path || '').replace(/\/+$/, '')
  return routes.some((r) => r.path === clean) ? clean : DEFAULT_PATH
}

function parseQuery(search) {
  const out = {}
  const params = new URLSearchParams(search || '')
  params.forEach((v, k) => {
    out[k] = v
  })
  return out
}

if (normalize(window.location.pathname) !== window.location.pathname) {
  history.replace(DEFAULT_PATH)
}

history.listen((to) => {
  const idx = to.indexOf('?')
  currentPath.value = normalize(idx >= 0 ? to.slice(0, idx) : to)
  currentQuery.value = parseQuery(idx >= 0 ? to.slice(idx + 1) : '')
})

export function go(path, query) {
  let target = path
  if (query && Object.keys(query).length) {
    const qs = new URLSearchParams(query).toString()
    target = `${path}?${qs}`
  }
  const samePage = path === currentPath.value
  if (samePage && target === window.location.pathname + window.location.search) return
  history.push(target)
  currentPath.value = path
  currentQuery.value = query || {}
}

/** 清掉当前地址上的 query 参数（通知定位参数消费后清理，避免刷新再次强制跳票） */
export function clearQuery() {
  if (!Object.keys(currentQuery.value).length) return
  history.replace(currentPath.value)
  currentQuery.value = {}
}

export function currentView() {
  return routes.find((r) => r.path === currentPath.value) || routes[0]
}

export default { routes, go, currentPath, currentQuery, currentView, clearQuery }
