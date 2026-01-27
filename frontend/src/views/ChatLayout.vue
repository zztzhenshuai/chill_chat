<script setup lang="ts">
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { chatSocket, type ChatMessage } from '@/services/websocket'
import MessageBubble from '@/components/MessageBubble.vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Plus, Picture } from '@element-plus/icons-vue'

const router = useRouter()
const currentUserId = Number(localStorage.getItem('chill_user_id') || 0)
const targetId = ref<number>(0) // The person we are talking to
const isGroup = ref(false)

const inputText = ref('')
const messages = ref<any[]>([])
const chatContainer = ref<HTMLElement | null>(null)
const showScrollBtn = ref(false)

const showAddFriendModal = ref(false)
const showRequestsModal = ref(false)
const showFriendInfoModal = ref(false)
const selectedFriend = ref<any>(null)

const searchFriendId = ref('')
const requestReason = ref('')
const pendingRequests = ref<any[]>([])

// Real Friend List
const friends = ref<any[]>([])
const groups = ref<any[]>([])
const showFriends = ref(true)
const showGroups = ref(true)

// Group Creation
const showCreateGroupModal = ref(false)
const newGroupName = ref('')
const selectedFriendsForGroup = ref<number[]>([])

const loadGroups = async () => {
    try {
        const res = await axios.get('/api/groups/my', { params: { userId: currentUserId } })
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
        ElMessage.warning('Please name the group and select at least one friend')
        return
    }
    
    try {
        await axios.post('/api/groups/create', selectedFriendsForGroup.value, { 
            params: { 
                ownerId: currentUserId,
                groupName: newGroupName.value
            }
        })
        ElMessage.success('Group Created!')
        showCreateGroupModal.value = false
        newGroupName.value = ''
        selectedFriendsForGroup.value = []
        loadGroups()
    } catch(err) {
        ElMessage.error('Failed to create group')
    }
}

