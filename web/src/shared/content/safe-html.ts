import { z } from 'zod'

const allowedTags = new Set([
  'a',
  'blockquote',
  'br',
  'code',
  'del',
  'em',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'li',
  'ol',
  'p',
  'pre',
  'strong',
  'table',
  'tbody',
  'td',
  'th',
  'thead',
  'tr',
  'ul',
])

function isAllowedHref(value: string): boolean {
  if (!value || value.includes('\\') || Array.from(value).some((character) =>
    character.charCodeAt(0) <= 0x20,
  )) return false

  const hasScheme = /^[a-z][a-z0-9+.-]*:/i.test(value)
  try {
    if (hasScheme) {
      const url = new URL(value)
      return url.protocol === 'https:' && Boolean(url.hostname) && !url.username && !url.password
    }
    if (value.startsWith('//') || !value.split(/[?#]/, 1)[0]) return false
    new URL(value, 'https://studystack.invalid/')
    return true
  } catch {
    return false
  }
}

function findUnsafeHtml(value: string): string | undefined {
  const document = new DOMParser().parseFromString(value, 'text/html')
  for (const element of document.querySelectorAll('*')) {
    const tag = element.tagName.toLowerCase()
    if (tag === 'html' || tag === 'head' || tag === 'body') {
      continue
    }
    if (!allowedTags.has(tag)) {
      return `tag ${tag} is not allowed`
    }
    for (const attribute of element.attributes) {
      const name = attribute.name.toLowerCase()
      if (tag !== 'a' || (name !== 'href' && name !== 'rel')) {
        return `attribute ${name} is not allowed`
      }
      if (name === 'href' && !isAllowedHref(attribute.value)) {
        return 'link URL is not allowed'
      }
    }
  }
  return undefined
}

export const safeHtmlSchema = z.string().superRefine((value, context) => {
  const issue = findUnsafeHtml(value)
  if (issue) {
    context.addIssue({ code: 'custom', message: `must be safe HTML: ${issue}` })
  }
}).brand<'SafeHtml'>()

export type SafeHtml = z.infer<typeof safeHtmlSchema>

export function parseSafeHtml(input: unknown): SafeHtml {
  return safeHtmlSchema.parse(input)
}
