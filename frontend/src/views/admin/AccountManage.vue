<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>添加账号
        </el-button>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 教职工 -->
        <el-tab-pane label="教职工" name="staff">
          <div class="excel-bar">
            <el-button @click="handleStaffExport">导出教职工</el-button>
            <el-button @click="handleStaffTemplate">下载模板</el-button>
            <el-button type="success" :loading="importing" @click="staffFileRef?.click()">批量导入</el-button>
            <input ref="staffFileRef" type="file" accept=".xlsx,.xls" hidden @change="handleStaffImport" />
          </div>
          <el-table :data="staffs">
            <el-table-column prop="staffNo" label="工号" width="100" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="row.roleType === 'ADMIN' ? 'danger' : 'warning'" size="small">
                  {{ row.roleType === 'ADMIN' ? '教学秘书' : '教师' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="department" label="院系" min-width="120" />
            <el-table-column prop="phone" label="手机号" width="130" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ENABLED' ? '正常' : '冻结' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit('STAFF', row)">编辑</el-button>
                <el-button link type="primary" @click="handleReset('STAFF', row)">重置密码</el-button>
                <el-button
                  v-if="row.roleType !== 'ADMIN'"
                  link
                  :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                  @click="handleToggle('STAFF', row)"
                >
                  {{ row.status === 'ENABLED' ? '冻结' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 学生 -->
        <el-tab-pane label="学生" name="student">
          <div class="search-bar">
            <el-input
              v-model="studentKeyword"
              placeholder="按姓名/学号/专业/班级搜索"
              clearable
              style="width: 280px"
              @keyup.enter="loadStudents"
            />
            <el-button type="primary" @click="loadStudents">查询</el-button>
            <div class="spacer" />
            <el-button @click="handleStudentExport">导出学生</el-button>
            <el-button @click="handleStudentTemplate">下载模板</el-button>
            <el-button type="success" :loading="importing" @click="studentFileRef?.click()">批量导入</el-button>
            <input ref="studentFileRef" type="file" accept=".xlsx,.xls" hidden @change="handleStudentImport" />
          </div>
          <el-table :data="students">
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="realName" label="姓名" width="90" />
            <el-table-column prop="gender" label="性别" width="60" />
            <el-table-column prop="major" label="专业" min-width="100" />
            <el-table-column prop="className" label="班级" min-width="100" />
            <el-table-column prop="enrollmentYear" label="入学年份" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit('STUDENT', row)">编辑</el-button>
                <el-button link type="primary" @click="handleReset('STUDENT', row)">重置密码</el-button>
                <el-button
                  v-if="row.status === 'ENABLED'"
                  link
                  type="danger"
                  @click="handleToggle('STUDENT', row, 'FROZEN')"
                >
                  冻结
                </el-button>
                <el-button v-else link type="success" @click="handleToggle('STUDENT', row, 'ENABLED')">
                  启用
                </el-button>
                <el-button
                  v-if="row.status === 'SUSPENDED'"
                  link
                  type="warning"
                  @click="handleToggle('STUDENT', row, 'ENABLED')"
                >
                  复学
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 添加 / 编辑账号弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑账号' : '添加账号'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item v-if="!editId" label="角色" prop="roleType">
          <el-radio-group v-model="form.roleType">
            <el-radio value="TEACHER">教师</el-radio>
            <el-radio value="STUDENT">学生</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="院系">
          <el-input v-model="form.department" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <template v-if="form.roleType === 'STUDENT'">
          <el-form-item label="性别">
            <el-radio-group v-model="form.gender">
              <el-radio value="男">男</el-radio>
              <el-radio value="女">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="专业">
            <el-input v-model="form.major" />
          </el-form-item>
          <el-form-item label="班级">
            <el-input v-model="form.className" />
          </el-form-item>
          <el-form-item label="入学年份">
            <el-input-number v-model="form.enrollmentYear" :min="2000" :max="2030" />
          </el-form-item>
        </template>
      </el-form>
      <div v-if="!editId" class="dialog-tip">账号生成后初始密码统一为 123456</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ editId ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 账号管理（教学秘书）
 * 职责：分"教职工 / 学生"两个页签管理账号——查询、添加 / 编辑（学号工号与角色不可改）、
 *       重置密码、冻结 / 启用 / 复学状态切换；
 *       支持 Excel 批量导入（导入成功弹窗展示每人随机初始密码）、导出列表、下载导入模板。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAccount,
  downloadStaffTemplate,
  downloadStudentTemplate,
  exportStaffs,
  exportStudents,
  importStaffs,
  importStudents,
  listStaffs,
  listStudents,
  resetPassword,
  toggleStatus,
  updateAccount
} from '@/api/account'
import { datedFilename, saveBlob } from '@/api/http'
import type { AccountForm, AccountImportResult } from '@/api/account'
import type { Staff, Student } from '@/types'

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const activeTab = ref('staff')
const staffs = ref<Staff[]>([])
const students = ref<Student[]>([])
const studentKeyword = ref('')
const dialogVisible = ref(false)
const editId = ref<{ userType: 'STAFF' | 'STUDENT'; id: number } | null>(null)
const formRef = ref<FormInstance>()
const staffFileRef = ref<HTMLInputElement>()
const studentFileRef = ref<HTMLInputElement>()

const form = reactive<AccountForm>({
  realName: '',
  roleType: 'STUDENT',
  gender: '男',
  department: '',
  major: '',
  className: '',
  enrollmentYear: new Date().getFullYear(),
  phone: ''
})

const rules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleType: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

// 学生账号状态 → 中文文案（正常 / 冻结 / 休学）
function statusText(status: string) {
  const map: Record<string, string> = { ENABLED: '正常', FROZEN: '冻结', SUSPENDED: '休学' }
  return map[status] || status
}

// 学生账号状态 → el-tag 颜色类型（与 statusText 对应）
function statusTagType(status: string) {
  const map: Record<string, 'success' | 'danger' | 'warning'> = {
    ENABLED: 'success',
    FROZEN: 'danger',
    SUSPENDED: 'warning'
  }
  return map[status] || 'info'
}

async function loadStaffs() {
  staffs.value = await listStaffs()
}

async function loadStudents() {
  students.value = await listStudents(studentKeyword.value || undefined)
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadStaffs(), loadStudents()])
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.realName = ''
  form.roleType = 'STUDENT'
  form.gender = '男'
  form.department = ''
  form.major = ''
  form.className = ''
  form.enrollmentYear = new Date().getFullYear()
  form.phone = ''
  dialogVisible.value = true
}

/** 打开编辑弹窗（教职工 / 学生），学号工号与角色不可修改 */
function openEdit(userType: 'STAFF' | 'STUDENT', row: Staff | Student) {
  editId.value = { userType, id: row.id }
  form.realName = row.realName
  form.roleType = userType === 'STAFF' ? 'TEACHER' : 'STUDENT'
  form.gender = ('gender' in row && row.gender) || '男'
  form.department = row.department ?? ''
  form.major = ('major' in row && row.major) || ''
  form.className = ('className' in row && row.className) || ''
  form.enrollmentYear = ('enrollmentYear' in row && row.enrollmentYear) || new Date().getFullYear()
  form.phone = row.phone ?? ''
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editId.value) {
      await updateAccount(editId.value.userType, editId.value.id, {
        realName: form.realName,
        gender: form.gender,
        department: form.department,
        phone: form.phone,
        major: form.major,
        className: form.className,
        enrollmentYear: form.enrollmentYear
      })
      ElMessage.success('账号信息已更新')
    } else {
      await createAccount(form)
      ElMessage.success('账号创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function handleReset(userType: 'STAFF' | 'STUDENT', row: Staff | Student) {
  ElMessageBox.confirm(`确定将「${row.realName}」的密码重置为 123456 吗？`, '重置密码', {
    type: 'warning'
  }).then(async () => {
    await resetPassword(userType, row.id)
    ElMessage.success('密码已重置为 123456')
  })
}

function handleToggle(userType: 'STAFF' | 'STUDENT', row: Staff | Student, status?: string) {
  const target = status ?? (row.status === 'ENABLED' ? 'FROZEN' : 'ENABLED')
  const action = target === 'ENABLED' ? '启用' : target === 'FROZEN' ? '冻结' : '复学'
  ElMessageBox.confirm(`确定${action}账号「${row.realName}」吗？`, '状态变更', { type: 'warning' }).then(
    async () => {
      await toggleStatus(userType, row.id, target)
      ElMessage.success(`${action}成功`)
      load()
    }
  )
}

async function handleStaffExport() {
  const blob = await exportStaffs()
  saveBlob(blob, datedFilename('教职工列表'))
}

async function handleStaffTemplate() {
  const blob = await downloadStaffTemplate()
  saveBlob(blob, '教职工导入模板.xlsx')
}

async function handleStaffImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const list = await importStaffs(file)
    showImportResult(list, '教职工')
    load()
  } finally {
    importing.value = false
    input.value = ''
  }
}