const loadFriends = async () => {
  // Load friends
  try {
    const res = await axios.get('/api/friends', { params: { userId: currentUserId } })
    friends.value = res.data.map((f: any) => ({
      id: f.friendId,
      name: f.friendName,
      avatar: f.friendAvatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${f.friendId}`,
      signature: f.friendSignature || 'Just chilling...',
      isGroup: false
    }))
  } catch (err) { console.error(err) }

  // Load requests
  try {
      const resReq = await axios.get('/api/friends/requests', { params: { userId: currentUserId }})
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
        userId: currentUserId,
        friendId: searchFriendId.value,
        reason: requestReason.value || 'Hi, let\'s be friends!'
      }
    })
    ElMessage.success('Request Sent!')
    showAddFriendModal.value = false
    searchFriendId.value = ''
    requestReason.value = ''
  } catch (err: any) {
    ElMessage.error(err.response?.data || 'Failed to send')
  }
}

const handleRequest = async (requestId: number, status: string) => {
    try {
        await axios.post('/api/friends/respond', null, {
            params: { requestId, status }
        })
        ElMessage.success(status === 'ACCEPTED' ? 'Friend Added!' : 'Rejected')
        loadFriends() // Refresh list
    } catch(err) { ElMessage.error('Action failed') }
}

const openFriendInfo = (friend: any) => {
    selectedFriend.value = friend
    showFriendInfoModal.value = true
}

const deleteFriend = async () => {
    if(!selectedFriend.value) return
    try {
        await axios.delete('/api/friends/delete', {
            params: { userId: currentUserId, friendId: selectedFriend.value.id }
        })
        ElMessage.success('Friend deleted')
        showFriendInfoModal.value = false
        if (targetId.value === selectedFriend.value.id) {
            targetId.value = 0
            messages.value = []
        }
        loadFriends()
    } catch(err) { ElMessage.error('Failed to delete') }
}

const filteredFriends = computed(() => {
  return friends.value
})

// Select Chat
const selectChat = async (id: number, group: boolean) => {
  if (id === targetId.value) return // Don't reload if clicking same chat
  
  targetId.value = id
  isGroup.value = group
  messages.value = [] // Setup UI for loading

  await loadHistory()
}

const loadHistory = async () => {
  if (!targetId.value) return
  try {
    const res = await axios.get('/api/messages/history', {
      params: {
        currentId: currentUserId,
        targetId: targetId.value,
        isGroup: isGroup.value
      }
    })
    // Map backend entity to frontend format
    messages.value = res.data.map((m: any) => ({
      type: 'CHAT',
      id: m.id,
      senderId: m.senderId,
      targetId: m.targetId,
      isGroup: m.isGroup,
      content: m.content,
      timestamp: new Date(m.createTime).getTime(),
      isSelf: m.senderId === currentUserId
    }))
    scrollToBottom()
  } catch (err) {
    console.error('Failed to load history', err)
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
    // Only show if it matches current chat
    const isRelevant = (msg.isGroup && msg.targetId === targetId.value) || 
                       (!msg.isGroup && (msg.senderId === targetId.value || (msg.senderId === currentUserId && msg.targetId === targetId.value)))

    if (isRelevant) {
      messages.value.push({
        ...msg,
        isSelf: msg.senderId === currentUserId
      })
      scrollToBottomIfNear()
    } else {
      // Store unread or notify
      ElMessage.info(`New message from ${msg.senderId}`)
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
    ElMessage.error('Upload failed')
  }
}

const logout = () => {
    // Clear storage
    localStorage.removeItem('chill_user_id')
    // Close socket
    chatSocket.disconnect()
    // Redirect
    router.push('/login')
    ElMessage.success('Logged out successfully')
}
</script>

<template>
  <div class="flex h-full w-full bg-white">
    <!-- Sidebar -->
    <div class="w-64 border-r border-gray-200 flex flex-col">
      <div class="p-4 bg-chill-blue text-white flex justify-between items-center shadow-sm">
        <span class="font-bold text-lg">Chill Chat</span>
        <div class="flex flex-col items-end">
             <span class="text-xs opacity-80 mb-1">ID: {{ currentUserId }}</span>
             <button 
                @click="logout" 
                class="text-[10px] bg-white text-chill-blue px-2 py-0.5 rounded hover:bg-gray-100 transition font-bold"
             >
                LOGOUT
             </button>
        </div>
      </div>
      
      <div class="overflow-y-auto flex-1">
        
        <!-- Groups Section -->
        <div>
          <div 
             @click="showGroups = !showGroups"
             class="px-4 py-2 bg-gray-100 text-xs font-bold text-gray-500 uppercase cursor-pointer hover:bg-gray-200 select-none flex justify-between"
          >
            <span>Groups ({{groups.length}})</span>
            <span>{{ showGroups ? '▼' : '▶' }}</span>
          </div>

          <div v-show="showGroups">
              <div 
                v-for="group in groups" 
                :key="'g-' + group.id"
                class="flex items-center p-3 hover:bg-gray-50 cursor-pointer transition"
                :class="(targetId === group.id && isGroup) ? 'bg-blue-50' : ''"
                @click="selectChat(group.id, true)"
              >
                <img :src="group.avatar" class="w-10 h-10 rounded-full mr-3 border border-gray-200" />
                <div>
                  <div class="text-sm font-medium text-gray-800">{{ group.name }}</div>
                  <div class="text-xs text-gray-400">Group Chat</div>
                </div>
              </div>
          </div>
        </div>

        <!-- Friends Section -->
        <div>
           <div 
              @click="showFriends = !showFriends"
              class="px-4 py-2 bg-gray-100 text-xs font-bold text-gray-500 uppercase cursor-pointer hover:bg-gray-200 select-none flex justify-between"
           >
              <span>Friends ({{friends.length}})</span>
              <span>{{ showFriends ? '▼' : '▶' }}</span>
           </div>
           
           <div v-show="showFriends">
            <div 
              v-for="friend in filteredFriends" 
              :key="'f-' + friend.id"
              class="flex items-center p-3 hover:bg-gray-50 cursor-pointer transition"
              :class="(targetId === friend.id && !isGroup) ? 'bg-blue-50' : ''"
              @click="selectChat(friend.id, false)"
            >
              <img 
                :src="friend.avatar" 
                @click.stop="openFriendInfo(friend)"
                class="w-10 h-10 rounded-full mr-3 hover:opacity-80 transition" 
              />
              <div>
                <div class="text-sm font-medium text-gray-800">{{ friend.name }}</div>
                <div class="text-xs text-gray-400 truncate w-32">{{ friend.signature }}</div>
              </div>
            </div>
          </div>
        </div>

      </div>
      
      <div class="p-3 border-t space-y-2">
         <!-- Create Group Button -->
         <button 
           @click="showCreateGroupModal = true"
           class="w-full bg-indigo-50 text-indigo-600 py-1.5 rounded text-sm hover:bg-indigo-100 transition border border-indigo-200"
         >
           + New Group
         </button>

         <button 
           v-if="pendingRequests.length > 0"
           @click="showRequestsModal = true"
           class="w-full bg-orange-100 text-orange-600 py-1.5 rounded text-sm hover:bg-orange-200 transition flex justify-center items-center"
         >
           Friend Requests <span class="ml-2 bg-orange-600 text-white rounded-full px-2 text-xs">{{pendingRequests.length}}</span>
         </button>

         <!-- Add Friend -->
         <button 
           @click="showAddFriendModal = true"
           class="w-full bg-chill-blue text-white py-1.5 rounded text-sm hover:bg-blue-600 transition"
         >
           + Add Friend
         </button>
      </div>
    </div>

    <!-- Chat Area -->
    <div class="flex-1 flex flex-col relative">
      <!-- Header -->
      <div class="h-14 border-b bg-white flex items-center px-4 justify-between shadow-sm z-10">
        <span class="font-bold text-gray-700">
          {{ targetId ? (isGroup ? 'Group ' + targetId : 'User ' + targetId) : 'Select a chat' }}
        </span>
      </div>

      <!-- Messages -->
      <div 
        ref="chatContainer"
        @scroll="handleScroll"
        class="flex-1 overflow-y-auto p-4 space-y-2 bg-[#F5F7FA]" 
        style="scroll-behavior: smooth;"
      >
        <div v-if="!targetId" class="text-center text-gray-400 mt-20">Select a friend to chill</div>
        
        <MessageBubble 
          v-for="(m, idx) in messages" 
          :key="idx" 
          :msg="m"
        />
      </div>

      <!-- New Message Hint -->
      <div 
        v-if="showScrollBtn"
        @click="scrollToBottom"
        class="absolute bottom-20 right-8 bg-chill-blue text-white px-3 py-1 rounded-full shadow-lg cursor-pointer text-sm animate-bounce"
      >
        New Messages ↓
      </div>

      <!-Create Group Modal -->
    <div v-if="showCreateGroupModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white p-6 rounded-lg w-96 shadow-xl flex flex-col max-h-[80vh]">
          <h3 class="text-lg font-bold mb-4">Create New Group</h3>
          
          <div class="mb-4">
            <label class="block text-xs font-bold text-gray-500 mb-1">GROUP NAME</label>
            <input 
                v-model="newGroupName" 
                type="text" 
                placeholder="Ex. Weekend Chillers"
                class="w-full border p-2 rounded focus:border-chill-blue outline-none"
            />
          </div>

          <div class="mb-2">
            <label class="block text-xs font-bold text-gray-500 mb-1">SELECT MEMBERS</label>
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

    <!-- - Input Area -->
      <div v-if="targetId" class="h-32 bg-white border-t p-3 flex flex-col">
        <!-- Toolbar -->
        <div class="flex space-x-3 mb-2 px-1">
          <label class="cursor-pointer text-gray-500 hover:text-chill-blue transition">
            <input type="file" class="hidden" accept="image/*" @change="handleFileUpload" />
            <el-icon :size="20"><Picture /></el-icon>
          </label>
        </div>
        
        <textarea 
          v-model="inputText"
          @keydown.ctrl.enter="sendMessage"
          class="flex-1 resize-none outline-none text-sm bg-transparent"
          placeholder="Type a message (Ctrl+Enter to send)..."
        ></textarea>
        
        <div class="flex justify-end">
          <button 
            @click="sendMessage"
            class="bg-chill-blue text-white px-6 py-1.5 rounded-md text-sm hover:bg-blue-600 transition"
          >
            Send
          </button>
        </div>
      </div>
    </div>

    <!-- Add Friend Modal -->
    <div v-if="showAddFriendModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white p-6 rounded-lg w-80 shadow-xl">
          <h3 class="text-lg font-bold mb-4">Add Friend</h3>
          <input 
            v-model="searchFriendId" 
            type="number" 
            placeholder="Enter User ID (e.g. 1001)"
            class="w-full border p-2 rounded mb-2 focus:border-chill-blue outline-none"
          />
          <textarea 
            v-model="requestReason" 
            placeholder="Reason (Optional)"
            class="w-full border p-2 rounded mb-4 focus:border-chill-blue outline-none h-20 resize-none text-sm"
          ></textarea>
          <div class="flex justify-end space-x-2">
             <button @click="showAddFriendModal = false" class="text-gray-500 px-3 py-1">Cancel</button>
             <button @click="sendFriendRequest" class="bg-chill-blue text-white px-3 py-1 rounded">Send Request</button>
          </div>
       </div>
    </div>

    <!-- Requests Modal -->
    <div v-if="showRequestsModal" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white p-6 rounded-lg w-96 shadow-xl max-h-[80vh] flex flex-col">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-bold">New Friend Requests</h3>
            <button @click="showRequestsModal = false" class="text-gray-400 hover:text-gray-600">✕</button>
          </div>
          
          <div class="flex-1 overflow-y-auto space-y-3">
             <div v-if="pendingRequests.length === 0" class="text-gray-400 text-center py-4">No pending requests</div>
             <div v-for="req in pendingRequests" :key="req.requestId" class="flex items-start space-x-3 p-3 bg-gray-50 rounded">
                <img :src="req.avatar" class="w-10 h-10 rounded-full bg-white"/>
                <div class="flex-1">
                   <div class="font-bold text-sm">{{req.name}}</div>
                   <div class="text-xs text-gray-500 mb-2">{{req.reason || 'No reason provided'}}</div>
                   <div class="flex space-x-2">
                      <button @click="handleRequest(req.requestId, 'ACCEPTED')" class="bg-green-500 text-white px-3 py-1 rounded text-xs hover:bg-green-600">Accept</button>
                      <button @click="handleRequest(req.requestId, 'REJECTED')" class="bg-red-400 text-white px-3 py-1 rounded text-xs hover:bg-red-500">Reject</button>
                   </div>
                </div>
             </div>
          </div>
       </div>
    </div>

    <!-- Friend Info Modal -->
    <div v-if="showFriendInfoModal && selectedFriend" class="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center">
       <div class="bg-white rounded-xl w-80 shadow-2xl overflow-hidden relative">
          <div class="h-24 bg-chill-blue"></div>
          <button @click="showFriendInfoModal = false" class="absolute top-2 right-2 text-white hover:opacity-80">✕</button>
          
          <div class="px-6 pb-6 -mt-10 flex flex-col items-center">
             <img :src="selectedFriend.avatar" class="w-20 h-20 rounded-full border-4 border-white bg-white mb-2" />
             <h3 class="font-bold text-xl">{{ selectedFriend.name }}</h3>
             <div class="text-gray-400 text-sm mb-4">ID: {{ selectedFriend.id }}</div>
             
             <div class="w-full bg-gray-50 p-3 rounded mb-6 text-center text-sm text-gray-600 italic">
                "{{ selectedFriend.signature || 'No signature yet.' }}"
             </div>

             <div class="w-full flex space-x-2">
                <button @click="showFriendInfoModal = false" class="flex-1 border border-gray-300 py-1.5 rounded text-gray-600 hover:bg-gray-50">Close</button>
                <button @click="deleteFriend" class="flex-1 bg-red-50 text-red-500 border border-red-100 py-1.5 rounded hover:bg-red-100">Delete Friend</button>
             </div>
          </div>
       </div>
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
::-webkit-scrollbar-track {
  background: transparent; 
}
</style>
