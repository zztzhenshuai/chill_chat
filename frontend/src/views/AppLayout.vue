<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { Plus, User, Setting, SwitchButton, Lock, Moon, Sunny } from '@element-plus/icons-vue'
import { chatSocket } from '@/services/websocket'

const router = useRouter()
const currentRoute = router.currentRoute
const currentUserId = Number(localStorage.getItem('chill_user_id'))
const showProfileModal = ref(false)
const isDark = ref(localStorage.getItem('theme') === 'dark')

const toggleTheme = () => {
    isDark.value = !isDark.value
    if (isDark.value) {
        document.documentElement.classList.add('dark')
        localStorage.setItem('theme', 'dark')
    } else {
        document.documentElement.classList.remove('dark')
        localStorage.setItem('theme', 'light')
    }
}

const handleStorageChange = (e: StorageEvent) => {
    if (e.key === 'theme') {
        const newVal = e.newValue === 'dark'
        if (isDark.value !== newVal) {
            isDark.value = newVal
            if (newVal) {
                document.documentElement.classList.add('dark')
            } else {
                document.documentElement.classList.remove('dark')
            }
        }
    }
}

// Init theme
onMounted(() => {
    if (isDark.value) {
        document.documentElement.classList.add('dark')
    } else {
        document.documentElement.classList.remove('dark')
    }
    window.addEventListener('storage', handleStorageChange)
})

onUnmounted(() => {
    window.removeEventListener('storage', handleStorageChange)
})

