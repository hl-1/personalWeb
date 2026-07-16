import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
import test from 'node:test'

const projectRoot = fileURLToPath(new URL('../..', import.meta.url))
const composePath = resolve(projectRoot, 'deploy', 'compose.yml')
const envPath = resolve(projectRoot, '.env.example')
const caddyfilePath = resolve(projectRoot, 'deploy', 'Caddyfile')
const serverDockerfilePath = resolve(projectRoot, 'server', 'Dockerfile')
const webDockerfilePath = resolve(projectRoot, 'web', 'Dockerfile')

function readComposeConfig() {
  assert.ok(existsSync(composePath), `missing Compose file: ${composePath}`)
  const result = spawnSync(
    'docker',
    ['compose', '--env-file', envPath, '-f', composePath, 'config', '--format', 'json'],
    { cwd: projectRoot, encoding: 'utf8' },
  )
  assert.equal(result.status, 0, result.stderr || result.stdout)
  return JSON.parse(result.stdout)
}

test('Compose defines the exact P0 topology and health dependencies', () => {
  const config = readComposeConfig()

  assert.deepEqual(Object.keys(config.services).sort(), ['app', 'caddy', 'postgres'])
  assert.deepEqual(Object.keys(config.volumes ?? {}).sort(), ['postgres-data'])
  assert.match(config.services.postgres.healthcheck.test.join(' '), /pg_isready/)
  assert.equal(config.services.postgres.ports, undefined)
  assert.equal(config.services.app.depends_on.postgres.condition, 'service_healthy')
  assert.equal(config.services.caddy.depends_on.app.condition, 'service_healthy')
})

test('Caddy mounts its config and only Caddy publishes an HTTP port', () => {
  const config = readComposeConfig()
  const mounts = config.services.caddy.volumes ?? []

  assert.ok(
    mounts.some(
      (mount) =>
        mount.type === 'bind' &&
        mount.target === '/etc/caddy/Caddyfile' &&
        mount.read_only === true,
    ),
    'caddy must mount deploy/Caddyfile read-only',
  )
  assert.equal(config.services.app.ports, undefined)
  assert.equal(config.services.caddy.ports.length, 1)
})

test('identity secrets are passed only to the backend application', () => {
  const config = readComposeConfig()
  const appEnvironment = config.services.app.environment
  const webBuildArgs = config.services.caddy.build.args ?? {}

  assert.equal(appEnvironment.GITHUB_CLIENT_ID, 'EXAMPLE_ONLY_GITHUB_CLIENT_ID')
  assert.equal(appEnvironment.GITHUB_CLIENT_SECRET, 'EXAMPLE_ONLY_GITHUB_CLIENT_SECRET')
  assert.equal(appEnvironment.STUDYSTACK_ADMIN_GITHUB_IDS, '')
  assert.deepEqual(Object.keys(webBuildArgs), ['VITE_API_BASE_URL'])

  const webConfiguration = JSON.stringify(config.services.caddy)
  assert.doesNotMatch(webConfiguration, /GITHUB_CLIENT_(?:ID|SECRET)/)
  assert.doesNotMatch(webConfiguration, /STUDYSTACK_ADMIN_GITHUB_IDS/)
})

test('Caddy preserves actuator rejection, backend proxy, then SPA fallback order', () => {
  assert.ok(existsSync(caddyfilePath), `missing Caddyfile: ${caddyfilePath}`)
  const caddyfile = readFileSync(caddyfilePath, 'utf8')
  const actuator = caddyfile.indexOf('@actuator')
  const backend = caddyfile.indexOf('@backend')
  const fallback = caddyfile.indexOf('try_files')

  assert.match(caddyfile, /path \/actuator \/actuator\/\*/)
  assert.match(
    caddyfile,
    /path \/api \/api\/\* \/oauth2 \/oauth2\/\* \/login\/oauth2 \/login\/oauth2\/\*/,
  )
  assert.match(caddyfile, /reverse_proxy @backend app:8080/)
  assert.ok(actuator >= 0 && actuator < backend && backend < fallback)
})

test('runtime image stages contain only runtime artifacts', () => {
  assert.ok(existsSync(serverDockerfilePath), `missing server Dockerfile: ${serverDockerfilePath}`)
  assert.ok(existsSync(webDockerfilePath), `missing web Dockerfile: ${webDockerfilePath}`)
  const serverFinal = readFileSync(serverDockerfilePath, 'utf8').split(/\nFROM /).at(-1)
  const webFinal = readFileSync(webDockerfilePath, 'utf8').split(/\nFROM /).at(-1)

  assert.match(serverFinal, /^eclipse-temurin:21-jre-alpine/)
  assert.doesNotMatch(serverFinal, /\.m2|maven|src\//i)
  assert.match(webFinal, /^caddy:[^\s]+-alpine/)
  assert.doesNotMatch(webFinal, /node_modules|pnpm|src\//i)
})
