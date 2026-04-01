<script setup lang="ts">
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { chatSocket, type ChatMessage } from '@/services/websocket'
import MessageBubble from '@/components/MessageBubble.vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Plus, Picture, Male, Female } from '@element-plus/icons-vue'

const router = useRouter()
const currentUserId = Number(localStorage.getItem('chill_user_id') || 0)
const targetId = ref<number>(0) // The person we are talking to
const isGroup = ref(false)

const getAge = (birthday: string) => {
    if (!birthday) return '未知'
    const birth = new Date(birthday)
    const today = new Date()
    let age = today.getFullYear() - birth.getFullYear()
    const m = today.getMonth() - birth.getMonth()
    if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
        age--
    }
    return age
}

const inputText = ref('')
const messages = ref<any[]>([])
const chatContainer = ref<HTMLElement | null>(null)
const showScrollBtn = ref(false)

// Pagination
const oldestMessageId = ref<number>(-1)
const hasMoreHistory = ref(false)
const isLoadingMore = ref(false)

const showAddFriendModal = ref(false)
const showRequestsModal = ref(false)
const showFriendInfoModal = ref(false)
const selectedFriend = ref<any>(null)

const searchFriendId = ref('')
const requestReason = ref('')
const pendingRequests = ref<any[]>([])

// Image Preview
const showImagePreview = ref(false)
const previewImage = ref('')

const openPreview = (url: string) => {
    previewImage.value = url
    showImagePreview.value = true
}

const userCache = ref<Record<number, any>>({})
const unreadCounts = ref<Record<string, number>>({})

// Search Query
const searchQuery = ref('')
const filteredFriends = computed(() => {
    if (!searchQuery.value) return friends.value
    const q = searchQuery.value.toLowerCase()
    return friends.value.filter(f => f.name.toLowerCase().includes(q) || (f.signature && f.signature.toLowerCase().includes(q)))
})

// Real Friend List
const friends = ref<any[]>([])
const groups = ref<any[]>([])
const showFriends = ref(true)
const showGroups = ref(true)

// Group Creation
const showCreateGroupModal = ref(false)
const newGroupName = ref('')
const selectedFriendsForGroup = ref<number[]>([])

// Group Management
const showGroupInfoModal = ref(false)
const currentGroupInfo = ref<any>(null)
const currentGroupMembers = ref<any[]>([])
const editingGroupName = ref('')
const showInviteModal = ref(false)
const selectedInviteIds = ref<number[]>([])

// Computed: my role in current group info modal
const myRoleInCurrentGroup = computed(() =>
  currentGroupMembers.value.find((m: any) => m.userId === currentUserId)?.role ?? ''
)

const openGroupInfo = async (groupId: number) => {
  const group = groups.value.find(g => g.id === groupId)
  if (!group) return
  currentGroupInfo.value = { ...group }
  editingGroupName.value = group.name
  showGroupInfoModal.value = true

  try {
    const res = await axios.get(`/api/groups/${groupId}/members`)
    currentGroupMembers.value = res.data
  } catch (err) { console.error('Failed to load group members', err) }
}

const quitGroup = async (groupId: number) => {
  try {
    await axios.delete(`/api/groups/${groupId}/quit`)
    ElMessage.success('已退出群聊')
    showGroupInfoModal.value = false
    if (targetId.value === groupId && isGroup.value) {
      targetId.value = 0
      messages.value = []
    }
    loadGroups()
  } catch (err: any) {
    ElMessage.error(err.response?.data || '退出失败')
  }
}

const disbandGroup = async (groupId: number) => {
  try {
    await axios.delete(`/api/groups/${groupId}`)
    ElMessage.success('群聊已解散')
    showGroupInfoModal.value = false
    if (targetId.value === groupId && isGroup.value) {
      targetId.value = 0
      messages.value = []
    }
    loadGroups()
  } catch (err: any) {
    ElMessage.error(err.response?.data || '解散失败')
  }
}

const kickMember = async (groupId: number, userId: number) => {
  try {
    await axios.delete(`/api/groups/${groupId}/kick`, { params: { userId } })
    ElMessage.success('已将该成员移出群聊')
    currentGroupMembers.value = currentGroupMembers.value.filter(m => m.userId !== userId)
  } catch (err: any) {
    ElMessage.error(err.response?.data || '操作失败')
  }
}

const updateGroupName = async (groupId: number) => {
  if (!editingGroupName.value.trim()) return
  try {
    await axios.put(`/api/groups/${groupId}`, null, { params: { groupName: editingGroupName.value } })
    ElMessage.success('群名已更新')
    const group = groups.value.find(g => g.id === groupId)
    if (group) group.name = editingGroupName.value
    if (currentGroupInfo.value) currentGroupInfo.value.name = editingGroupName.value
  } catch (err: any) {
    ElMessage.error(err.response?.data || '更新失败')
  }
}

const setAdmin = async (groupId: number, userId: number) => {
  try {
    const res = await axios.put(`/api/groups/${groupId}/promote`, null, { params: { userId } })
    ElMessage.success(res.data)
    // Refresh member list
    const mRes = await axios.get(`/api/groups/${groupId}/members`)
    currentGroupMembers.value = mRes.data
  } catch (err: any) {
    ElMessage.error(err.response?.data || '操作失败')
  }
}

const inviteFriendsToGroup = async () => {
  if (selectedInviteIds.value.length === 0) return
  try {
    const res = await axios.post(`/api/groups/${currentGroupInfo.value.id}/invite`, selectedInviteIds.value)
    ElMessage.success(res.data)
    showInviteModal.value = false
    selectedInviteIds.value = []
    const mRes = await axios.get(`/api/groups/${currentGroupInfo.value.id}/members`)
    currentGroupMembers.value = mRes.data
  } catch (err: any) {
    ElMessage.error(err.response?.data || '邀请失败')
  }
}

