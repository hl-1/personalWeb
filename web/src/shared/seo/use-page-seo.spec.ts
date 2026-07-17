import { mount } from '@vue/test-utils'
import { defineComponent, type PropType } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import { usePageSeo, type PageSeo } from './use-page-seo'

const SeoHost = defineComponent({
  props: {
    seo: { type: Object as PropType<PageSeo>, required: true },
  },
  setup(props) {
    usePageSeo(() => props.seo)
    return () => null
  },
})

describe('usePageSeo', () => {
  afterEach(() => {
    document.head.querySelectorAll('[data-studystack-seo]').forEach((node) => node.remove())
    document.title = ''
    window.history.replaceState({}, '', '/')
  })

  it('updates title, description, canonical, and Open Graph values reactively', async () => {
    document.title = 'Original title'
    window.history.replaceState({}, '', '/blog/first-article')
    const seo = {
      title: 'First article',
      description: 'First description',
      canonicalPath: '/blog/first-article',
    }

    const wrapper = mount(SeoHost, { props: { seo } })

    expect(document.title).toBe('First article | StudyStack')
    expect(meta('description')).toBe('First description')
    expect(meta('og:title', 'property')).toBe('First article | StudyStack')
    expect(meta('og:description', 'property')).toBe('First description')
    expect(meta('og:url', 'property')).toBe(`${window.location.origin}/blog/first-article`)
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href'))
      .toBe(`${window.location.origin}/blog/first-article`)

    await wrapper.setProps({
      seo: {
        title: 'Second article',
        description: 'Second description',
        canonicalPath: '/blog/second-article',
      },
    })

    expect(document.title).toBe('Second article | StudyStack')
    expect(meta('description')).toBe('Second description')
    expect(document.head.querySelectorAll('meta[name="description"]')).toHaveLength(1)

    wrapper.unmount()
    expect(document.title).toBe('Original title')
    expect(document.head.querySelector('[data-studystack-seo]')).toBeNull()
  })

  it('uses the literal site title for the home page and omits empty descriptions', () => {
    const wrapper = mount(SeoHost, {
      props: { seo: { title: 'StudyStack', canonicalPath: '/' } },
    })

    expect(document.title).toBe('StudyStack')
    expect(document.head.querySelector('meta[name="description"]')).toBeNull()
    wrapper.unmount()
  })
})

function meta(value: string, attribute = 'name'): string | null | undefined {
  return document.head
    .querySelector(`meta[${attribute}="${value}"]`)
    ?.getAttribute('content')
}
