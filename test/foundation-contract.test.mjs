import assert from 'node:assert/strict'
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import test from 'node:test'

const rootUrl = new URL('../', import.meta.url)

async function repositoryFile(path) {
  return readFile(new URL(path, rootUrl), 'utf8')
}

test('foundation verifier runs required fail-fast stages in order', async () => {
  const script = await repositoryFile('bin/verify-foundation.ps1')
  const stages = [
    'verify-repository.ps1',
    'verify-foundation-clean.ps1',
    'verify-security-profiles.ps1',
    'verify-swagger-annotations.ps1',
    'mvn',
    'npm ci',
    'npm test',
    'npm run build:prod',
    'npm audit --audit-level=high'
  ]

  let previous = -1
  for (const stage of stages) {
    const current = script.indexOf(stage)
    assert.ok(current > previous, `${stage} must appear after the previous stage`)
    previous = current
  }
  assert.match(script, /Push-Location\s+\$repoRoot/)
  assert.match(script, /\$LASTEXITCODE/)
  assert.match(script, /exit\s+\$stageExitCode/)
})

async function runFoundationCleanWithLogFixtures(fileNames) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'opc-foundation-clean-'))
  try {
    await mkdir(join(fixtureRoot, 'bin'))
    await mkdir(join(fixtureRoot, 'logs'))
    await mkdir(join(fixtureRoot, 'sql'))
    await mkdir(join(fixtureRoot, 'frontend', 'src'), { recursive: true })
    await copyFile(new URL('../bin/verify-foundation-clean.ps1', import.meta.url), join(fixtureRoot, 'bin', 'verify-foundation-clean.ps1'))
    for (const fileName of fileNames) {
      const filePath = join(fixtureRoot, fileName)
      await mkdir(dirname(filePath), { recursive: true })
      await writeFile(filePath, `${['admin', '123'].join('')}\n`, 'utf8')
    }

    return spawnSync('powershell.exe', [
      '-NoProfile',
      '-ExecutionPolicy', 'Bypass',
      '-File', join(fixtureRoot, 'bin', 'verify-foundation-clean.ps1')
    ], { cwd: fixtureRoot, encoding: 'utf8' })
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true })
  }
}

test('foundation residue scan excludes ignored log files', async () => {
  const result = await runFoundationCleanWithLogFixtures([
    'runtime.log',
    'logs/runtime.log',
    'nested/runtime.log'
  ])

  assert.equal(result.status, 0, result.stderr || result.stdout)
})

test('foundation residue scan still inspects non-log files under logs', async () => {
  const result = await runFoundationCleanWithLogFixtures(['logs/legacy.js'])

  assert.equal(result.status, 1, result.stderr || result.stdout)
  assert.match(result.stdout, /Foundation residue check failed/)
})

test('CI uses Java 17 and a supported Node 24 release', async () => {
  const workflow = await repositoryFile('.github/workflows/ci.yml')

  assert.match(workflow, /java-version:\s*['"]?17/)
  assert.match(workflow, /cache:\s*['"]?maven/)
  assert.match(workflow, /node-version:\s*['"]?24/)
  assert.match(workflow, /verify-foundation\.ps1/)
})

test('dependency scan fails at CVSS 7 and always uploads HTML and JSON reports', async () => {
  const workflow = await repositoryFile('.github/workflows/dependency-check.yml')
  const pom = await repositoryFile('pom.xml')

  assert.match(workflow, /workflow_dispatch:/)
  assert.match(workflow, /schedule:/)
  assert.match(workflow, /NVD_API_KEY:\s*\$\{\{\s*secrets\.NVD_API_KEY\s*\}\}/)
  assert.match(workflow, /if:\s*always\(\)/)
  assert.match(workflow, /dependency-check-report\.html/)
  assert.match(workflow, /dependency-check-report\.json/)
  assert.match(workflow, /npm ci/)
  assert.match(workflow, /npm audit --audit-level=high/)
  assert.match(workflow, /dependencies\.Count -eq 0/)
  assert.match(workflow, /steps\.maven_scan\.outcome != 'success'/)
  assert.match(pom, /<id>dependency-check<\/id>/)
  assert.match(pom, /<failBuildOnCVSS>7<\/failBuildOnCVSS>/)
  assert.match(pom, /<failOnError>true<\/failOnError>/)
  assert.match(pom, /<nvdApiKeyEnvironmentVariable>NVD_API_KEY<\/nvdApiKeyEnvironmentVariable>/)
  assert.match(pom, /<formats>[\s\S]*<format>HTML<\/format>[\s\S]*<format>JSON<\/format>[\s\S]*<\/formats>/)
})