// Friends not yet in the current group
const friendsNotInGroup = computed(() => {
  const memberIds = new Set(currentGroupMembers.value.map((m: any) => m.userId))
  return friends.value.filter((f: any) => !memberIds.has(f.id))
})

const fetchUserInfo = async (userId: number) => {
    if (userCache.value[userId]) return
    try {
        const res = await axios.get(`/api/user/${userId}`)
        userCache.value[userId] = {
            id: res.data.id,
            name: res.data.username,
            avatar: res.data.avatar,
            signature: res.data.signature,
            gender: res.data.gender,
            birthday: res.data.birthday,
            location: res.data.location
        }
    } catch(err) { console.error('Failed to fetch user', userId)} 
}

const handleAvatarClick = async (userId: number) => {
   if (!userCache.value[userId]) await fetchUserInfo(userId)
   if (userCache.value[userId]) {
       selectedFriend.value = userCache.value[userId]
       showFriendInfoModal.value = true
   }
}

const loadGroups = async () => {
    try {
        const res = await axios.get('/api/groups/my')
        groups.value = res.data.map((g: any) => ({
            id: g.id,
            name: g.name,
            avatar: g.avatar,
            isGroup: true
        }))
    } catch (err) { console.error('Failed to load groups', err) }
}

const toggleFriendSelection = (friendId: number) => {
    if (selectedFriendsForGroup.value.includes(friendId)) {
        selectedFriendsForGroup.value = selectedFriendsForGroup.value.filter(id => id !== friendId)
    } else {
        selectedFriendsForGroup.value.push(friendId)
    }
}

const createGroup = async () => {
    if (!newGroupName.value.trim() || selectedFriendsForGroup.value.length === 0) {
        ElMessage.warning('请命名群组并选择至少一位好友')
        return
    }
    
    try {
        await axios.post('/api/groups/create', selectedFriendsForGroup.value, { 
            params: { 
                groupName: newGroupName.value
            }
        })
        ElMessage.success('群组已创建！')
        showCreateGroupModal.value = false
        newGroupName.value = ''
        selectedFriendsForGroup.value = []
        loadGroups()
    } catch(err) {
        ElMessage.error('创建群组失败')
    }
}

const loadFriends = async () => {
  // Load friends
  try {
    const res = await axios.get('/api/friends')
    
    // Map and Cache first
    const list = res.data.map((f: any) => {
        const u = {
          id: f.friendId,
          name: f.friendName,
          avatar: f.friendAvatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${f.friendId}`,
          signature: f.friendSignature || '随便看看...',
          gender: f.friendGender,
          birthday: f.friendBirthday,
          location: f.friendLocation,
          isOnline: f.isOnline,
          isGroup: false
        }
        // Cache friend info
        userCache.value[f.friendId] = u
        return u
    })

    // Sort: ChillBot first, then Online status
    friends.value = list.sort((a: any, b: any) => {
        if (a.name === 'ChillBot') return -1;
        if (b.name === 'ChillBot') return 1;
        // Then sort by online status
        if (a.isOnline && !b.isOnline) return -1;
        if (!a.isOnline && b.isOnline) return 1;
        return 0;
    })
    
    // Cache self
    userCache.value[currentUserId] = {
       id: currentUserId,
       name: localStorage.getItem('chill_username') || '我',
       avatar: localStorage.getItem('chill_avatar') || '',
       signature: localStorage.getItem('chill_signature') || ''
    }
  } catch (err) { console.error(err) }

  // Load requests
  try {
      const resReq = await axios.get('/api/friends/requests')
      pendingRequests.value = resReq.data.map((r: any) => ({
          requestId: r.id,
          name: r.requesterName,
          avatar: r.requesterAvatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${r.requesterId}`,
          reason: r.reason
      }))
  } catch(err) { console.error(err) }
  
  loadGroups()
}

const sendFriendRequest = async () => {
  if (!searchFriendId.value) return
  try {
    await axios.post('/api/friends/request', null, {
      params: {
        friendId: searchFriendId.value,
        reason: requestReason.value || '你好，交个朋友吧！'
      }
    })
    ElMessage.success('请求已发送！')
    showAddFriendModal.value = false
    searchFriendId.value = ''
    requestReason.value = ''
  } catch (err: any) {
    ElMessage.error(err.response?.data || '发送失败')
  }
}

const handleRequest = async (requestId: number, status: string) => {
    try {
        await axios.post('/api/friends/respond', null, {
            params: { requestId, status }
        })
        ElMessage.success(status === 'ACCEPTED' ? '已添加好友！' : '已拒绝')
        loadFriends() // Refresh list
    } catch(err) { ElMessage.error('操作失败') }
}

const openFriendInfo = (friend: any) => {
    selectedFriend.value = friend
    showFriendInfoModal.value = true
}

const deleteFriend = async () => {
    if(!selectedFriend.value) return
    try {
        await axios.delete('/api/friends/delete', {
            params: { friendId: selectedFriend.value.id }
        })
        ElMessage.success('好友已删除')
        showFriendInfoModal.value = false
        if (targetId.value === selectedFriend.value.id) {
            targetId.value = 0
            messages.value = []
        }
        loadFriends()
    } catch(err) { ElMessage.error('删除失败') }
}



// Select Chat
const selectChat = async (id: number, group: boolean) => {
  if (id === targetId.value) return // Don't reload if clicking same chat
  
  targetId.value = id
  isGroup.value = group

  // Clear unread count
  const key = (group ? 'g-' : 'f-') + id
  unreadCounts.value[key] = 0
  
  messages.value = [] // Setup UI for loading

  await loadHistory()
}

