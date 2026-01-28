<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const startNow = () => {
    router.push('/login')
}
</script>

<template>
  <div class="h-screen w-full bg-gradient-to-br from-blue-50 to-indigo-100 flex flex-col justify-center items-center relative overflow-hidden">
    
    <!-- Navbar -->
    <nav class="fixed top-0 w-full h-16 z-50 flex items-center justify-between px-10 bg-white/30 backdrop-blur-md border-b border-white/20 shadow-sm">
        <div class="flex items-center space-x-2">
            <div class="w-8 h-8 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-lg flex items-center justify-center text-white font-bold shadow">CC</div>
            <span class="font-bold text-xl text-gray-700 tracking-tight font-serif italic">Chill Chat</span>
        </div>
        <button @click="startNow" class="px-6 py-2 bg-white/80 text-indigo-600 font-bold rounded-full hover:bg-white transition shadow-sm border border-indigo-100 text-sm">
            即刻登录
        </button>
    </nav>

    <!-- Background Decoration -->
    <div class="absolute top-20 left-20 w-32 h-32 bg-purple-200 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob"></div>
    <div class="absolute top-40 right-20 w-32 h-32 bg-yellow-200 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob animation-delay-2000"></div>
    <div class="absolute -bottom-8 left-40 w-32 h-32 bg-pink-200 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob animation-delay-4000"></div>

    <!-- Main Content -->
    <div class="z-10 text-center">
        <!-- Bouncing Text Title -->
        <h1 class="text-6xl md:text-8xl font-black text-chill-blue tracking-tighter mb-4 flex justify-center space-x-2">
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.1s">C</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.2s">h</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.3s">i</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.4s">l</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.5s">l</span>
            <span class="w-4"></span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.6s">C</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.7s">h</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.8s">a</span>
            <span class="animate-bounce-custom nav-item" style="animation-delay: 0.9s">t</span>
        </h1>
        
        <p class="text-xl text-gray-600 mb-12 font-light tracking-wide">
            连接 · 共鸣 · 放松
        </p>

        <!-- Start Button -->
        <button 
            @click="startNow"
            class="group relative px-8 py-3 bg-chill-blue text-white text-lg font-bold rounded-full overflow-hidden shadow-lg hover:shadow-2xl transition-all duration-300 hover:-translate-y-1"
        >
            <span class="relative z-10 flex items-center">
                立即开始
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                </svg>
            </span>
            <div class="absolute inset-0 bg-blue-600 transform scale-x-0 group-hover:scale-x-100 transition-transform origin-left duration-300"></div>
        </button>
    </div>

    <!-- Footer -->
    <div class="absolute bottom-5 text-gray-400 text-sm">
        © 2026 Chill Chat Inc.
    </div>
  </div>
</template>

<style scoped>
@keyframes bounce-custom {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-20px);
  }
}

.animate-bounce-custom {
  display: inline-block;
  animation: bounce-custom 2s infinite ease-in-out;
}

/* Custom shadow text */
h1 {
    text-shadow: 4px 4px 0px rgba(0,0,0,0.1);
}

/* Blob Animation */
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
