import { existsSync, readFileSync } from 'node:fs'
import { relative, resolve, sep } from 'node:path'

function displayPath(path) {
  return relative(process.cwd(), resolve(path)).split(sep).join('/')
}

function fail(message) {
  console.error(message)
  process.exitCode = 1
}

function normalizeLineEndings(content) {
  return content.replace(/\r\n?/g, '\n')
}

const [committedPath, generatedPath] = process.argv.slice(2)

if (!committedPath || !generatedPath) {
  fail('Usage: node scripts/assert-contract-sync.mjs <committed> <generated>')
} else if (!existsSync(committedPath)) {
  fail(`Contract file is missing: ${displayPath(committedPath)}`)
} else if (!existsSync(generatedPath)) {
  fail(`Contract file is missing: ${displayPath(generatedPath)}`)
} else if (
  normalizeLineEndings(readFileSync(committedPath, 'utf8')) !==
  normalizeLineEndings(readFileSync(generatedPath, 'utf8'))
) {
  fail(
    `OpenAPI contract types differ: ${displayPath(committedPath)} != ${displayPath(generatedPath)}`,
  )
}
