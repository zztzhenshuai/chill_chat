<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  msg: {
    senderId: number
    content: string
    timestamp: number
    isSelf: boolean
    type?: string // 'text' | 'image'
  },
  avatar?: string
}>()

const emit = defineEmits(['click-avatar', 'view-image'])

const timeSafe = computed(() => new Date(props.msg.timestamp).toLocaleTimeString())

const handleAvatarClick = () => {
  emit('click-avatar', props.msg.senderId)
}

const handleImageClick = () => {
  emit('view-image', props.msg.content)
}
</script>

<template>
  <div class="flex mb-4" :class="msg.isSelf ? 'flex-row-reverse' : 'flex-row'">
    <!-- Avatar -->
    <div class="flex-shrink-0 mx-3">
      <div 
        class="w-10 h-10 rounded-full bg-gray-300 overflow-hidden cursor-pointer hover:opacity-80 transition ring-2 ring-transparent hover:ring-indigo-300"
        @click="handleAvatarClick"
      >
        <img 
          :src="avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + msg.senderId" 
          class="w-full h-full object-cover"
          alt="Avatar"
        />
      </div>
    </div>

    <!-- Bubble -->
    <div 
      class="max-w-[70%] rounded-2xl p-3 shadow-sm relative text-sm backdrop-blur-sm"
      :class="msg.isSelf ? 'bg-gradient-to-br from-indigo-500 to-purple-500 text-white rounded-tr-none shadow-indigo-200' : 'bg-white/80 text-gray-800 rounded-tl-none border border-white/50'"
    >
      <div v-if="msg.content.startsWith('http')" class="image-content group">
         <img 
            v-lazy="msg.content" 
            class="max-w-[200px] max-h-[250px] rounded-lg cursor-zoom-in object-cover hover:brightness-110 transition shadow-sm" 
            @click="handleImageClick"
         />
      </div>
      <div v-else class="whitespace-pre-wrap break-words leading-relaxed">{{ msg.content }}</div>
      
      <!-- Time -->
      <div 
        class="text-xs mt-1 text-right opacity-70"
        :class="msg.isSelf ? 'text-blue-100' : 'text-gray-400'"
      >
        {{ timeSafe }}
      </div>
    </div>
  </div>
</template>
