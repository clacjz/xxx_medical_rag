import { onMounted, onUnmounted, ref } from 'vue'

export function useScrollReveal() {
  const observer = ref<IntersectionObserver | null>(null)

  onMounted(() => {
    observer.value = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('visible')
            observer.value?.unobserve(entry.target)
          }
        })
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
      }
    )
  })

  onUnmounted(() => {
    observer.value?.disconnect()
  })

  const observe = (el: HTMLElement) => {
    if (el && observer.value) {
      el.classList.add('reveal')
      observer.value.observe(el)
    }
  }

  return { observe }
}
