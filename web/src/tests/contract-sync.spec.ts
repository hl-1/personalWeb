/// <reference types="node" />

import { spawnSync } from 'node:child_process'
import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

const webRoot = process.cwd()
const script = resolve(webRoot, 'scripts', 'assert-contract-sync.mjs')
const contractRoot = resolve(webRoot, '.contract')
let fixtureRoot: string

function runComparison(committedPath: string, generatedPath: string) {
  return spawnSync(process.execPath, [script, committedPath, generatedPath], {
    cwd: webRoot,
    encoding: 'utf8',
  })
}

describe('contract sync comparison', () => {
  beforeEach(() => {
    mkdirSync(contractRoot, { recursive: true })
    fixtureRoot = mkdtempSync(resolve(contractRoot, 'sync-test-'))
  })

  afterEach(() => {
    rmSync(fixtureRoot, { force: true, recursive: true })
  })

  it('treats LF and CRLF contracts as equal without rewriting the committed file', () => {
    const committed = resolve(fixtureRoot, 'committed.d.ts')
    const generated = resolve(fixtureRoot, 'generated.d.ts')
    writeFileSync(committed, 'export interface Api {\r\n  ok: boolean\r\n}\r\n')
    writeFileSync(generated, 'export interface Api {\n  ok: boolean\n}\n')
    const before = statSync(committed).mtimeMs

    const result = runComparison(committed, generated)

    expect(result.status).toBe(0)
    expect(readFileSync(committed, 'utf8')).toContain('\r\n')
    expect(statSync(committed).mtimeMs).toBe(before)
  })

  it('reports both relative paths when contracts differ', () => {
    const committed = resolve(fixtureRoot, 'committed.d.ts')
    const generated = resolve(fixtureRoot, 'generated.d.ts')
    writeFileSync(committed, 'export type Value = string\n')
    writeFileSync(generated, 'export type Value = number\n')

    const result = runComparison(committed, generated)

    expect(result.status).toBe(1)
    expect(result.stderr).toContain('.contract/')
    expect(result.stderr).toContain('committed.d.ts')
    expect(result.stderr).toContain('generated.d.ts')
    expect(result.stderr).not.toContain(webRoot)
  })

  it('reports a missing committed contract with a relative path', () => {
    const committed = resolve(fixtureRoot, 'missing.d.ts')
    const generated = resolve(fixtureRoot, 'generated.d.ts')
    writeFileSync(generated, 'export interface Api {}\n')

    const result = runComparison(committed, generated)

    expect(result.status).toBe(1)
    expect(result.stderr).toContain('Contract file is missing')
    expect(result.stderr).toContain('.contract/')
    expect(result.stderr).toContain('missing.d.ts')
    expect(result.stderr).not.toContain(webRoot)
  })
})
