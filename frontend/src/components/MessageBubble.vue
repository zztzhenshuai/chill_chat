<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  msg: {
    senderId: number
    content: string
    timestamp: number
    isSelf: boolean
    type?: string // 'text' | 'image'
  }
}>()

const timeSafe = computed(() => new Date(props.msg.timestamp).toLocaleTimeString())
</script>

<template>
  <div class="flex mb-4" :class="msg.isSelf ? 'flex-row-reverse' : 'flex-row'">
    <!-- Avatar -->
    <div class="flex-shrink-0 mx-3">
      <div class="w-10 h-10 rounded-full bg-gray-300 overflow-hidden">
        <img 
          v-lazy="'https://api.dicebear.com/7.x/avataaars/svg?seed=' + msg.senderId" 
          class="w-full h-full object-cover"
          alt="Avatar"
        />
      </div>
    </div>

    <!-- Bubble -->
    <div 
      class="max-w-[70%] rounded-lg p-3 shadow-sm relative text-sm"
      :class="msg.isSelf ? 'bg-chill-blue text-white rounded-tr-none' : 'bg-white text-gray-800 rounded-tl-none'"
    >
      <div v-if="msg.content.startsWith('http')" class="image-content">
         <img v-lazy="msg.content" class="max-w-full rounded-md cursor-pointer" />
      </div>
      <div v-else class="whitespace-pre-wrap break-words">{{ msg.content }}</div>
      
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
