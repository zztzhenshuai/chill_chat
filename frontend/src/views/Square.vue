<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Delete, ChatDotRound } from '@element-plus/icons-vue'

const posts = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const PAGE_SIZE = 20
const showCreateModal = ref(false)
const newPostContent = ref('')
const newPostImages = ref<string[]>([])
const currentUserId = Number(localStorage.getItem('chill_user_id'))
const currentUserAvatar = localStorage.getItem('chill_avatar')
const viewMode = ref<'all' | 'my'>('all') // 'all' or 'my'

// Comments State
const expandedComments = ref<Record<number, boolean>>({})
const postComments = ref<Record<number, any[]>>({})
const commentInputs = ref<Record<number, string>>({})

// Image Preview
const showImagePreview = ref(false)
const previewImage = ref('')

const openPreview = (url: string) => {
    previewImage.value = url
    showImagePreview.value = true
}

const loadFeed = async () => {
  loading.value = true
  currentPage.value = 1
  hasMore.value = true
  try {
    const params: any = { currentUserId, page: 1, size: PAGE_SIZE }
    if (viewMode.value === 'my') {
        params.filterUserId = currentUserId
    }
    const res = await axios.get('/api/posts', { params })
    const data = res.data.map((p: any) => ({
        ...p,
        images: p.imageUrl ? p.imageUrl.split(',') : []
    }))
    posts.value = data
    hasMore.value = data.length === PAGE_SIZE
  } catch (err) {
    ElMessage.error('加载动态失败')
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const nextPage = currentPage.value + 1
    const params: any = { currentUserId, page: nextPage, size: PAGE_SIZE }
    if (viewMode.value === 'my') {
        params.filterUserId = currentUserId
    }
    const res = await axios.get('/api/posts', { params })
    const data = res.data.map((p: any) => ({
        ...p,
        images: p.imageUrl ? p.imageUrl.split(',') : []
    }))
    posts.value.push(...data)
    currentPage.value = nextPage
    hasMore.value = data.length === PAGE_SIZE
  } catch (err) {
    ElMessage.error('加载更多失败')
  } finally {
    loadingMore.value = false
  }
}

const switchView = (mode: 'all' | 'my') => {
    viewMode.value = mode
    loadFeed()
}

const toggleLike = async (post: any) => {
  try {
    const res = await axios.post(`/api/posts/${post.id}/like`, null, {
      params: { userId: currentUserId }
    })
    post.likeCount = res.data
    post.isLiked = !post.isLiked
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

const deletePost = async (post: any) => {
    try {
        await ElMessageBox.confirm('确定要删除这条动态吗？', '提示', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning'
        })
        
        await axios.delete(`/api/posts/${post.id}`, { params: { userId: currentUserId } })
        ElMessage.success('已删除')
        loadFeed()
    } catch(err) {
        if(err !== 'cancel') ElMessage.error('删除失败')
    }
}

// Upload for post
const handleImageUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files) return

  const remaining = 9 - newPostImages.value.length
  if (remaining <= 0) {
      ElMessage.warning('最多只能上传9张图片')
      return
  }

  const filesToUpload = Array.from(files).slice(0, remaining)
  
  for(const file of filesToUpload) {
    const formData = new FormData()
    formData.append('file', file)
    try {
        const res = await axios.post('/api/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 60000 // 60s timeout for slow uploads
        })
        newPostImages.value.push(res.data.url)
    } catch (err) {
        ElMessage.error('图片上传失败')
    }
  }
  input.value = '' // reset
}

const removeImage = (index: number) => {
    newPostImages.value.splice(index, 1)
}

const createPost = async () => {
  if (!newPostContent.value && newPostImages.value.length === 0) return 
  
  try {
    await axios.post('/api/posts', {
      userId: currentUserId,
      content: newPostContent.value,
      imageUrl: newPostImages.value.join(',')
    })
    ElMessage.success('发布成功！')
    showCreateModal.value = false
    newPostContent.value = ''
    newPostImages.value = []
    loadFeed()
  } catch (err) {
    ElMessage.error('发布失败')
  }
}

