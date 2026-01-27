<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'

const posts = ref<any[]>([])
const loading = ref(false)
const showCreateModal = ref(false)
const newPostContent = ref('')
const newPostImage = ref('')
const currentUserId = Number(localStorage.getItem('chill_user_id'))

const loadFeed = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/posts', {
       params: { currentUserId } 
    })
    posts.value = res.data
  } catch (err) {
    ElMessage.error('Failed to load feed')
  } finally {
    loading.value = false
  }
}

const toggleLike = async (post: any) => {
  try {
    const res = await axios.post(`/api/posts/${post.id}/like`, null, {
      params: { userId: currentUserId }
    })
    post.likeCount = res.data
    post.isLiked = !post.isLiked
  } catch (err) {
    ElMessage.error('Action failed')
  }
}

// Upload for post
const handleImageUpload = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)
  
  try {
    const res = await axios.post('/api/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    newPostImage.value = res.data.url
  } catch (err) {
    ElMessage.error('Image upload failed')
  }
}

const createPost = async () => {
  if (!newPostContent.value && !newPostImage.value) return 
  
  try {
    await axios.post('/api/posts', {
      userId: currentUserId,
      content: newPostContent.value,
      imageUrl: newPostImage.value
    })
    ElMessage.success('Posted!')
    showCreateModal.value = false
    newPostContent.value = ''
    newPostImage.value = ''
    loadFeed()
  } catch (err) {
    ElMessage.error('Failed to post')
  }
}

onMounted(() => {
  loadFeed()
})
</script>

<template>
  <div class="flex-1 flex flex-col bg-gray-50 h-full relative">
    <!-- Header -->
    <div class="h-14 bg-white border-b flex items-center justify-between px-6 shadow-sm flex-shrink-0">
      <h1 class="text-lg font-bold text-gray-800">Relax Square</h1>
      <button 
        @click="showCreateModal = true"
        class="bg-chill-blue text-white px-4 py-1.5 rounded-full text-sm hover:bg-blue-600 transition"
      >
        New Post +
      </button>
    </div>

    <!-- Feed -->
    <div class="flex-1 overflow-y-auto p-4 md:p-8 space-y-6">
      <div v-if="loading && posts.length === 0" class="text-center text-gray-400">Loading vibes...</div>
      
      <div 
        v-for="post in posts" 
        :key="post.id"
        class="bg-white rounded-xl shadow-sm p-4 border border-gray-100 max-w-2xl mx-auto"
      >
        <!-- Author -->
        <div class="flex items-center mb-3">
          <img :src="post.avatar" class="w-10 h-10 rounded-full mr-3 border" />
          <div>
             <div class="font-bold text-gray-800">{{ post.username }}</div>
             <div class="text-xs text-gray-400">{{ new Date(post.createTime).toLocaleString() }}</div>
          </div>
        </div>

        <!-- Content -->
        <div class="text-gray-700 mb-3 whitespace-pre-wrap">{{ post.content }}</div>
        
        <!-- Image -->
        <div v-if="post.imageUrl" class="mb-4 rounded-lg overflow-hidden max-h-96 bg-gray-50 flex justify-center">
          <img v-lazy="post.imageUrl" class="object-contain max-h-full" />
        </div>

        <!-- Actions -->
        <div class="flex items-center pt-3 border-t border-gray-50 text-gray-500 text-sm space-x-6">
           <button 
             @click="toggleLike(post)"
             class="flex items-center space-x-1 hover:text-red-500 transition"
             :class="post.isLiked ? 'text-red-500' : ''"
           >
             <span>{{ post.isLiked ? '♥' : '♡' }}</span>
             <span>{{ post.likeCount }}</span>
           </button>

           <div class="flex items-center space-x-1">
             <span>💬</span>
             <span>{{ post.commentCount }}</span>
           </div>
        </div>
      </div>
    </div>

    <!-- Create Modal -->
    <div v-if="showCreateModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-lg p-6 animate-[fadeIn_0.2s_ease-out]">
         <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">Create Post</h3>
            <button @click="showCreateModal = false" class="text-gray-400 hover:text-gray-600">✕</button>
         </div>

         <textarea 
            v-model="newPostContent"
            class="w-full h-32 p-3 border rounded-lg resize-none focus:border-chill-blue focus:ring-1 focus:ring-chill-blue outline-none"
            placeholder="Share your thoughts..."
         ></textarea>

         <div v-if="newPostImage" class="mt-2 relative">
            <img :src="newPostImage" class="h-20 rounded border" />
            <button @click="newPostImage = ''" class="absolute -top-2 -right-2 bg-red-500 text-white rounded-full w-5 h-5 text-xs">✕</button>
         </div>

         <div class="flex justify-between items-center mt-4 border-t pt-4">
             <label class="cursor-pointer text-chill-blue hover:bg-blue-50 px-3 py-1.5 rounded transition">
                 <input type="file" @change="handleImageUpload" class="hidden" accept="image/*" />
                 <el-icon class="mr-1 relative top-0.5"><Picture /></el-icon>
                 Add Image
             </label>

             <button 
               @click="createPost"
               :disabled="!newPostContent && !newPostImage"
               class="bg-chill-blue text-white px-6 py-2 rounded-lg hover:bg-blue-600 transition disabled:opacity-50"
             >
               Post
             </button>
         </div>
      </div>
    </div>
  </div>
</template>
