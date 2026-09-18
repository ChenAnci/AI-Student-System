<!-- ===== 发送通知对话框组件（SendNotificationDialog） =====
  职责：管理端/教师端发送通知的弹窗，支持按课程（老师/管理员）、
  按班级/专业/院系/全体学生（仅管理员）选择接收对象，提交后通知后端群发。 -->
<template>
  <!-- 弹窗显隐由 modelValue 控制，关闭后重置表单；dialog 根节点即表单内容 -->
  <el-dialog :model-value="modelValue" title="发送通知" width="600px" @update:model-value="$emit('update:modelValue', $event)" @closed="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <!-- 标题：必填，最长 100 字 -->
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：调课通知" />
      </el-form-item>
      <!-- 内容：必填，最多 2000 字 -->
      <el-form-item label="内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit />
      </el-form-item>
      <!-- 接收对象类型：课程对所有人生效；班级/专业/院系/全体仅管理员可见 -->
      <el-form-item label="接收对象" prop="kind">
        <el-radio-group v-model="form.kind">
          <el-radio-button label="COURSE">按课程</el-radio-button>
          <el-radio-button v-if="isAdmin" label="CLASS">按班级</el-radio-button>
          <el-radio-button v-if="isAdmin" label="MAJOR">按专业</el-radio-button>
          <el-radio-button v-if="isAdmin" label="DEPARTMENT">按院系</el-radio-button>
          <el-radio-button v-if="isAdmin" label="ALL">全部学生</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <!-- 按课程时：下拉选择课程（老师=自己的课，管理员=全部课程） -->
      <el-form-item v-if="form.kind === 'COURSE'" label="选择课程" prop="courseId">
        <el-select v-model="form.courseId" filterable placeholder="请选择课程" style="width: 100%">
          <el-option v-for="c in courses" :key="c.id" :label="`${c.courseCode} ${c.courseName}`" :value="c.id" />
        </el-select>
      </el-form-item>
      <!-- 按班级/专业/院系时：输入对应名称（仅管理员出现） -->
      <el-form-item v-else-if="form.kind === 'CLASS'" label="班级" prop="className">
        <el-input v-model="form.className" placeholder="如：软件2101" />
      </el-form-item>
      <el-form-item v-else-if="form.kind === 'MAJOR'" label="专业" prop="major">
        <el-input v-model="form.major" placeholder="如：软件工程" />
      </el-form-item>
      <el-form-item v-else-if="form.kind === 'DEPARTMENT'" label="院系" prop="department">
        <el-input v-model="form.department" placeholder="如：计算机学院" />
      </el-form-item>
    </el-form>
    <!-- 底部按钮：取消关闭弹窗，发送提交 -->
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">发送</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sendNotification, type SendNotificationBody } from '@/api/notification'
import { listMyCourses } from '@/api/course'
import { useUserStore } from '@/stores/user'
import type { Course } from '@/types'

// 弹窗显隐由父组件 modelValue 控制，发送成功后触发 sent 事件通知父组件刷新
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'sent'): void }>()

const userStore = useUserStore()
// 仅管理员可按班级/专业/院系/全体发送；老师角色只能按自己的课程发送（后端同样鉴权，前端仅做展示控制）
const isAdmin = computed(() => userStore.role() === 'ADMIN')

// 表单实例引用（用于校验与清空校验状态）；提交中的 loading 标志；可选课程列表
const formRef = ref<FormInstance>()
const loading = ref(false)
const courses = ref<Course[]>([])

// 发送表单：kind 为接收对象类型，默认"按课程"（对老师/管理员都适用，管理员可再切换其它维度）
const form = reactive<{
  title: string
  content: string
  kind: string
  courseId?: number
  className: string
  major: string
  department: string
}>({
  title: '',
  content: '',
  kind: 'COURSE',
  courseId: undefined,
  className: '',
  major: '',
  department: ''
})

// 表单校验规则：标题与内容必填
const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

// 提交发送：先表单校验，再按所选 kind 组装目标参数——只携带当前 kind 对应的字段，其余置 undefined，避免误传脏数据
async function submit() {
  await formRef.value?.validate()
  const body: SendNotificationBody = {
    title: form.title,
    content: form.content,
    target: {
      kind: form.kind as SendNotificationBody['target']['kind'],
      courseId: form.kind === 'COURSE' ? form.courseId : undefined,
      className: form.kind === 'CLASS' ? form.className : undefined,
      major: form.kind === 'MAJOR' ? form.major : undefined,
      department: form.kind === 'DEPARTMENT' ? form.department : undefined
    }
  }
  loading.value = true
  try {
    await sendNotification(body)
    ElMessage.success('发送成功')
    emit('update:modelValue', false)
    emit('sent')
  } catch {
    // 已由拦截器提示
  } finally {
    loading.value = false
  }
}

// 关闭弹窗后重置表单与校验状态，避免下次打开残留上次填写的内容
function reset() {
  Object.assign(form, {
    title: '',
    content: '',
    kind: 'COURSE',
    courseId: undefined,
    className: '',
    major: '',
    department: ''
  })
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open && courses.value.length === 0) {
      // 打开时加载课程列表（老师=自己的课程；管理员=全部）
      listMyCourses()
        .then((cs) => {
          courses.value = cs
        })
        .catch(() => {})
    }
  }
)
</script>