const profileForm = ref({
  username: localStorage.getItem('chill_username') || '',
  avatar: localStorage.getItem('chill_avatar') || '',
  signature: localStorage.getItem('chill_signature') || '',
  gender: 0,
  birthday: '',
  location: '',
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const navigate = (path: string) => {
  router.push(path)
}

const openProfileModal = async () => {
    try {
        const res = await axios.get(`/api/user/${currentUserId}`)
        // Only copy non-password fields
        const { password, ...rest } = res.data
        profileForm.value = { 
            ...profileForm.value, 
            ...rest,
            oldPassword: '',
            newPassword: '',
            confirmPassword: ''
        }
        
        // Format date if needed
        if (profileForm.value.birthday) {
             profileForm.value.birthday = new Date(profileForm.value.birthday).toISOString().split('T')[0]
        }
        showProfileModal.value = true
    } catch(err) {
        ElMessage.error('无法加载个人资料')
    }
}

const handleAvatarUpload = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await axios.post('/api/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    profileForm.value.avatar = res.data.url
  } catch (err) { ElMessage.error('上传失败') }
}

const updateProfile = async () => {
  if (profileForm.value.newPassword) {
      if (profileForm.value.newPassword !== profileForm.value.confirmPassword) {
          ElMessage.error('两次输入的新密码不一致')
          return
      }
      if (!profileForm.value.oldPassword) {
          ElMessage.error('请输入旧密码')
          return
      }
  }

  try {
    const res = await axios.put('/api/user/profile', profileForm.value, { params: { userId: currentUserId }})
    localStorage.setItem('chill_username', res.data.username)
    localStorage.setItem('chill_avatar', res.data.avatar)
    if(res.data.signature) localStorage.setItem('chill_signature', res.data.signature)
    ElMessage.success('个人资料已更新')
    showProfileModal.value = false
    // Force reload to see changes
    location.reload()
  } catch(err: any) { 
      ElMessage.error(err.response?.data || '更新失败') 
  }
}

const logout = () => {
    localStorage.removeItem('chill_user_id')
    chatSocket.disconnect()
    router.push('/')
    ElMessage.success('已退出登录')
}
</script>

<template>
  <div class="flex h-screen bg-chill-bg dark:bg-gray-900 overflow-hidden transition-colors duration-300">
    <!-- Main Sidebar -->
    <div class="w-16 bg-white dark:bg-gray-800 border-r dark:border-gray-700 flex flex-col items-center py-4 space-y-6 z-20 transition-colors duration-300">
      <div class="w-10 h-10 bg-chill-blue rounded-lg flex items-center justify-center text-white font-bold cursor-pointer shadow-md hover:shadow-lg transition">
        CC
      </div>

      <div 
        @click="navigate('/app/chat')"
        class="p-2 rounded-lg cursor-pointer transition hover:bg-gray-100 dark:hover:bg-gray-700"
        :class="currentRoute.path.includes('chat') ? 'text-chill-blue bg-blue-50 dark:bg-gray-700' : 'text-gray-400 dark:text-gray-500'"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
      </div>

      <div 
        @click="navigate('/app/square')"
        class="p-2 rounded-lg cursor-pointer transition hover:bg-gray-100 dark:hover:bg-gray-700"
        :class="currentRoute.path.includes('square') ? 'text-chill-blue bg-blue-50 dark:bg-gray-700' : 'text-gray-400 dark:text-gray-500'"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="9" y1="21" x2="9" y2="9"></line></svg>
      </div>

      <div class="flex-1"></div>

      <!-- Theme Toggle -->
      <div 
        @click="toggleTheme"
        class="w-10 h-10 rounded-lg flex items-center justify-center cursor-pointer transition border"
        :class="isDark ? 'bg-gray-800 border-gray-700 text-yellow-500 hover:text-yellow-400 hover:bg-gray-700' : 'bg-white border-gray-200 text-indigo-500 hover:text-indigo-600 hover:bg-indigo-50'"
        :title="isDark ? '切换亮色模式' : '切换深色模式'"
      >
         <el-icon :size="20" v-if="isDark"><Sunny /></el-icon>
         <el-icon :size="20" v-else><Moon /></el-icon>
      </div>

      <!-- Profile -->
      <div 
        @click="openProfileModal"
        class="w-10 h-10 rounded-full bg-gray-100 overflow-hidden cursor-pointer border hover:border-chill-blue transition ring-2 ring-transparent hover:ring-blue-100"
        title="我的资料"
      >
         <img v-if="profileForm.avatar" :src="profileForm.avatar" class="w-full h-full object-cover"/>
         <div v-else class="flex items-center justify-center h-full text-gray-400"><el-icon><Setting/></el-icon></div>
      </div>

      <!-- Logout -->
      <div 
        @click="logout"
        class="w-10 h-10 rounded-lg flex items-center justify-center text-gray-600 cursor-pointer hover:bg-red-50 hover:text-red-600 transition border border-gray-200"
        title="退出登录"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
      </div>
    </div>

    <!-- Content Area -->
    <div class="flex-1 flex overflow-hidden">
      <router-view></router-view>
    </div>

    <!-- Profile Modal -->
    <div v-if="showProfileModal" class="fixed inset-0 bg-black bg-opacity-50 z-[999] flex items-center justify-center backdrop-blur-sm">
       <div class="bg-white p-6 rounded-xl w-[500px] shadow-2xl">
          <h3 class="text-xl font-bold mb-4">编辑个人资料</h3>
          
          <div class="flex justify-center mb-4">
             <div class="relative w-24 h-24 rounded-full bg-gray-100 overflow-hidden group cursor-pointer border-2 border-gray-100 hover:border-chill-blue">
                <img :src="profileForm.avatar" class="w-full h-full object-cover" />
                <div class="absolute inset-0 bg-black bg-opacity-30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition">
                   <span class="text-white text-xs">更换</span>
                </div>
                <input type="file" @change="handleAvatarUpload" class="absolute inset-0 opacity-0 cursor-pointer" accept="image/*"/>
             </div>
          </div>

          <div class="space-y-4">
             <!-- Readonly ID -->
             <div>
                <label class="block text-xs font-bold text-gray-500 mb-1">用户 ID</label>
                <div class="w-full bg-gray-100 text-gray-500 p-2 rounded text-sm font-mono border border-gray-200">
                    {{ currentUserId }}
                </div>
             </div>

             <div>
                <label class="block text-xs font-bold text-gray-500 mb-1">用户名</label>
                <input v-model="profileForm.username" class="w-full border p-2 rounded outline-none focus:border-chill-blue text-sm" placeholder="请输入用户名" />
             </div>
             
             <div>
                <label class="block text-xs font-bold text-gray-500 mb-1">个性签名</label>
                <textarea 
                    v-model="profileForm.signature" 
                    maxlength="50"
                    rows="2"
                    class="w-full border p-2 rounded outline-none focus:border-chill-blue text-sm resize-none" 
                    placeholder="写一句话介绍自己... (限50字)" 
                ></textarea>
                <div class="text-right text-xs text-gray-400 mt-1">
                    {{ (profileForm.signature || '').length }}/50
                </div>
             </div>
             
             <div class="flex space-x-3">
                 <div class="w-1/3">
                    <label class="block text-xs font-bold text-gray-500 mb-1">性别</label>
                    <select v-model="profileForm.gender" class="w-full border p-2 rounded outline-none text-sm bg-white">
                        <option :value="0">保密</option>
                        <option :value="1">男</option>
                        <option :value="2">女</option>
                    </select>
                 </div>
                 <div class="flex-1">
                    <label class="block text-xs font-bold text-gray-500 mb-1">生日</label>
                    <input v-model="profileForm.birthday" type="date" class="w-full border p-2 rounded outline-none text-sm text-gray-600" />
                 </div>
             </div>
             
             <div>
                <label class="block text-xs font-bold text-gray-500 mb-1">所在地</label>
                <input v-model="profileForm.location" class="w-full border p-2 rounded outline-none focus:border-chill-blue text-sm" placeholder="例如: 广东, 深圳" />
             </div>
             
             <div class="border-t pt-4 mt-2 bg-gray-50 -mx-6 px-6 pb-2">
                <div class="text-xs font-bold text-indigo-500 mb-3 flex items-center">
                    <el-icon class="mr-1"><Lock /></el-icon> 修改密码 (可选)
                </div>
                <div class="space-y-3">
                    <div>
                        <label class="block text-xs text-gray-400 mb-1">当前旧密码</label>
                        <input v-model="profileForm.oldPassword" class="w-full border p-2 rounded outline-none focus:border-indigo-400 text-sm" type="password" />
                    </div>
                    <div class="space-y-3">
                        <div>
                            <label class="block text-xs text-gray-400 mb-1">新密码</label>
                            <input v-model="profileForm.newPassword" class="w-full border p-2 rounded outline-none focus:border-indigo-400 text-sm" type="password" />
                        </div>
                        <div>
                            <label class="block text-xs text-gray-400 mb-1">确认新密码</label>
                            <input v-model="profileForm.confirmPassword" class="w-full border p-2 rounded outline-none focus:border-indigo-400 text-sm" type="password" />
                        </div>
                    </div>
                </div>
             </div>
          </div>

          <div class="flex justify-end space-x-2 mt-6">
             <button @click="showProfileModal = false" class="text-gray-500 px-4 py-2 hover:bg-gray-100 rounded">取消</button>
             <button @click="updateProfile" class="bg-chill-blue text-white px-6 py-2 rounded">保存</button>
          </div>
       </div>
    </div>
  </div>
</template>
