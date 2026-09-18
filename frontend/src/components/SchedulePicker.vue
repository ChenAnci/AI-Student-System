<!-- ===== 排课选择器组件（SchedulePicker） =====
  职责：以 7 天 × 12 节的矩阵形式选择排课时间段，
  通过 v-model（modelValue）与父组件双向绑定排课文本，
  如 "周一 1-2节;周三 3-4节"。支持编辑回显与清空。 -->
<template>
  <div class="schedule-picker">
    <div class="sp-grid">
      <!-- 表头：首列为固定"节次"，其后为周一至周日 -->
      <div class="sp-cell sp-head sp-corner">节次</div>
      <div v-for="d in days" :key="d" class="sp-cell sp-head">{{ d }}</div>
      <!-- 行：每行首列为节次编号，其后 7 个格子可点击选择 -->
      <template v-for="slot in slotCount" :key="slot">
        <div class="sp-cell sp-slot">{{ slot }}</div>
        <div
          v-for="d in days"
          :key="d"
          class="sp-cell sp-selectable"
          :class="{ 'sp-active': isSelected(d, slot) }"
          @click="toggle(d, slot)"
        >
          &nbsp;
        </div>
      </template>
    </div>
    <div class="sp-selected">
      已选时段：<span :class="scheduleText ? '' : 'sp-empty'">{{ scheduleText || '未选择' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

// 组件对外接收排课文本（v-model），并向上触发 update:modelValue 事件同步值
const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

// 表头固定的周一至周日；每天最多 12 节课
const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const
const slotCount = 12

// 内部选中状态：以 "周X-节次"（如 "周一-2"）为 key 的集合
const selected = ref<Set<string>>(new Set())

/** 解析现有 schedule 文本（如 "周一 1-2节;周三 3-4节"）回填矩阵 */
function parseSchedule(text?: string) {
  const set = new Set<string>()
  if (!text) return set
  // 匹配形如 "周一 1-2节" 的片段，逐一展开成 "周X-节次" 键
  const re = /周[一二三四五六日天]\s*(\d+)\s*[-~—至]\s*(\d+)节/g
  let m: RegExpExecArray | null
  while ((m = re.exec(text))) {
    const day = m[0].match(/周[一二三四五六日天]/)![0]
    const start = Number(m[1])
    const end = Number(m[2])
    // 将连续节次段展开为单个节次逐个加入集合
    for (let i = start; i <= end && i <= slotCount; i++) {
      set.add(`${day}-${i}`)
    }
  }
  return set
}

// 判断某天某节是否处于选中状态
function isSelected(day: string, slot: number) {
  return selected.value.has(`${day}-${slot}`)
}

// 点击切换某天某节的选中状态，并同步输出排课文本
function toggle(day: string, slot: number) {
  const key = `${day}-${slot}`
  const next = new Set(selected.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  selected.value = next
  emitText(next)
}

// 将选中集合合并连续节次，生成排课文本（如 "周一 1-2节;周三 3-4节"）
function computeText(set: Set<string>): string {
  const parts: string[] = []
  for (const day of days) {
    const slots: number[] = []
    // 收集当天被选中的节次编号
    for (let i = 1; i <= slotCount; i++) {
      if (set.has(`${day}-${i}`)) slots.push(i)
    }
    if (slots.length === 0) continue
    // 把连续编号合并为一段 "起始-结束节"
    let segStart = slots[0]
    let segEnd = slots[0]
    for (let i = 1; i <= slots.length; i++) {
      if (i < slots.length && slots[i] === slots[i - 1] + 1) {
        segEnd = slots[i]
      } else {
        parts.push(`${day} ${segStart}-${segEnd}节`)
        segStart = slots[i]
        segEnd = slots[i]
      }
    }
  }
  return parts.join(';')
}

// 将计算出的排课文本通过 update:modelValue 事件回传给父组件
function emitText(set: Set<string>) {
  emit('update:modelValue', computeText(set))
}

// 已选排课文本（用于下方展示，与矩阵状态保持一致）
const scheduleText = computed(() => computeText(selected.value))

// 外部传入的 schedule 变化时同步矩阵（编辑回显 / 清空）；与组件自身生成的文本一致时不重建，避免循环
watch(
  () => props.modelValue,
  (val) => {
    if (computeText(selected.value) !== (val ?? '')) {
      selected.value = parseSchedule(val)
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.schedule-picker {
  width: 100%;
}
.sp-grid {
  display: grid;
  grid-template-columns: 48px repeat(7, 1fr);
  gap: 2px;
  max-width: 520px;
}
.sp-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 2px;
  user-select: none;
}
.sp-head {
  color: #303133;
  font-weight: 600;
  background: #eef1f6;
}
.sp-corner {
  color: #909399;
  font-weight: 400;
}
.sp-slot {
  color: #909399;
}
.sp-selectable {
  cursor: pointer;
  background: #fff;
  border: 1px solid #ebeef5;
}
.sp-selectable:hover {
  border-color: #409eff;
}
.sp-active {
  background: #409eff !important;
  border-color: #409eff !important;
}
.sp-selected {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
.sp-empty {
  color: #c0c4cc;
}
</style>
