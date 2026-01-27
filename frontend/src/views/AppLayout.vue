<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { Plus, User, Setting } from '@element-plus/icons-vue'

const router = useRouter()
const currentRoute = router.currentRoute
const currentUserId = Number(localStorage.getItem('chill_user_id'))
const showProfileModal = ref(false)
const profileForm = ref({
  username: localStorage.getItem('chill_username') || '',
  avatar: localStorage.getItem('chill_avatar') || '',
  signature: localStorage.getItem('chill_signature') || '',
  password: ''
})

const navigate = (path: string) => {
  router.push(path)
}

const handleAvatarUpload = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await axios.post('/api/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    profileForm.value.avatar = res.data.url
  } catch (err) { ElMessage.error('Upload failed') }
}

const updateProfile = async () => {
  try {
    const res = await axios.put('/api/user/profile', profileForm.value, { params: { userId: currentUserId }})
    localStorage.setItem('chill_username', res.data.username)
    localStorage.setItem('chill_avatar', res.data.avatar)
    if(res.data.signature) localStorage.setItem('chill_signature', res.data.signature)
    ElMessage.success('Profile updated')
    showProfileModal.value = false
    // Force reload to see changes
    location.reload()
  } catch(err) { ElMessage.error('Failed') }
}
</script>

<template>
  <div class="flex h-screen bg-chill-bg overflow-hidden">
    <!-- Main Sidebar -->
    <div class="w-16 bg-white border-r flex flex-col items-center py-4 space-y-6">
      <div class="w-10 h-10 bg-chill-blue rounded-lg flex items-center justify-center text-white font-bold cursor-pointer">
        CC
      </div>

      <div 
        @click="navigate('/app/chat')"
        class="p-2 rounded-lg cursor-pointer transition hover:bg-gray-100"
        :class="currentRoute.path.includes('chat') ? 'text-chill-blue bg-blue-50' : 'text-gray-400'"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
      </div>

      <div 
        @click="navigate('/app/square')"
        class="p-2 rounded-lg cursor-pointer transition hover:bg-gray-100"
        :class="currentRoute.path.includes('square') ? 'text-chill-blue bg-blue-50' : 'text-gray-400'"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="9" y1="21" x2="9" y2="9"></line></svg>
      </div>
      
      <div class="flex-1"></div>

      <!-- Settings -->
      <div 
        @click="showProfileModal = true"
        class="w-10 h-10 rounded-full bg-gray-100 overflow-hidden cursor-pointer border hover:border-chill-blue"
      >
         <img v-if="profileForm.avatar" :src="profileForm.avatar" class="w-full h-full object-cover"/>
         <div v-else class="flex items-center justify-center h-full text-gray-400"><el-icon><Setting/></el-icon></div>
      </div>
    </div>

    <!-- Content Area -->
    <div class="flex-1 flex overflow-hidden">
      <router-view></router-view>
    </div>

    <!-- Profile Modal -->
    <div v-if="showProfileModal" class="fixed inset-0 bg-black bg-opacity-50 z-[999] flex items-center justify-center">
       <div class="bg-white p-6 rounded-xl w-96 shadow-2xl">
          <h3 class="text-xl font-bold mb-4">Edit Profile</h3>
          
          <div class="flex justify-center mb-4">
             <div class="relative w-24 h-24 rounded-full bg-gray-100 overflow-hidden group cursor-pointer border-2 border-gray-100 hover:border-chill-blue">
                <img :src="profileForm.avatar" class="w-full h-full object-cover" />
                <div class="absolute inset-0 bg-black bg-opacity-30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition">
                   <span class="text-white text-xs">Change</span>
                </div>
                <input type="file" @change="handleAvatarUpload" class="absolute inset-0 opacity-0 cursor-pointer" accept="image/*"/>
             </div>
          </div>

          <div class="space-y-3">
             <input v-model="profileForm.username" class="w-full border p-2 rounded outline-none focus:border-chill-blue" placeholder="Username" />
             <input v-model="profileForm.signature" class="w-full border p-2 rounded outline-none focus:border-chill-blue" placeholder="Signature (Bio)" />
             <input v-model="profileForm.password" class="w-full border p-2 rounded outline-none focus:border-chill-blue" placeholder="New Password (Optional)" type="password" />
          </div>

          <div class="flex justify-end space-x-2 mt-6">
             <button @click="showProfileModal = false" class="text-gray-500 px-4 py-2 hover:bg-gray-100 rounded">Cancel</button>
             <button @click="updateProfile" class="bg-chill-blue text-white px-6 py-2 rounded">Save</button>
          </div>
       </div>
    </div>
  </div>
</template>