async function handleStudentExport() {
  const blob = await exportStudents()
  saveBlob(blob, datedFilename('学生列表'))
}

async function handleStudentTemplate() {
  const blob = await downloadStudentTemplate()
  saveBlob(blob, '学生导入模板.xlsx')
}

async function handleStudentImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const list = await importStudents(file)
    showImportResult(list, '学生')
    load()
  } finally {
    importing.value = false
    input.value = ''
  }
}

/** 导入成功后弹窗展示每人随机初始密码，供线下分发给对应人员 */
function showImportResult(list: AccountImportResult[], label: string) {
  // 姓名/学号来自用户上传的 Excel，拼接进 HTML 前必须转义，防止自 XSS
  const escapeHtml = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;')
  const lines = list
    .map((x) => `· ${escapeHtml(x.realName)}（${escapeHtml(x.userNo)}）：初始密码 ${escapeHtml(x.initPassword)}`)
    .join('<br/>')
  ElMessageBox.alert(
    `<div style="max-height:320px;overflow:auto;font-size:13px;line-height:1.9">共导入 ${list.length} 名${label}，每人初始密码均不同（仅此一次展示，请妥善保管并线下分发）：<br/><br/>${lines}</div>`,
    `${label}导入成功`,
    { dangerouslyUseHTMLString: true, confirmButtonText: '我已保存' }
  )
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 14px;
}
.excel-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.spacer {
  flex: 1;
}
.dialog-tip {
  font-size: 12px;
  color: #909399;
  padding: 0 0 6px 90px;
}
</style>