const loadHistory = async () => {
  if (!targetId.value) return
  oldestMessageId.value = -1
  hasMoreHistory.value = false
  try {
    const res = await axios.get('/api/messages/history', {
      params: {
        targetId: targetId.value,
        isGroup: isGroup.value,
        pageSize: 30
      }
    })
    
    // FETCH MISSING AVATARS
    const uids = new Set(res.data.map((m: any) => m.senderId))
    uids.forEach(uid => {
      if(!userCache.value[uid as number]) fetchUserInfo(uid as number)
    })
    
    // Map backend entity to frontend format
    const mapped = res.data.map((m: any) => ({
      type: 'CHAT',
      id: m.id,
      senderId: m.senderId,
      targetId: m.targetId,
      isGroup: m.isGroup,
      content: m.content,
      timestamp: new Date(m.createTime).getTime(),
      isSelf: m.senderId === currentUserId
    }))

    messages.value = mapped
    if (mapped.length > 0) {
      oldestMessageId.value = mapped[0].id
    }
    hasMoreHistory.value = res.data.length >= 30
    scrollToBottom()
  } catch (err) {
    console.error('Failed to load history', err)
  }
}

const loadMoreHistory = async () => {
  if (!targetId.value || !hasMoreHistory.value || isLoadingMore.value) return
  if (oldestMessageId.value === -1) return

  isLoadingMore.value = true
  const container = chatContainer.value
  const prevScrollHeight = container?.scrollHeight ?? 0

  try {
    const res = await axios.get('/api/messages/history', {
      params: {
        targetId: targetId.value,
        isGroup: isGroup.value,
        beforeId: oldestMessageId.value,
        pageSize: 30
      }
    })

    const uids = new Set(res.data.map((m: any) => m.senderId))
    uids.forEach(uid => {
      if(!userCache.value[uid as number]) fetchUserInfo(uid as number)
    })

    const mapped = res.data.map((m: any) => ({
      type: 'CHAT',
      id: m.id,
      senderId: m.senderId,
      targetId: m.targetId,
      isGroup: m.isGroup,
      content: m.content,
      timestamp: new Date(m.createTime).getTime(),
      isSelf: m.senderId === currentUserId
    }))

    messages.value = [...mapped, ...messages.value]
    if (mapped.length > 0) {
      oldestMessageId.value = mapped[0].id
    }
    hasMoreHistory.value = res.data.length >= 30

    // Restore scroll so the user's view doesn't jump
    nextTick(() => {
      if (container) {
        container.scrollTop = container.scrollHeight - prevScrollHeight
      }
    })
  } catch (err) {
    console.error('Failed to load more history', err)
  } finally {
    isLoadingMore.value = false
  }
}

// Send Message
const sendMessage = () => {
  if (!inputText.value.trim() || !targetId.value) return
  
  const msg: ChatMessage = {
    type: 'CHAT',
    senderId: currentUserId,
    targetId: targetId.value,
    isGroup: isGroup.value,
    content: inputText.value,
    timestamp: Date.now()
  }
  
  chatSocket.send(msg)
  
  // Optimistic update
  messages.value.push({ ...msg, isSelf: true })
  inputText.value = ''
  scrollToBottom()
}

// Receive Message
onMounted(() => {
  if (!currentUserId) {
    router.push('/login')
    return
  }

  // Ensure socket is connected if page refreshed
  chatSocket.connect(currentUserId)
  loadFriends()

  chatSocket.onMessageCallback = (msg) => {
    // Handle ONLINE Status updates
    if (msg.type === 'STATUS') {
        const friend = friends.value.find(f => f.id === msg.senderId)
        if (friend) {
            friend.isOnline = (msg.content === 'ONLINE')
        }
        return
    }

    // Only show if it matches current chat
    const isRelevant = (msg.isGroup && msg.targetId === targetId.value) || 
                       (!msg.isGroup && (msg.senderId === targetId.value || (msg.senderId === currentUserId && msg.targetId === targetId.value)))

    if (isRelevant) {
      if (!userCache.value[msg.senderId]) fetchUserInfo(msg.senderId)
      messages.value.push({
        ...msg,
        isSelf: msg.senderId === currentUserId
      })
      scrollToBottomIfNear()
    } else {
      // Store unread or notify
      if (msg.isGroup) {
          const key = 'g-' + msg.targetId
          unreadCounts.value[key] = (unreadCounts.value[key] || 0) + 1
      } else {
          // Private message
          const key = 'f-' + msg.senderId
          unreadCounts.value[key] = (unreadCounts.value[key] || 0) + 1
      }
      ElMessage.info(`来自 ${msg.senderId} 的新消息`)
    }
  }
})

// Scroll Logic
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

const scrollToBottomIfNear = () => {
  if (!chatContainer.value) return
  const { scrollTop, scrollHeight, clientHeight } = chatContainer.value
  // If we are close to bottom (within 100px)
  if (scrollHeight - scrollTop - clientHeight < 150) {
    scrollToBottom()
  } else {
    showScrollBtn.value = true
  }
}

const handleScroll = () => {
  if (!chatContainer.value) return
  const { scrollTop, scrollHeight, clientHeight } = chatContainer.value
  if (scrollHeight - scrollTop - clientHeight < 50) {
    showScrollBtn.value = false
  }
  // Load older messages when scrolled to top
  if (scrollTop <= 10 && hasMoreHistory.value && !isLoadingMore.value) {
    loadMoreHistory()
  }
}

// File Upload
const handleFileUpload = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  try {
    const res = await axios.post('/api/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const url = res.data.url
    
    // Send Image Message
    const msg: ChatMessage = {
       type: 'CHAT',
       senderId: currentUserId,
       targetId: targetId.value,
       isGroup: isGroup.value,
       content: url,
       timestamp: Date.now()
    }
    chatSocket.send(msg)
    messages.value.push({ ...msg, isSelf: true })
    scrollToBottom()
    
  } catch (err) {
    ElMessage.error('上传失败')
  }
}

