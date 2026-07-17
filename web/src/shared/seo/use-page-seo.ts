import {
  onScopeDispose,
  toValue,
  watchEffect,
  type MaybeRefOrGetter,
} from 'vue'

export interface PageSeo {
  title: string
  description?: string | null
  canonicalPath: string
}

interface HeadSnapshot {
  title: string
}

export function usePageSeo(seo: MaybeRefOrGetter<PageSeo>): void {
  const snapshot: HeadSnapshot = { title: document.title }

  watchEffect(() => {
    const value = toValue(seo)
    const title = value.title === 'StudyStack'
      ? 'StudyStack'
      : `${value.title} | StudyStack`
    const canonicalUrl = new URL(value.canonicalPath, window.location.origin).toString()

    document.title = title
    setMeta('name', 'description', value.description)
    setMeta('property', 'og:title', title)
    setMeta('property', 'og:description', value.description)
    setMeta('property', 'og:url', canonicalUrl)
    setCanonical(canonicalUrl)
  })

  onScopeDispose(() => {
    document.title = snapshot.title
    document.head.querySelectorAll('[data-studystack-seo]').forEach((node) => node.remove())
  })
}

function setMeta(
  attribute: 'name' | 'property',
  key: string,
  content: string | null | undefined,
): void {
  const selector = `meta[${attribute}="${key}"][data-studystack-seo]`
  const existing = document.head.querySelector<HTMLMetaElement>(selector)
  if (!content) {
    existing?.remove()
    return
  }
  const element = existing ?? document.createElement('meta')
  element.setAttribute(attribute, key)
  element.setAttribute('content', content)
  element.dataset.studystackSeo = 'true'
  if (!existing) {
    document.head.append(element)
  }
}

function setCanonical(href: string): void {
  const existing = document.head.querySelector<HTMLLinkElement>(
    'link[rel="canonical"][data-studystack-seo]',
  )
  const element = existing ?? document.createElement('link')
  element.rel = 'canonical'
  element.href = href
  element.dataset.studystackSeo = 'true'
  if (!existing) {
    document.head.append(element)
  }
}
