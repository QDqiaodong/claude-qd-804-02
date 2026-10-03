import { ref } from 'vue'

/**
 * 站内通知需要知道「当前值班员是谁」才能查收自己那一份。
 * 演示环境没有登录体系，用顶栏身份选择器代替：选择结果存 localStorage，
 * 刷新或换设备（同一浏览器）登录后仍是同一身份；服务端按姓名过滤/鉴权，
 * 已读、未读、解除状态全部以数据库为准，不依赖本地缓存。
 */
const STORAGE_KEY = 'substation.currentPerson'

/** 种子值班员：覆盖种子数据里的调度员与监护人，可直接切换；也可手输其他姓名 */
export const PERSON_PRESETS = [
  '黄志强',
  '孙振华',
  '刘玉兰',
  '周涛',
  '吴斌'
]

function restore() {
  try {
    return localStorage.getItem(STORAGE_KEY) || ''
  } catch (e) {
    return ''
  }
}

/** 当前值班员姓名（全局单例，顶栏选择、通知中心、操作页共用） */
export const currentPerson = ref(restore())

export function setCurrentPerson(name) {
  const next = (name || '').trim()
  currentPerson.value = next
  try {
    if (next) {
      localStorage.setItem(STORAGE_KEY, next)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch (e) {
    /* 隐私模式等场景忽略持久化失败，本次会话仍生效 */
  }
}