// Resizing Logic
const sidebarWidth = ref(280) // default width
const isResizing = ref(false)
let startX = 0
let startWidth = 0

const startResize = (e: MouseEvent) => {
  isResizing.value = true
  startX = e.clientX
  startWidth = sidebarWidth.value
  
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
}

const handleResize = (e: MouseEvent) => {
  if (isResizing.value) {
    // Delta approach to handle offsets (like AppLayout w-16 sidebar)
    const delta = e.clientX - startX
    const newWidth = startWidth + delta
    
    // Min 200px, Max 600px
    if (newWidth > 200 && newWidth < 600) {
      sidebarWidth.value = newWidth
    }
  }
}

const stopResize = () => {
  isResizing.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
}

// Input Resizing Logic
const inputHeight = ref(150)
const isResizingInput = ref(false)

const startResizeInput = () => {
  isResizingInput.value = true
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleResizeInput)
  document.addEventListener('mouseup', stopResizeInput)
}

const handleResizeInput = (e: MouseEvent) => {
  if (isResizingInput.value) {
    const newHeight = window.innerHeight - e.clientY
    if (newHeight > 100 && newHeight < 500) {
      inputHeight.value = newHeight
    }
  }
}

const stopResizeInput = () => {
  isResizingInput.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', handleResizeInput)
  document.removeEventListener('mouseup', stopResizeInput)
}

const isSelectedUserFriend = computed(() => {
    if (!selectedFriend.value) return false
    return friends.value.some(f => f.id === selectedFriend.value.id)
})
</script>

