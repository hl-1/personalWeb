/// <reference types="node" />

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const webRoot = process.cwd()
const projectRoot = resolve(webRoot, '..')
const rootExamplePath = resolve(projectRoot, '.env.example')
const webExamplePath = resolve(webRoot, '.env.example')
const gitignorePath = resolve(projectRoot, '.gitignore')
const springResources = resolve(projectRoot, 'server', 'src', 'main', 'resources')

function readEnv(path: string): Record<string, string> {
  return Object.fromEntries(
    readFileSync(path, 'utf8')
      .split(/\r?\n/)
      .filter((line) => line && !line.startsWith('#'))
      .map((line) => {
        const separator = line.indexOf('=')
        return [line.slice(0, separator), line.slice(separator + 1)]
      }),
  )
}

function filesUnder(path: string): string[] {
  return readdirSync(path)
    .map((name) => resolve(path, name))
    .flatMap((entry) => (statSync(entry).isDirectory() ? filesUnder(entry) : [entry]))
}

describe('configuration policy', () => {
  it('provides non-secret root and web examples', () => {
    const rootEnv = readEnv(rootExamplePath)
    const webEnv = readEnv(webExamplePath)

    expect(rootEnv).toMatchObject({
      DB_NAME: 'studystack',
      DB_USER: 'studystack',
      CADDY_HTTP_PORT: '8080',
    })
    expect(rootEnv.DB_PASSWORD).toMatch(/^EXAMPLE_ONLY_/)
    expect(rootEnv.GITHUB_CLIENT_ID).toBe('EXAMPLE_ONLY_GITHUB_CLIENT_ID')
    expect(rootEnv.GITHUB_CLIENT_SECRET).toBe('EXAMPLE_ONLY_GITHUB_CLIENT_SECRET')
    expect(rootEnv.STUDYSTACK_ADMIN_GITHUB_IDS).toBe('')
    expect(webEnv).toEqual({ VITE_API_BASE_URL: '/api' })
  })

  it('does not expose server-only names through Vite', () => {
    const webSources = filesUnder(resolve(webRoot, 'src'))
      .filter((path) => /\.(ts|vue)$/.test(path))
      .map((path) => readFileSync(path, 'utf8'))
      .join('\n')

    expect(webSources).not.toMatch(
      /VITE_(?:DB|DATABASE|POSTGRES|OAUTH|ADMIN|JWT|SECRET|PASSWORD|PRIVATE)/i,
    )
  })

  it('uses server environment variables without a production password default', () => {
    const application = readFileSync(resolve(springResources, 'application.yml'), 'utf8')
    const production = readFileSync(
      resolve(springResources, 'application-prod.yml'),
      'utf8',
    )
    const springConfig = filesUnder(springResources)
      .filter((path) => path.endsWith('.yml'))
      .map((path) => readFileSync(path, 'utf8'))
      .join('\n')

    expect(application).toContain('${SPRING_PROFILES_ACTIVE:dev}')
    for (const variable of ['DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD']) {
      expect(springConfig).toContain(`\${${variable}`)
    }
    expect(production).toMatch(/password:\s*\$\{DB_PASSWORD\}\s*$/m)
  })

  it('does not contain recognized real-secret shapes', () => {
    const policyFiles = [
      rootExamplePath,
      webExamplePath,
      ...filesUnder(springResources).filter((path) => path.endsWith('.yml')),
      ...filesUnder(resolve(webRoot, 'src')).filter((path) => /\.(ts|vue)$/.test(path)),
    ]
    const content = policyFiles.map((path) => readFileSync(path, 'utf8')).join('\n')
    const privateKeyPattern = new RegExp(
      ['BEGIN ', '(?:RSA |EC |OPENSSH )?', 'PRIVATE ', 'KEY'].join(''),
    )
    const credentialPattern = new RegExp(
      ['ghp_', '[A-Za-z0-9]{20,}', '|github_', 'pat_', '|AKIA', '[0-9A-Z]{16}'].join(''),
    )

    expect(content).not.toMatch(privateKeyPattern)
    expect(content).not.toMatch(credentialPattern)
  })

  it('ignores local secrets and generated output while keeping examples', () => {
    const gitignore = readFileSync(gitignorePath, 'utf8')

    for (const pattern of [
      '.env',
      '.env.*.local',
      '*.pem',
      '*.key',
      '*.p12',
      '*.dump',
      '*.backup',
      'database-dumps/',
      '.contract/',
      'node_modules/',
      'dist/',
      'target/',
    ]) {
      expect(gitignore).toContain(pattern)
    }
    expect(gitignore).toContain('!.env.example')
    expect(gitignore).toContain('!web/.env.example')
  })
})
