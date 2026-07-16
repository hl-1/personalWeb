/// <reference types="node" />

import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parse } from 'yaml'
import { describe, expect, it } from 'vitest'

type Step = {
  uses?: string
  run?: string
  with?: Record<string, string | number>
  'working-directory'?: string
}

type Job = {
  environment?: unknown
  needs?: string | string[]
  steps?: Step[]
}

type Workflow = {
  permissions?: Record<string, string>
  jobs?: Record<string, Job>
}

const projectRoot = resolve(process.cwd(), '..')
const workflowPath = resolve(projectRoot, '.github', 'workflows', 'ci.yml')

function readWorkflow(): { source: string; workflow: Workflow } {
  expect(existsSync(workflowPath), '.github/workflows/ci.yml must exist').toBe(true)
  const source = readFileSync(workflowPath, 'utf8')
  return { source, workflow: parse(source) as Workflow }
}

function steps(job: Job | undefined): Step[] {
  expect(job?.steps).toBeInstanceOf(Array)
  return job?.steps ?? []
}

function commandText(job: Job | undefined): string {
  return steps(job)
    .map((step) => step.run ?? '')
    .join('\n')
}

function expectAction(job: Job | undefined, action: string): Step {
  const step = steps(job).find((candidate) => candidate.uses === action)
  expect(step, `missing action ${action}`).toBeDefined()
  return step as Step
}

function expectFrozenInstall(job: Job | undefined): void {
  expect(commandText(job)).toContain('pnpm install --frozen-lockfile')
}

describe('CI quality gates', () => {
  it('defines only the five P0 verification jobs with read-only repository access', () => {
    const { workflow } = readWorkflow()

    expect(workflow.permissions).toEqual({ contents: 'read' })
    expect(Object.keys(workflow.jobs ?? {}).sort()).toEqual([
      'backend',
      'compose',
      'contract',
      'e2e',
      'frontend',
    ])
  })

  it('builds the backend with Java 21 and transfers only the OpenAPI contract artifact', () => {
    const { workflow } = readWorkflow()
    const jobs = workflow.jobs ?? {}
    const java = expectAction(jobs.backend, 'actions/setup-java@v4')
    const upload = expectAction(jobs.backend, 'actions/upload-artifact@v4')
    const download = expectAction(jobs.contract, 'actions/download-artifact@v4')

    expect(java.with).toMatchObject({ distribution: 'temurin', 'java-version': 21, cache: 'maven' })
    expect(commandText(jobs.backend)).toContain('./mvnw -B -ntp verify')
    expect(upload.with).toEqual({
      name: 'openapi-contract',
      path: 'server/target/openapi/openapi.json',
      'if-no-files-found': 'error',
    })
    expect(jobs.contract?.needs).toBe('backend')
    expect(download.with).toEqual({
      name: 'openapi-contract',
      path: 'server/target/openapi',
    })

    const allUses = Object.values(jobs).flatMap((job) =>
      steps(job).flatMap((step) => (step.uses ? [step.uses] : [])),
    )
    expect(allUses.filter((action) => action.includes('/upload-artifact@'))).toEqual([
      'actions/upload-artifact@v4',
    ])
    expect(allUses.filter((action) => action.includes('/download-artifact@'))).toEqual([
      'actions/download-artifact@v4',
    ])
  })

  it('runs every frontend, contract, Compose, and browser gate with pinned tooling', () => {
    const { workflow } = readWorkflow()
    const jobs = workflow.jobs ?? {}

    for (const name of ['frontend', 'contract', 'e2e']) {
      const job = jobs[name]
      expectAction(job, 'pnpm/action-setup@v4')
      const setupPnpm = expectAction(job, 'pnpm/action-setup@v4')
      expect(setupPnpm.with).toMatchObject({ version: '11.9.0' })
      expectFrozenInstall(job)
    }

    const frontendCommands = commandText(jobs.frontend)
    for (const command of ['pnpm lint', 'pnpm typecheck', 'pnpm test', 'pnpm build']) {
      expect(frontendCommands).toContain(command)
    }
    expect(commandText(jobs.contract)).toContain('pnpm contract:check')
    expect(commandText(jobs.compose)).toContain(
      'docker compose --env-file .env.example -f deploy/compose.yml config --quiet',
    )

    const e2eCommands = commandText(jobs.e2e)
    expect(e2eCommands).toContain('pnpm exec playwright install --with-deps chromium')
    expect(e2eCommands).toContain(
      'docker compose --env-file .env.example -f deploy/compose.yml up -d --build --wait',
    )
    expect(e2eCommands).toContain('pnpm test:e2e')
    expect(e2eCommands).not.toContain('pnpm test:e2e -- foundation-routing.spec.ts')
    expect(e2eCommands).toContain(
      'docker compose --env-file .env.example -f deploy/compose.yml down --volumes',
    )
  })

  it('contains no deployment, publishing, release, registry, or remote-shell capability', () => {
    const { source, workflow } = readWorkflow()
    const jobs = workflow.jobs ?? {}

    const actions = Object.values(jobs).flatMap((job) =>
      steps(job).flatMap((step) => (step.uses ? [step.uses] : [])),
    )

    expect(Object.values(jobs).every((job) => job.environment === undefined)).toBe(true)
    expect(actions.every((action) => /@(?:v\d+|[0-9a-f]{40})$/.test(action))).toBe(true)
    expect(source).not.toMatch(/docker\/(?:login|build-push)-action/i)
    expect(source).not.toMatch(/\bdocker\s+push\b/i)
    expect(source).not.toMatch(/\b(?:gh\s+release|release-action|softprops\/action-gh-release)\b/i)
    expect(source).not.toMatch(/\b(?:ssh|scp|rsync)\b/i)
    expect(source).not.toMatch(/\b(?:kubectl|helm|ansible-playbook)\b/i)
    expect(source).not.toMatch(/\benvironment\s*:/i)
  })
})
