import type { Directive } from 'vue'

export const staggerDirective: Directive = {
  mounted(el: HTMLElement) {
    const children = el.children
    Array.from(children).forEach((child, index) => {
      ;(child as HTMLElement).style.setProperty('--index', String(index))
      child.classList.add('stagger-child')
    })
  }
}
