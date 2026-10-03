<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-end"
    :width="430"
    trigger="click"
    popper-class="ntf-pop"
    @show="handleShow"
  >
    <template #reference>
      <div class="ntf-bell" :class="{ 'is-empty': !person }">
        <el-badge :hidden="!unread" :value="unread" :max="99" type="danger">
          <span class="ntf-icon">&#128276;</span>
        </el-badge>
      </div>
    </template>

    <div class="ntf-panel">
      <div class="ntf-head">
        <span>操作失败通知</span>
        <span class="ntf-head-sub" v-if="person">
          {{ person }} · 未读 {{ unread }}
        </span>
        <span class="ntf-head-sub" v-else>请先在右侧选择值班员身份</span>
      </div>

      <div v-if="!person" class="ntf-none">
        选择身份后才能查收发给调度员/监护人的失败通知
      </div>
      <div v-else-if="!items.length" class="ntf-none">暂无操作失败通知</div>
      <div v-else class="ntf-list">
        <div
          v-for="n in items"
          :key="n.id"
          class="ntf-item"
          :class="{ unread: !n.read, resolved: n.resolved }"
          @click="openTicket(n)"
        >
          <div class="ntf-item-top">
            <span class="ntf-tag" :class="n.resolved ? 'tag-ok' : 'tag-fail'">
              {{ n.resolved ? '已解除' : '待处理' }}
            </span>
            <span class="ntf-role" :class="{ 'role-unread': !n.read }">
              {{ n.recipientRoleName }} {{ n.read ? '已确认' : '待确认' }}
            </span>
            <span class="ntf-time">{{ fmt(n.failTime) }}</span>
          </div>
          <div class="ntf-title">
            <b>{{ n.ticketNo }}</b>
            第 {{ n.stepNo }} 步操作失败
          </div>
          <div class="ntf-desc">{{ n.actionDesc }}</div>
          <div class="ntf-reason">原因：{{ n.failReason }}</div>
          <div class="ntf-meta">
            <span>{{ n.failPerson }} 登记 · {{ fmt(n.failTime) }}</span>
          </div>
          <!-- 已读与解除留痕：解除后双方已读时间保留、不可删除 -->
          <div class="ntf-trace">
            <span>本人{{ n.read ? `已于 ${fmt(n.readTime)} 确认` : '尚未确认' }}</span>
            <span v-if="n.resolved" class="ntf-resolved-at">
              原步已于 {{ fmt(n.resolveTime) }} 重试成功，通知解除（留痕不可删除）
            </span>
          </div>
          <div class="ntf-actions" @click.stop>
            <el-button
              v-if="!n.read"
              size="small"
              type="primary"
              plain
              @click="ack(n)"
            >确认已读</el-button>
            <el-button size="small" @click="openTicket(n)">
              {{ n.resolved ? '查看票面' : '进入失败步骤' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { failNotifications, failNotificationUnread, readFailNotification } from '../api'
import { currentPerson } from '../session'
import { go } from '../router'

const visible = ref(false)
const items = ref([])
const unread = ref(0)
const person = currentPerson

async function refreshUnread() {
  if (!person.value) {
    unread.value = 0
    return
  }
  try {
    const res = await failNotificationUnread(person.value)
    unread.value = res.data.count || 0
  } catch (e) {
    /* 角标静默失败，不打扰操作 */
  }
}

async function refreshList() {
  if (!person.value) {
    items.value = []
    return
  }
  try {
    const res = await failNotifications(person.value)
    items.value = res.data || []
  } catch (e) {
    ElMessage.error(`通知加载失败：${e.message}`)
  }
}

async function handleShow() {
  visible.value = true
  await refreshList()
  await refreshUnread()
}

/** 各自确认已读：只更新本人那一行，另一方状态不变 */
async function ack(n) {
  if (!person.value) {
    ElMessage.warning('请先选择当前值班员身份')
    return
  }
  try {
    await readFailNotification(n.id, person.value)
    ElMessage.success(`已以「${person.value}」身份确认该通知`)
    await Promise.all([refreshList(), refreshUnread()])
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 从通知进入操作票：定位到失败步骤（query 由操作页消费） */
function openTicket(n) {
  visible.value = false
  go('/operations', { ticketId: n.ticketId, stepNo: n.stepNo })
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : ''
}

watch(person, () => {
  refreshUnread()
  if (visible.value) refreshList()
}, { immediate: true })

// 多终端协作：另一台设备的状态变化（重试解除、另一方确认不影响自己）8 秒内对齐
const timer = setInterval(() => {
  refreshUnread()
  if (visible.value) refreshList()
}, 8000)
onBeforeUnmount(() => clearInterval(timer))

defineExpose({ refreshUnread })
</script>

<style scoped>
.ntf-bell {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
}

.ntf-bell:hover {
  background: rgba(255, 255, 255, 0.28);
}

.ntf-bell.is-empty {
  opacity: 0.65;
}

.ntf-panel {
  margin: -12px;
}

.ntf-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 800;
  color: var(--ops-orange-dark);
  border-bottom: 1px solid var(--ops-line);
}

.ntf-head-sub {
  font-size: 11.5px;
  font-weight: 400;
  color: var(--ops-grey);
}

.ntf-none {
  padding: 28px 14px;
  text-align: center;
  font-size: 12.5px;
  color: var(--ops-grey);
}

.ntf-list {
  max-height: 420px;
  overflow-y: auto;
}

.ntf-item {
  padding: 10px 14px;
  border-bottom: 1px dashed var(--ops-line);
  cursor: pointer;
}

.ntf-item:hover {
  background: var(--ops-orange-soft);
}

.ntf-item.unread {
  background: #fdecea;
}

.ntf-item.unread:hover {
  background: #fbe0dd;
}

.ntf-item.resolved {
  background: #f1f8e9;
}

.ntf-item.resolved:hover {
  background: #e8f3df;
}

.ntf-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ntf-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 999px;
}

.tag-fail {
  background: #c62828;
  color: #fff;
}

.tag-ok {
  background: #2e7d32;
  color: #fff;
}

.ntf-role {
  font-size: 11.5px;
  color: var(--ops-grey);
}

.ntf-role.role-unread {
  color: #c62828;
  font-weight: 700;
}

.ntf-time {
  margin-left: auto;
  font-size: 11px;
  color: var(--ops-grey);
  font-variant-numeric: tabular-nums;
}

.ntf-title {
  font-size: 13px;
  color: var(--ops-ink);
}

.ntf-desc {
  font-size: 12px;
  color: var(--ops-ink-2);
  margin-top: 2px;
}

.ntf-reason {
  font-size: 12px;
  color: #c62828;
  margin-top: 3px;
  font-weight: 700;
}

.ntf-item.resolved .ntf-reason {
  color: var(--ops-grey);
  font-weight: 400;
}

.ntf-meta {
  font-size: 11px;
  color: var(--ops-grey);
  margin-top: 3px;
}

.ntf-trace {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: var(--ops-grey);
  margin-top: 4px;
}

.ntf-resolved-at {
  color: #2e7d32;
}

.ntf-actions {
  margin-top: 6px;
  display: flex;
  gap: 8px;
}
</style>
