<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const isRegister = ref(false)
const formData = ref({
  username: '',
  password: ''
})
const loading = ref(false)

const handleSubmit = async () => {
  if (!formData.value.username || !formData.value.password) return
  
  loading.value = true
  try {
    const endpoint = isRegister.value ? '/api/auth/register' : '/api/auth/login'
    const res = await axios.post(endpoint, formData.value)
    
    if (isRegister.value) {
      ElMessage.success('Registered! Please login.')
      isRegister.value = false
    } else {
      // Login success
      const { token, userId, username, avatar } = res.data
      localStorage.setItem('token', token)
      localStorage.setItem('chill_user_id', userId)
      localStorage.setItem('chill_username', username)
      localStorage.setItem('chill_avatar', avatar)
      
      ElMessage.success('Welcome back, ' + username)
      router.push('/app/chat')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data || 'Operation failed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-chill-bg">
    <div class="bg-white p-8 rounded-xl shadow-lg w-full max-w-md">
      <h1 class="text-2xl font-bold text-center mb-6 text-chill-blue">Chill Chat</h1>
      
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Username</label>
          <input 
            v-model="formData.username"
            type="text" 
            class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-chill-blue focus:ring focus:ring-chill-blue focus:ring-opacity-50 p-2 border"
          />
        </div>

        <div>
           <label class="block text-sm font-medium text-gray-700">Password</label>
           <input 
             v-model="formData.password"
             type="password" 
             class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-chill-blue focus:ring focus:ring-chill-blue focus:ring-opacity-50 p-2 border"
           />
        </div>
        
        <button 
          @click="handleSubmit" 
          :disabled="loading"
          class="w-full bg-chill-blue text-white py-2 rounded-md hover:bg-blue-600 transition disabled:opacity-50"
        >
          {{ loading ? 'Processing...' : (isRegister ? 'Register' : 'Login') }}
        </button>

        <div class="text-center text-sm text-gray-500 cursor-pointer hover:text-chill-blue" @click="isRegister = !isRegister">
          {{ isRegister ? 'Already have account? Login' : 'No account? Register' }}
        </div>
      </div>
    </div>
  </div>
</template>