<template>
  <div class="flex h-full w-full bg-white dark:bg-gray-900 transition-colors duration-300">
    <!-- Sidebar -->
    <div 
      class="border-r border-gray-200 dark:border-gray-700 flex flex-col flex-shrink-0 bg-white dark:bg-gray-800 transition-colors duration-300"
      :style="{ width: sidebarWidth + 'px' }"
    >
      <div class="p-4 bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 text-white flex justify-center items-center shadow-md">
        <span class="font-bold text-xl tracking-wider animate-pulse font-serif italic">Chill Chat</span>
      </div>
      
      <div class="px-3 pb-2 mt-2">
         <input 
            v-model="searchQuery" 
            placeholder="搜索好友..." 
            class="w-full bg-gray-100 dark:bg-gray-700 border-none rounded px-3 py-1.5 text-xs focus:ring-1 focus:ring-chill-blue outline-none text-gray-600 dark:text-gray-200 transaction-colors"
         />
      </div>
      
      <div class="overflow-y-auto flex-1">
        
        <!-- Groups Section -->
        <div>
          <div 
             @click="showGroups = !showGroups"
             class="px-4 py-2 bg-gray-100 dark:bg-gray-800 text-xs font-bold text-gray-500 dark:text-gray-400 uppercase cursor-pointer hover:bg-gray-200 dark:hover:bg-gray-700 select-none flex justify-between transition-colors"
          >
            <span>群组 ({{groups.length}})</span>
            <span>{{ showGroups ? '▼' : '▶' }}</span>
          </div>

          <div v-show="showGroups">
              <div 
                v-for="group in groups" 
                :key="'g-' + group.id"
                class="flex items-center p-3 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition"
                :class="(targetId === group.id && isGroup) ? 'bg-blue-50 dark:bg-indigo-900/30' : ''"
                @click="selectChat(group.id, true)"
              >
                <img :src="group.avatar" class="w-10 h-10 rounded-full mr-3 border border-gray-200 dark:border-gray-600" />
                <div class="flex-1">
                  <div class="flex justify-between items-center">
                    <div class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ group.name }}</div>
                    <!-- Group Unread Badge -->
                    <div 
                      v-if="unreadCounts['g-' + group.id] > 0"
                      class="bg-red-500 text-white text-[10px] h-4 min-w-[16px] px-1 rounded-full flex items-center justify-center font-bold"
                    >
                      {{ unreadCounts['g-' + group.id] > 99 ? '99+' : unreadCounts['g-' + group.id] }}
                    </div>
                  </div>
                  <div class="text-xs text-gray-400">
                    <span>群聊</span>
                  </div>
                </div>
              </div>
          </div>
        </div>

        <!-- Friends Section -->
        <div>
           <div 
              @click="showFriends = !showFriends"
              class="px-4 py-2 bg-gray-100 dark:bg-gray-800 text-xs font-bold text-gray-500 dark:text-gray-400 uppercase cursor-pointer hover:bg-gray-200 dark:hover:bg-gray-700 select-none flex justify-between transition-colors"
           >
              <span>好友列表 ({{friends.length}})</span>
              <span>{{ showFriends ? '▼' : '▶' }}</span>
           </div>
           
           <div v-show="showFriends">
            <div 
              v-for="friend in filteredFriends" 
              :key="'f-' + friend.id"
              class="flex items-center p-3 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition"
              :class="(targetId === friend.id && !isGroup) ? 'bg-blue-50 dark:bg-indigo-900/30' : ''"
              @click="selectChat(friend.id, false)"
            >
              <div class="relative mr-3">
                  <img 
                    :src="friend.avatar" 
                    @click.stop="openFriendInfo(friend)"
                    class="w-10 h-10 rounded-full hover:opacity-80 transition" 
                  />
                  <!-- Online Status -->
                  <div 
                    v-if="friend.isOnline"
                    class="absolute bottom-0 right-0 w-3 h-3 bg-green-500 rounded-full border-2 border-white dark:border-gray-800"
                  ></div>
                  <div 
                    v-else
                    class="absolute bottom-0 right-0 w-3 h-3 bg-gray-400 rounded-full border-2 border-white dark:border-gray-800"
                  ></div>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex justify-between items-center">
                    <div class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ friend.name }}</div>
                    <!-- Unread Badge -->
                    <div 
                        v-if="unreadCounts['f-' + friend.id] > 0"
                        class="bg-red-500 text-white text-[10px] h-4 min-w-[16px] px-1 rounded-full flex items-center justify-center font-bold"
                    >
                        {{ unreadCounts['f-' + friend.id] > 99 ? '99+' : unreadCounts['f-' + friend.id] }}
                    </div>
                </div>
                <div class="text-xs text-gray-400 dark:text-gray-500 truncate w-32">{{ friend.signature }}</div>
              </div>
            </div>
          </div>
        </div>

      </div>
      
      <div class="p-3 border-t space-y-2 border-gray-200 dark:border-gray-700">
         <!-- Create Group Button -->
         <button 
           @click="showCreateGroupModal = true"
           class="w-full bg-indigo-50 dark:bg-gray-800 text-indigo-600 dark:text-indigo-400 py-1.5 rounded text-sm hover:bg-indigo-100 dark:hover:bg-gray-700 transition border border-indigo-200 dark:border-gray-600"
         >
           + 新建群组
         </button>

         <button 
           v-if="pendingRequests.length > 0"
           @click="showRequestsModal = true"
           class="w-full bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400 py-1.5 rounded text-sm hover:bg-orange-200 dark:hover:bg-orange-900/50 transition flex justify-center items-center"
         >
           好友请求 <span class="ml-2 bg-orange-600 text-white rounded-full px-2 text-xs">{{pendingRequests.length}}</span>
         </button>

         <!-- Add Friend -->
         <button 
           @click="showAddFriendModal = true"
           class="w-full bg-chill-blue text-white py-1.5 rounded text-sm hover:bg-blue-600 transition"
         >
           + 添加好友
         </button>
      </div>
    </div>

    <!-- Resizer Handle -->
    <div
        class="w-1 hover:bg-blue-400 cursor-col-resize bg-gray-50 dark:bg-gray-700 hover:shadow-lg transition-colors flex-shrink-0 z-20 flex items-center justify-center group"
        :class="isResizing ? 'bg-blue-500' : ''"
        @mousedown.prevent="startResize"
    >
       <div class="h-8 w-0.5 bg-gray-300 dark:bg-gray-500 group-hover:bg-white rounded"></div>
    </div>

    <!-- Chat Area -->
    <div class="flex-1 flex flex-col relative w-0 overflow-hidden bg-gradient-to-br from-slate-50 dark:from-gray-900 via-indigo-50/30 dark:via-indigo-900/10 to-purple-50/30 dark:to-purple-900/10 transition-colors duration-300">
      <!-- Background Blobs -->
      <div class="absolute top-20 left-20 w-80 h-80 bg-indigo-400 rounded-full filter blur-[80px] opacity-25 animate-blob pointer-events-none"></div>
      <div class="absolute bottom-20 right-20 w-80 h-80 bg-purple-400 rounded-full filter blur-[80px] opacity-25 animate-blob animation-delay-2000 pointer-events-none"></div>

      <!-- Header -->
      <div class="h-14 border-b border-white/40 dark:border-gray-700/50 bg-white/70 dark:bg-gray-800/80 backdrop-blur-md flex items-center px-4 justify-between shadow-sm z-10 relative transition-colors duration-300">
        <span class="font-bold text-gray-800 dark:text-gray-100 tracking-wide">
          {{ targetId ? (isGroup ? (groups.find(g => g.id === targetId)?.name || '群组 ' + targetId) : (userCache[targetId]?.name || '用户 ' + targetId)) : '选择一个聊天' }}
        </span>
        <!-- Group management button -->
        <button
          v-if="isGroup && targetId"
          @click="openGroupInfo(targetId)"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition"
          title="群组详情"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a4 4 0 00-5-3.87M9 20H4v-2a4 4 0 015-3.87m6-4.13a4 4 0 11-8 0 4 4 0 018 0zm6 0a4 4 0 11-2 0" />
          </svg>
          群组详情
        </button>
      </div>

      <!-- Messages -->
      <div 
        ref="chatContainer"
        @scroll="handleScroll"
        class="flex-1 overflow-y-auto p-4 space-y-4 bg-transparent relative z-0 scroll-smooth" 
      >
        <div v-if="!targetId" class="flex flex-col items-center justify-center h-full text-gray-400 font-light">
           <div class="text-6xl mb-4 opacity-30 animate-pulse">💬</div>
           <p>选择好友开始聊天</p>
        </div>

        <!-- Load More Indicator -->
        <div v-if="isLoadingMore" class="flex justify-center py-2">
          <span class="text-xs text-gray-400 animate-pulse">加载更多消息...</span>
        </div>
        <div v-else-if="targetId && !hasMoreHistory && messages.length > 0" class="flex justify-center py-1">
          <span class="text-xs text-gray-300 dark:text-gray-600">— 已无更多消息 —</span>
        </div>
        
        <MessageBubble 
          v-for="(m, idx) in messages" 
          :key="idx" 
          :msg="m"
          :avatar="userCache[m.senderId]?.avatar"
          :senderName="isGroup && !m.isSelf ? (userCache[m.senderId]?.name || ('用户 ' + m.senderId)) : undefined"
          @click-avatar="handleAvatarClick"
          @view-image="openPreview"
        />
      </div>

      <!-- New Message Hint -->
      <div 
        v-if="showScrollBtn"
        @click="scrollToBottom"
        class="absolute bottom-20 right-8 bg-chill-blue text-white px-3 py-1 rounded-full shadow-lg cursor-pointer text-sm animate-bounce"
      >
        新消息 ↓
      </div>

      <!-- Create Group Modal -->
    <div v-if="showCreateGroupModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white p-6 rounded-lg w-96 shadow-xl flex flex-col max-h-[80vh]">
          <h3 class="text-lg font-bold mb-4">创建新群组</h3>
          
          <div class="mb-4">
            <label class="block text-xs font-bold text-gray-500 mb-1">群组名称</label>
            <input 
                v-model="newGroupName" 
                type="text" 
                placeholder="例如：周末派对"
                class="w-full border p-2 rounded focus:border-chill-blue outline-none"
            />
          </div>

          <div class="mb-2">
            <label class="block text-xs font-bold text-gray-500 mb-1">选择成员</label>
            <div class="border rounded h-48 overflow-y-auto p-2 space-y-1">
                <div 
                    v-for="f in friends" 
                    :key="f.id"
                    @click="toggleFriendSelection(f.id)"
                    class="flex items-center p-2 rounded cursor-pointer hover:bg-gray-50"
                    :class="selectedFriendsForGroup.includes(f.id) ? 'bg-blue-50 border-blue-200' : ''"
                >
                    <div class="w-4 h-4 border mr-3 rounded flex items-center justify-center"
                        :class="selectedFriendsForGroup.includes(f.id) ? 'bg-chill-blue border-chill-blue' : 'border-gray-300'"
                    >
                        <span v-if="selectedFriendsForGroup.includes(f.id)" class="text-white text-xs">✓</span>
                    </div>
                    <img :src="f.avatar" class="w-6 h-6 rounded-full mr-2"/>
                    <span class="text-sm">{{f.name}}</span>
                </div>
            </div>
            <div class="text-xs text-right text-gray-400 mt-1">
                Selected: {{selectedFriendsForGroup.length}}
            </div>
          </div>

          <div class="flex justify-end space-x-2 mt-4">
             <button @click="showCreateGroupModal = false" class="text-gray-500 px-3 py-1">Cancel</button>
             <button @click="createGroup" class="bg-chill-blue text-white px-4 py-1.5 rounded">Create Group</button>
          </div>
       </div>
    </div>

    <!-- Input Resize Handle -->
     <div 
        v-if="targetId"
        class="h-1 bg-gray-50 dark:bg-gray-800 hover:bg-blue-400 cursor-row-resize hover:shadow-lg transition-colors z-20 flex items-center justify-center group border-t border-gray-200 dark:border-gray-700"
         :class="isResizingInput ? 'bg-blue-500' : ''"
        @mousedown.prevent="startResizeInput"
      >
        <div class="w-8 h-0.5 bg-gray-300 dark:bg-gray-500 group-hover:bg-white rounded"></div>
      </div>

    <!-- - Input Area -->
      <div 
        v-if="targetId" 
        class="bg-white dark:bg-gray-800 p-3 flex flex-col transition-colors duration-300"
        :style="{ height: inputHeight + 'px' }"
      >
        <!-- Toolbar -->
        <div class="flex space-x-3 mb-2 px-1">
          <label class="cursor-pointer text-gray-500 dark:text-gray-400 hover:text-chill-blue transition">
            <input type="file" class="hidden" accept="image/*" @change="handleFileUpload" />
            <el-icon :size="20"><Picture /></el-icon>
          </label>
        </div>
        
        <textarea 
          v-model="inputText"
          @keydown.ctrl.enter="sendMessage"
          class="flex-1 resize-none outline-none text-sm bg-transparent text-gray-800 dark:text-gray-200 placeholder-gray-400 dark:placeholder-gray-500"
          placeholder="输入消息 (Ctrl+Enter 发送)..."
        ></textarea>
        
        <div class="flex justify-end">
          <button 
            @click="sendMessage"
            class="bg-chill-blue text-white px-6 py-1.5 rounded-md text-sm hover:bg-blue-600 transition"
          >
            发送
          </button>
        </div>
      </div>
    </div>

    <!-- Add Friend Modal -->
    <div v-if="showAddFriendModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white p-6 rounded-lg w-80 shadow-xl">
          <h3 class="text-lg font-bold mb-4">添加好友</h3>
          <input 
            v-model="searchFriendId" 
            type="number" 
            placeholder="输入用户 ID (如 1001)"
            class="w-full border p-2 rounded mb-2 focus:border-chill-blue outline-none"
          />
          <textarea 
            v-model="requestReason" 
            placeholder="申请理由 (可选)"
            class="w-full border p-2 rounded mb-4 focus:border-chill-blue outline-none h-20 resize-none text-sm"
          ></textarea>
          <div class="flex justify-end space-x-2">
             <button @click="showAddFriendModal = false" class="text-gray-500 px-3 py-1">取消</button>
             <button @click="sendFriendRequest" class="bg-chill-blue text-white px-3 py-1 rounded">发送请求</button>
          </div>
       </div>
    </div>

    <!-- Requests Modal -->
    <div v-if="showRequestsModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white p-6 rounded-lg w-96 shadow-xl max-h-[80vh] flex flex-col">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">新的好友请求</h3>
            <button @click="showRequestsModal = false" class="text-gray-400 hover:text-gray-600">✕</button>
          </div>
          
          <div class="flex-1 overflow-y-auto space-y-3">
             <div v-if="pendingRequests.length === 0" class="text-gray-400 text-center py-4">暂无请求</div>
             <div v-for="req in pendingRequests" :key="req.requestId" class="flex items-start space-x-3 p-3 bg-gray-50 rounded">
                <img :src="req.avatar" class="w-10 h-10 rounded-full bg-white"/>
                <div class="flex-1">
                   <div class="font-bold text-sm">{{req.name}}</div>
                   <div class="text-xs text-gray-500 mb-2">{{req.reason || '未提供理由'}}</div>
                   <div class="flex space-x-2">
                      <button @click="handleRequest(req.requestId, 'ACCEPTED')" class="bg-green-500 text-white px-3 py-1 rounded text-xs hover:bg-green-600">接受</button>
                      <button @click="handleRequest(req.requestId, 'REJECTED')" class="bg-red-400 text-white px-3 py-1 rounded text-xs hover:bg-red-500">拒绝</button>
                   </div>
                </div>
             </div>
          </div>
       </div>
    </div>

    <!-- Friend Info Modal -->
    <div v-if="showFriendInfoModal && selectedFriend" class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center">
       <div class="bg-white rounded-xl w-80 shadow-2xl overflow-hidden relative animate-fade-in">
          <div class="h-24 bg-gradient-to-r from-indigo-500 to-purple-600"></div>
          <button @click="showFriendInfoModal = false" class="absolute top-2 right-2 text-white hover:opacity-80 transition hover:rotate-90">✕</button>
          
          <div class="px-6 pb-6 -mt-10 flex flex-col items-center">
             <img :src="selectedFriend.avatar" class="w-20 h-20 rounded-full border-4 border-white bg-white mb-2" />
             <div class="flex items-center justify-center space-x-1 mb-1">
                <h3 class="font-bold text-xl">{{ selectedFriend.name }}</h3>
                <el-icon v-if="selectedFriend.gender === 1" class="text-blue-500 bg-blue-100 rounded-full p-0.5"><Male /></el-icon>
                <el-icon v-if="selectedFriend.gender === 2" class="text-pink-500 bg-pink-100 rounded-full p-0.5"><Female /></el-icon>
             </div>
             <div class="text-gray-400 text-xs mb-2">ID: {{ selectedFriend.id }}</div>
             
             <div class="flex items-center space-x-2 text-xs text-gray-500 mb-4 bg-gray-50 px-3 py-1 rounded-full border border-gray-100" v-if="selectedFriend.birthday || selectedFriend.location">
                 <span v-if="selectedFriend.birthday">🎂 {{ getAge(selectedFriend.birthday) }}岁</span>
                 <span v-if="selectedFriend.birthday && selectedFriend.location" class="border-l border-gray-300 h-3 mx-1"></span>
                 <span v-if="selectedFriend.location">📍 {{ selectedFriend.location }}</span>
             </div>

             <div class="w-full bg-gray-50 p-3 rounded mb-6 text-center text-sm text-gray-600 italic border border-dashed border-gray-200">
                "{{ selectedFriend.signature || '这个人很懒，什么都没写。' }}"
             </div>

             <div class="w-full flex space-x-2">
                <button @click="showFriendInfoModal = false" class="flex-1 border border-gray-300 py-1.5 rounded text-gray-600 hover:bg-gray-50">关闭</button>
                <button v-if="isSelectedUserFriend" @click="deleteFriend" class="flex-1 bg-red-50 text-red-500 border border-red-100 py-1.5 rounded hover:bg-red-100">删除好友</button>
             </div>
          </div>
       </div>
    </div>

    <!-- Group Info Modal -->
    <div v-if="showGroupInfoModal && currentGroupInfo" class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center">
      <div class="bg-white dark:bg-gray-800 rounded-xl w-96 shadow-2xl overflow-hidden relative animate-fade-in max-h-[85vh] flex flex-col">
        <div class="h-20 bg-gradient-to-r from-indigo-500 to-purple-600 flex items-center px-5">
          <img :src="currentGroupInfo.avatar" class="w-12 h-12 rounded-full border-2 border-white mr-3" />
          <div class="flex-1 min-w-0">
            <div class="font-bold text-white text-base truncate">{{ currentGroupInfo.name }}</div>
            <div class="text-indigo-200 text-xs">{{ currentGroupMembers.length }} 位成员</div>
          </div>
          <button @click="showGroupInfoModal = false" class="text-white hover:opacity-70 ml-2 text-xl">✕</button>
        </div>

        <!-- Owner: rename group -->
        <div v-if="myRoleInCurrentGroup === 'owner'" class="px-4 pt-3 flex space-x-2">
          <input 
            v-model="editingGroupName"
            class="flex-1 border rounded px-2 py-1 text-sm dark:bg-gray-700 dark:border-gray-600 dark:text-white outline-none focus:border-indigo-400"
            placeholder="修改群名..."
          />
          <button @click="updateGroupName(currentGroupInfo.id)" class="bg-indigo-500 text-white px-3 py-1 rounded text-sm hover:bg-indigo-600">
            保存
          </button>
        </div>

        <!-- Members List -->
        <div class="flex-1 overflow-y-auto px-4 py-3 space-y-2">
          <div class="text-xs font-bold text-gray-400 dark:text-gray-500 uppercase mb-1">成员列表</div>
          <div v-for="member in currentGroupMembers" :key="member.userId" class="flex items-center space-x-3 p-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
            <img :src="member.avatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${member.userId}`" class="w-9 h-9 rounded-full" />
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium text-gray-800 dark:text-gray-200 flex items-center space-x-1">
                <span class="truncate">{{ member.username }}</span>
                <span v-if="member.role === 'owner'" class="text-[10px] bg-yellow-100 text-yellow-700 dark:bg-yellow-900/50 dark:text-yellow-400 px-1.5 py-0.5 rounded-full">群主</span>
                <span v-else-if="member.role === 'admin'" class="text-[10px] bg-blue-100 text-blue-600 dark:bg-blue-900/50 dark:text-blue-400 px-1.5 py-0.5 rounded-full">管理员</span>
              </div>
              <div class="text-xs text-gray-400">ID: {{ member.userId }}</div>
            </div>
            <div class="flex items-center gap-1">
              <!-- Set/unset admin: only owner can do this, for non-owner members -->
              <button
                v-if="myRoleInCurrentGroup === 'owner' && member.userId !== currentUserId"
                @click="setAdmin(currentGroupInfo.id, member.userId)"
                class="text-blue-400 hover:text-blue-600 text-xs px-2 py-1 rounded hover:bg-blue-50 dark:hover:bg-blue-900/30 transition"
              >{{ member.role === 'admin' ? '撤销管理' : '设为管理' }}</button>
              <!-- Kick: owner can kick anyone, admin can kick regular members only -->
              <button
                v-if="(myRoleInCurrentGroup === 'owner' && member.userId !== currentUserId) || (myRoleInCurrentGroup === 'admin' && member.role === 'member')"
                @click="kickMember(currentGroupInfo.id, member.userId)"
                class="text-red-400 hover:text-red-600 text-xs px-2 py-1 rounded hover:bg-red-50 dark:hover:bg-red-900/30 transition"
              >移出</button>
            </div>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="px-4 py-3 border-t border-gray-100 dark:border-gray-700 flex flex-wrap gap-2">
          <button @click="showGroupInfoModal = false" class="flex-1 border border-gray-300 dark:border-gray-600 py-1.5 rounded text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700 text-sm">关闭</button>
          <!-- Owner or admin: invite friends -->
          <button
            v-if="myRoleInCurrentGroup === 'owner' || myRoleInCurrentGroup === 'admin'"
            @click="showInviteModal = true"
            class="flex-1 bg-indigo-50 text-indigo-600 border border-indigo-200 py-1.5 rounded hover:bg-indigo-100 text-sm dark:bg-indigo-900/30 dark:border-indigo-700 dark:text-indigo-300"
          >邀请好友</button>
          <!-- Non-owner: quit button -->
          <button 
            v-if="myRoleInCurrentGroup !== 'owner'"
            @click="quitGroup(currentGroupInfo.id)"
            class="flex-1 bg-orange-50 text-orange-500 border border-orange-200 py-1.5 rounded hover:bg-orange-100 text-sm"
          >退出群聊</button>
          <!-- Owner: disband button -->
          <button 
            v-if="myRoleInCurrentGroup === 'owner'"
            @click="disbandGroup(currentGroupInfo.id)"
            class="flex-1 bg-red-50 text-red-500 border border-red-100 py-1.5 rounded hover:bg-red-100 text-sm"
          >解散群聊</button>
        </div>
      </div>
    </div>

    <!-- Invite Friends Sub-Modal -->
    <div v-if="showInviteModal" class="fixed inset-0 bg-black/60 backdrop-blur-sm z-[60] flex items-center justify-center">
      <div class="bg-white dark:bg-gray-800 rounded-xl w-80 shadow-2xl overflow-hidden animate-fade-in max-h-[70vh] flex flex-col">
        <div class="h-12 bg-gradient-to-r from-indigo-500 to-purple-600 flex items-center px-4 justify-between">
          <span class="font-bold text-white text-sm">邀请好友进群</span>
          <button @click="showInviteModal = false; selectedInviteIds = []" class="text-white hover:opacity-70 text-xl">✕</button>
        </div>
        <div class="flex-1 overflow-y-auto px-3 py-2 space-y-1">
          <div v-if="friendsNotInGroup.length === 0" class="text-sm text-gray-400 text-center py-4">所有好友已在群内</div>
          <label
            v-for="f in friendsNotInGroup" :key="f.id"
            class="flex items-center space-x-3 p-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
          >
            <input type="checkbox" :value="f.id" v-model="selectedInviteIds" class="accent-indigo-500" />
            <img :src="f.avatar" class="w-8 h-8 rounded-full" />
            <span class="text-sm text-gray-800 dark:text-gray-200">{{ f.name }}</span>
          </label>
        </div>
        <div class="px-3 py-3 border-t border-gray-100 dark:border-gray-700 flex space-x-2">
          <button @click="showInviteModal = false; selectedInviteIds = []" class="flex-1 border border-gray-300 dark:border-gray-600 py-1.5 rounded text-gray-600 dark:text-gray-400 hover:bg-gray-50 text-sm">取消</button>
          <button @click="inviteFriendsToGroup" :disabled="selectedInviteIds.length === 0" class="flex-1 bg-indigo-500 text-white py-1.5 rounded hover:bg-indigo-600 text-sm disabled:opacity-40">邀请 {{ selectedInviteIds.length > 0 ? `(${selectedInviteIds.length})` : '' }}</button>
        </div>
      </div>
    </div>

    <!-- Image Preview Modal -->
    <div v-if="showImagePreview" class="fixed inset-0 bg-black/80 backdrop-blur-sm z-[100] flex items-center justify-center cursor-zoom-out" @click="showImagePreview = false">
        <img :src="previewImage" class="max-w-[90vw] max-h-[90vh] object-contain shadow-2xl rounded-lg animate-fade-in" @click.stop />
        <button class="absolute top-4 right-4 text-white text-4xl hover:text-gray-300 transform hover:rotate-90 transition p-4">×</button>
    </div>
  </div>
</template>

<style scoped>
/* Custom Scrollbar for Chat */
::-webkit-scrollbar {
  width: 6px;
}
::-webkit-scrollbar-thumb {
  background: #ccc; 
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: #999;
}
::-webkit-scrollbar-track {
  background: transparent; 
}

/* Animations */
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
@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
.animate-fade-in {
  animation: fadeIn 0.2s ease-out forwards;
}
</style>
