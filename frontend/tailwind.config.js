/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'chill-blue': '#409EFF',
        'chill-bg': '#F5F7FA',
      }
    },
  },
  plugins: [],
}