// Comments Logic
const toggleComments = async (post: any) => {
    const postId = post.id
    if (expandedComments.value[postId]) {
        expandedComments.value[postId] = false
        return
    }
    
    // Load comments
    try {
        const res = await axios.get(`/api/posts/${postId}/comments`)
        postComments.value[postId] = res.data
        expandedComments.value[postId] = true
    } catch(err) {
        ElMessage.error('加载评论失败')
    }
}

const sendComment = async (post: any) => {
    const content = commentInputs.value[post.id]
    if (!content?.trim()) return
    
    try {
        await axios.post(`/api/posts/${post.id}/comments`, {
            userId: currentUserId,
            content: content
        })
        commentInputs.value[post.id] = ''
        // Reload comments
        const res = await axios.get(`/api/posts/${post.id}/comments`)
        postComments.value[post.id] = res.data
        post.commentCount++ 
    } catch(err) {
        ElMessage.error('评论失败')
    }
}

onMounted(() => {
  loadFeed()
})
</script>


<template>
  <div class="flex-1 flex flex-col bg-gradient-to-br from-indigo-50 via-purple-50 to-pink-50 dark:from-gray-900 dark:via-indigo-900/30 dark:to-purple-900/30 h-full relative font-sans overflow-hidden transition-colors duration-300">
    <!-- Decorative Background blobs -->
    <div class="absolute top-0 left-1/4 w-96 h-96 bg-indigo-200/30 dark:bg-indigo-600/20 rounded-full mix-blend-multiply dark:mix-blend-overlay filter blur-3xl opacity-70 animate-blob pointer-events-none"></div>
    <div class="absolute top-0 right-1/4 w-96 h-96 bg-purple-200/30 dark:bg-purple-600/20 rounded-full mix-blend-multiply dark:mix-blend-overlay filter blur-3xl opacity-70 animate-blob animation-delay-2000 pointer-events-none"></div>
    <div class="absolute -bottom-32 left-1/3 w-96 h-96 bg-pink-200/30 dark:bg-pink-600/20 rounded-full mix-blend-multiply dark:mix-blend-overlay filter blur-3xl opacity-70 animate-blob animation-delay-4000 pointer-events-none"></div>

    <!-- Header -->
    <div class="h-16 bg-white/40 dark:bg-gray-800/40 backdrop-blur-md border-b border-white/20 dark:border-gray-700/50 flex items-center justify-between px-6 sticky top-0 z-20 transition-all duration-300 shadow-sm">
      <div class="flex space-x-8">
          <button 
            @click="switchView('all')" 
            class="relative py-4 text-sm font-bold tracking-wide transition-all duration-300"
            :class="viewMode === 'all' ? 'text-transparent bg-clip-text bg-gradient-to-r from-violet-600 to-indigo-600 dark:from-violet-400 dark:to-indigo-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'"
          >
            广场动态
            <span v-if="viewMode === 'all'" class="absolute bottom-0 left-0 w-full h-[3px] bg-gradient-to-r from-violet-500 to-indigo-500 rounded-full animate-slideIn shadow-[0_0_10px_rgba(139,92,246,0.3)]"></span>
          </button>
          <button 
            @click="switchView('my')" 
            class="relative py-4 text-sm font-bold tracking-wide transition-all duration-300"
            :class="viewMode === 'my' ? 'text-transparent bg-clip-text bg-gradient-to-r from-pink-600 to-rose-600 dark:from-pink-400 dark:to-rose-400' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'"
          >
            我的帖子
            <span v-if="viewMode === 'my'" class="absolute bottom-0 left-0 w-full h-[3px] bg-gradient-to-r from-pink-500 to-rose-500 rounded-full animate-slideIn shadow-[0_0_10px_rgba(244,63,94,0.3)]"></span>
          </button>
      </div>
      <button 
        @click="showCreateModal = true"
        class="bg-gradient-to-r from-violet-500 via-purple-500 to-fuchsia-500 text-white px-6 py-2 rounded-full text-sm font-bold hover:shadow-lg hover:shadow-purple-200 transition-all duration-300 transform hover:-translate-y-0.5 active:scale-95 border border-white/20"
      >
        <span class="flex items-center space-x-1.5">
          <span class="text-lg leading-none">+</span>
          <span>发布 Note</span>
        </span>
      </button>
    </div>

    <!-- Feed -->
    <div class="flex-1 overflow-y-auto p-4 md:p-8 space-y-6 scroll-smooth">
      <div v-if="loading && posts.length === 0" class="flex flex-col items-center justify-center mt-20 space-y-4">
          <div class="w-10 h-10 border-4 border-blue-100 border-t-blue-500 rounded-full animate-spin"></div>
          <p class="text-gray-400 text-sm tracking-widest animate-pulse">LOADING VIBES...</p>
      </div>
      
      <div v-if="!loading && posts.length === 0" class="flex flex-col items-center justify-center mt-20 animate-fade-in">
          <div class="text-6xl mb-4">🏜️</div>
          <p class="text-gray-400 font-light">这里还是一片荒原，去种下第一棵树吧</p>
      </div>
      
      <div
        v-for="(post, index) in posts"
        :key="post.id"
        class="bg-white dark:bg-gray-800 rounded-2xl p-6 border border-gray-100 dark:border-gray-700 max-w-2xl mx-auto transition-all duration-300 hover:shadow-xl hover:shadow-gray-100 dark:hover:shadow-black/30 hover:-translate-y-1 group"
        :style="{ animation: `fadeInUp 0.5s ease-out backwards ${index * 0.1}s` }"
      >
        <!-- Author -->
        <div class="flex items-center justify-between mb-4">
            <div class="flex items-center cursor-pointer group/author">
                <div class="relative">
                    <img :src="post.avatar" class="w-12 h-12 rounded-full object-cover border-[3px] border-white dark:border-gray-700 shadow-sm ring-2 ring-transparent group-hover/author:ring-indigo-100 dark:group-hover/author:ring-indigo-900 transition-all duration-300" />
                    <!-- Online indicator (mock) -->
                    <div class="absolute bottom-0 right-0 w-3.5 h-3.5 bg-gradient-to-tr from-green-400 to-emerald-400 border-2 border-white dark:border-gray-700 rounded-full"></div>
                </div>
                <div class="ml-3">
                    <div class="font-bold text-gray-800 dark:text-gray-100 text-[15px] tracking-wide group-hover/author:text-indigo-600 dark:group-hover/author:text-indigo-400 transition-colors">{{ post.username }}</div>
                    <div class="text-xs text-gray-400 mt-0.5 font-medium flex items-center">
                       <span>{{ new Date(post.createTime).toLocaleString() }}</span>
                       <span class="mx-2 text-gray-200 dark:text-gray-600">|</span>
                       <span class="text-indigo-300 dark:text-indigo-400">{{ ['上海', '北京', '广州', '深圳'][Math.floor(Math.random()*4)] }}</span>
                    </div>
                </div>
            </div>
            
            <!-- Delete Button (Only for owner) -->
            <button 
               v-if="post.userId === currentUserId"
               @click="deletePost(post)"
               class="text-gray-300 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 p-2 rounded-full transition-all duration-300 opacity-0 group-hover:opacity-100"
               title="删除"
            >
               <el-icon><Delete /></el-icon>
            </button>
        </div>

        <!-- Content -->
        <div class="text-gray-700 dark:text-gray-300 mb-4 whitespace-pre-wrap text-[15px] leading-relaxed tracking-wide font-normal">{{ post.content }}</div>
        
        <!-- Image Grid -->
        <div v-if="post.images && post.images.length > 0" class="mb-5">
           <!-- 1 Image -->
           <div v-if="post.images.length === 1" class="rounded-2xl overflow-hidden max-h-[500px] w-fit shadow-sm hover:shadow-md transition-shadow duration-300">
              <img 
                 v-lazy="post.images[0]" 
                 @click.stop="openPreview(post.images[0])"
                 class="max-h-full max-w-full object-contain cursor-zoom-in hover:scale-[1.02] transition-transform duration-500 block" 
              />
           </div>
           
           <!-- 2 or 4 Images -->
           <div v-else-if="post.images.length === 2 || post.images.length === 4" class="grid grid-cols-2 gap-2 max-w-[70%]">
              <div v-for="(img, idx) in post.images" :key="idx" class="aspect-square rounded-xl overflow-hidden cursor-zoom-in relative group/img">
                  <div class="absolute inset-0 bg-black/0 group-hover/img:bg-black/10 transition-colors duration-300 z-10 w-full h-full"></div>
                  <img 
                     v-lazy="img" 
                     @click.stop="openPreview(img)"
                     class="w-full h-full object-cover object-top hover:scale-110 transition-transform duration-700"
                  />
              </div>
           </div>
           
           <!-- 3, 5-9 Images -->
           <div v-else class="grid grid-cols-3 gap-2">
              <div v-for="(img, idx) in post.images" :key="idx" class="aspect-square rounded-xl overflow-hidden cursor-zoom-in relative group/img">
                  <div class="absolute inset-0 bg-black/0 group-hover/img:bg-black/10 transition-colors duration-300 z-10 w-full h-full"></div>
                  <img 
                     v-lazy="img" 
                     @click.stop="openPreview(img)"
                     class="w-full h-full object-cover object-center hover:scale-110 transition-transform duration-700"
                  />
              </div>
           </div>
        </div>

        <!-- Actions -->
        <div class="flex items-center pt-4 border-t border-gray-100/50 text-gray-500 text-sm space-x-8">
           <button 
             @click="toggleLike(post)"
             class="flex items-center space-x-1.5 transition-all duration-300 group/btn"
             :class="post.isLiked ? 'text-rose-500' : 'hover:text-rose-500'"
           >
             <svg
               class="w-5 h-5 transition-transform duration-300"
               :class="{'scale-125 animate-heart-pop': post.isLiked, 'group-hover/btn:scale-110': !post.isLiked}"
               viewBox="0 0 24 24"
               :fill="post.isLiked ? 'currentColor' : 'none'"
               stroke="currentColor"
               stroke-width="2"
               stroke-linecap="round"
               stroke-linejoin="round"
             >
               <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
             </svg>
             <span class="font-medium">{{ post.likeCount || 0 }}</span>
           </button>

           <button 
             @click="toggleComments(post)"
             class="flex items-center space-x-1.5 hover:text-indigo-500 transition-all duration-300 group/btn"
             :class="expandedComments[post.id] ? 'text-indigo-500' : ''"
           >
             <svg
               class="w-5 h-5 transition-transform duration-300 group-hover/btn:scale-110"
               viewBox="0 0 24 24"
               fill="none"
               stroke="currentColor"
               stroke-width="2"
               stroke-linecap="round"
               stroke-linejoin="round"
             >
               <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
             </svg>
             <span class="font-medium">{{ post.commentCount || 0 }}</span>
           </button>
        </div>

        <!-- Comments Section -->
        <div v-if="expandedComments[post.id]" class="mt-4 bg-white/50 border border-white/60 shadow-inner rounded-xl p-4 animate-slideDown overflow-hidden backdrop-blur-sm">
            <!-- List -->
            <div v-if="postComments[post.id] && postComments[post.id].length > 0" class="space-y-3 mb-4 max-h-60 overflow-y-auto pr-2 custom-scrollbar">
                <div v-for="comment in postComments[post.id]" :key="comment.id" class="flex items-start text-sm group/comment">
                   <div class="flex-shrink-0 mr-2 mt-0.5">
                        <img :src="comment.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" class="w-7 h-7 rounded-full border border-white shadow-sm" />
                   </div>
                   <div class="bg-white/80 px-3 py-2 rounded-2xl rounded-tl-none shadow-sm flex-1 hover:bg-white transition-colors duration-200">
                       <div class="flex justify-between items-baseline mb-1">
                           <span class="font-bold text-gray-700 text-xs">{{comment.userId === currentUserId ? '我' : comment.username}}</span>
                           <span class="text-[10px] text-gray-300 font-light">{{ new Date(comment.createTime).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) }}</span>
                       </div>
                       <div class="text-gray-600 leading-relaxed">{{comment.content}}</div>
                   </div>
                </div>
            </div>
            <div v-else class="flex flex-col items-center justify-center py-6 text-gray-400">
                <el-icon class="text-2xl mb-1 opacity-50"><ChatDotRound /></el-icon>
                <span class="text-xs">还没有评论，快来抢沙发</span>
            </div>
            
            <!-- Input -->
            <div class="flex items-center space-x-2 relative">
                <img :src="currentUserAvatar" class="w-8 h-8 rounded-full border border-gray-200 dark:border-gray-700" v-if="currentUserAvatar" />
                <input 
                   v-model="commentInputs[post.id]"
                   class="flex-1 bg-white/50 dark:bg-gray-700/50 backdrop-blur-sm border-0 ring-1 ring-gray-200/50 dark:ring-gray-600/50 rounded-full px-4 py-2 text-sm outline-none focus:ring-2 focus:ring-purple-400 dark:focus:ring-purple-500 transition-all shadow-sm pl-4 text-gray-800 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500"
                   placeholder="写下你的评论..." 
                   @keydown.enter="sendComment(post)"
                />
                <button 
                  @click="sendComment(post)"
                  class="absolute right-1 top-1 bottom-1 bg-gradient-to-r from-indigo-500 to-purple-500 text-white px-4 rounded-full text-xs font-bold hover:shadow-lg hover:from-indigo-600 hover:to-purple-600 transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
                  :disabled="!commentInputs[post.id]?.trim()"
                >
                  发送
                </button>
            </div>
        </div>
      </div>

      <!-- Load More -->
      <div class="flex justify-center py-6 max-w-2xl mx-auto w-full">
        <button
          v-if="hasMore && !loading"
          @click="loadMore"
          :disabled="loadingMore"
          class="flex items-center space-x-2 px-8 py-2.5 rounded-full border border-indigo-200 dark:border-indigo-700 text-indigo-500 dark:text-indigo-400 text-sm font-semibold hover:bg-indigo-50 dark:hover:bg-indigo-900/30 transition-all duration-300 disabled:opacity-60"
        >
          <span v-if="loadingMore" class="w-4 h-4 border-2 border-indigo-300 border-t-indigo-500 rounded-full animate-spin"></span>
          <span>{{ loadingMore ? '加载中...' : '加载更多' }}</span>
        </button>
        <p v-else-if="!hasMore && posts.length > 0" class="text-gray-400 dark:text-gray-600 text-xs">— 已经到底了 —</p>
      </div>
    </div>

    <!-- Image Preview Modal -->
    <div v-if="showImagePreview" class="fixed inset-0 bg-black bg-opacity-90 z-[100] flex items-center justify-center cursor-zoom-out" @click="showImagePreview = false">
        <img :src="previewImage" class="max-w-[95vw] max-h-[95vh] object-contain shadow-2xl rounded-lg" @click.stop />
        <button class="absolute top-4 right-4 text-white text-4xl hover:text-gray-300 transform hover:rotate-90 transition p-4">×</button>
    </div>

    <div v-if="showCreateModal" class="fixed inset-0 bg-black/40 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div class="bg-white/80 dark:bg-gray-800/90 backdrop-blur-2xl rounded-xl shadow-2xl w-full max-w-lg p-6 flex flex-col max-h-[90vh] ring-1 ring-white/60 dark:ring-gray-700">
         <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold text-gray-800 dark:text-gray-100">发布新动态</h3>
            <button @click="showCreateModal = false" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 font-bold px-2">✕</button>
         </div>

         <textarea 
            v-model="newPostContent"
            class="w-full h-32 p-3 border rounded-lg resize-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500 outline-none mb-4 bg-gray-50/50 dark:bg-gray-700/50 dark:border-gray-600 text-gray-800 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500"
            placeholder="分享你的新鲜事..."
         ></textarea>

         <!-- Image Preview Grid -->
         <div v-if="newPostImages.length > 0" class="grid grid-cols-3 gap-2 mb-4 max-h-48 overflow-y-auto p-1">
            <div v-for="(img, idx) in newPostImages" :key="idx" class="relative aspect-square rounded overflow-hidden group border">
                <img :src="img" class="w-full h-full object-cover"/>
                <div class="absolute inset-0 bg-black bg-opacity-40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition cursor-pointer" @click="removeImage(idx)">
                    <el-icon class="text-white text-xl"><Delete/></el-icon>
                </div>
            </div>
            
            <!-- Upload placeholder (if < 9) -->
            <label 
               v-if="newPostImages.length < 9"
               class="border-2 border-dashed border-gray-300 rounded flex flex-col items-center justify-center cursor-pointer hover:border-purple-500 hover:text-purple-500 aspect-square transition bg-gray-50/50 hover:bg-purple-50/30"
            >
                 <input type="file" @change="handleImageUpload" class="hidden" accept="image/*" multiple />
                 <el-icon class="text-xl mb-1"><Picture /></el-icon>
                 <span class="text-xs">{{ newPostImages.length }}/9</span>
            </label>
         </div>
         
         <div v-else class="mb-4">
              <label class="inline-flex items-center cursor-pointer text-gray-500 hover:text-purple-600 transition bg-gray-100 hover:bg-purple-50 px-4 py-2 rounded-lg text-sm">
                 <input type="file" @change="handleImageUpload" class="hidden" accept="image/*" multiple />
                 <el-icon class="mr-2 text-lg"><Picture /></el-icon>
                 添加图片 (最多9张)
              </label>
         </div>

         <div class="flex justify-end pt-2 border-t mt-auto">
             <button 
               @click="createPost"
               :disabled="!newPostContent && newPostImages.length === 0"
               class="bg-gradient-to-r from-indigo-500 to-purple-500 text-white px-8 py-2 rounded-lg hover:shadow-lg hover:scale-105 transition disabled:opacity-50 font-bold"
             >
               发布
             </button>
         </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out forwards;
}

@keyframes slideIn {
  from {
    width: 0;
    opacity: 0;
  }
  to {
    width: 100%;
    opacity: 1;
  }
}

/* Custom heartbeat animation */
@keyframes heartPop {
  0% { transform: scale(1); }
  50% { transform: scale(1.4); }
  100% { transform: scale(1); }
}

.animate-heart-pop {
  animation: heartPop 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

/* Smooth scrollbar for comment section */
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #cbd5e1;
  border-radius: 20px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background-color: #94a3b8;
}

/* Blob Background Animation */
@keyframes blob {
  0% { transform: translate(0px, 0px) scale(1); }
  33% { transform: translate(30px, -50px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
  100% { transform: translate(0px, 0px) scale(1); }
}
.animate-blob {
  animation: blob 7s infinite;
}
.animation-delay-2000 {
  animation-delay: 2s;
}
.animation-delay-4000 {
  animation-delay: 4s;
}
</style>
