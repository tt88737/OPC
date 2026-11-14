import assert from 'node:assert/strict'
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import test from 'node:test'

const rootUrl = new URL('../', import.meta.url)
const powerShellExecutable = process.platform === 'win32' ? 'powershell.exe' : 'pwsh'
const requireFromFrontend = createRequire(new URL('../frontend/package.json', import.meta.url))
const { parse: parseYaml } = requireFromFrontend('yaml')

const PINNED_ACTIONS = {
  'actions/checkout': { sha: '3d3c42e5aac5ba805825da76410c181273ba90b1', version: 'v7.0.1' },
  'actions/setup-java': { sha: '77dd5b2ce225ea11469928ac317850245a74ae78', version: 'v6.2.0' },
  'actions/setup-node': { sha: '949feb2413d6458794dcd2491c4babbbce0c15c1', version: 'v7.1.0' },
  'actions/upload-artifact': { sha: 'cf430e030ddbb5b0abf93d22962f4752f3646cd9', version: 'v7.0.2' }
}

async function repositoryFile(path) {
  return readFile(new URL(path, rootUrl), 'utf8')
}

async function repositoryYaml(path) {
  return parseYaml(await repositoryFile(path))
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

test('foundation verifier propagates a failing child exit code', async () => {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'opc-foundation-exit-'))
  try {
    await mkdir(join(fixtureRoot, 'bin'))
    await copyFile(new URL('../bin/verify-foundation.ps1', import.meta.url), join(fixtureRoot, 'bin', 'verify-foundation.ps1'))
    await writeFile(join(fixtureRoot, 'bin', 'verify-repository.ps1'), 'exit 23\n', 'utf8')

    const result = spawnSync(powerShellExecutable, [
      '-NoProfile',
      '-ExecutionPolicy', 'Bypass',
      '-File', join(fixtureRoot, 'bin', 'verify-foundation.ps1')
    ], { cwd: tmpdir(), encoding: 'utf8' })

    assert.equal(result.status, 23, result.stderr || result.stdout)
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true })
  }
})

async function runFoundationCleanWithLogFixtures(fileNames) {
  const files = Object.fromEntries(fileNames.map(fileName => [fileName, `${['admin', '123'].join('')}\n`]))
  return runFoundationCleanFixture(files)
}

async function runFoundationCleanFixture(files) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'opc-foundation-clean-'))
  try {
    await mkdir(join(fixtureRoot, 'bin'))
    await mkdir(join(fixtureRoot, 'logs'))
    await mkdir(join(fixtureRoot, 'sql'))
    await mkdir(join(fixtureRoot, 'frontend', 'src'), { recursive: true })
    await mkdir(join(fixtureRoot, 'opc-admin', 'src', 'main', 'resources'), { recursive: true })
    await copyFile(new URL('../bin/verify-foundation-clean.ps1', import.meta.url), join(fixtureRoot, 'bin', 'verify-foundation-clean.ps1'))
    const gitInit = spawnSync('git', ['init', '--quiet'], { cwd: fixtureRoot, encoding: 'utf8' })
    assert.equal(gitInit.status, 0, gitInit.stderr || gitInit.stdout)
    for (const [fileName, content] of Object.entries(files)) {
      const filePath = join(fixtureRoot, fileName)
      await mkdir(dirname(filePath), { recursive: true })
      await writeFile(filePath, content, 'utf8')
    }

    return spawnSync(powerShellExecutable, [
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

for (const [table, insert] of [
  ['sys_dept', "insert into sys_dept values(101, 100, '0,100', '测试部门');\n"],
  ['sys_post', "insert into sys_post values(1, 'test', '示例岗位');\n"],
  ['sys_notice', "insert into sys_notice values(1, '示例通知');\n"]
]) {
  test(`foundation residue verifier rejects ${table} demo rows`, async () => {
    const result = await runFoundationCleanFixture({ 'sql/opc_20260417.sql': insert })

    assert.equal(result.status, 1, result.stderr || result.stdout)
    assert.match(result.stdout, /Foundation SQL check failed/)
  })
}

test('foundation residue verifier rejects the legacy banner', async () => {
  const result = await runFoundationCleanFixture({
    'opc-admin/src/main/resources/banner.txt': '_ooOoo_ o8888888o 永无 BUG\n'
  })

  assert.equal(result.status, 1, result.stderr || result.stdout)
  assert.match(result.stdout, /Foundation banner check failed/)
})

test('foundation residue verifier accepts required configuration and third-party notices', async () => {
  const upstreamName = ['Ruo', 'Yi'].join('')
  const upstreamChineseName = String.fromCodePoint(0x82e5, 0x4f9d)
  const legacyCopyright = `Copyright ${String.fromCodePoint(0x00a9)} 2018-2026`
  const result = await runFoundationCleanFixture({
    'sql/opc_20260417.sql': [
      "insert into sys_menu values(1, '系统管理');",
      "insert into sys_dict_type values(1, '通知类型');"
    ].join('\n'),
    'opc-admin/src/main/resources/banner.txt': 'OPC\n',
    'THIRD_PARTY_NOTICES.md': `${upstreamName} ${upstreamChineseName}\n${legacyCopyright}\nMIT License\n`
  })

  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.match(result.stdout, /Foundation residue and credential checks passed/)
})

test('foundation SQL contains only neutral bootstrap data', async () => {
  const sql = await repositoryFile('sql/opc_20260417.sql')

  assert.doesNotMatch(sql, /insert into sys_dept values\((?:101|102|103|104|105|106|107|108|109),/i)
  assert.doesNotMatch(sql, /15888888888|opc@example\.com/)
  assert.match(sql, /insert into sys_user values\(1,\s+null,/i)
  assert.doesNotMatch(sql, /insert into sys_post values/i)
  assert.doesNotMatch(sql, /insert into sys_user_post values/i)
  assert.doesNotMatch(sql, /insert into sys_role_dept values/i)
  assert.doesNotMatch(sql, /insert into sys_notice values/i)
  assert.match(sql, /insert into sys_menu values/i)
  assert.match(sql, /insert into sys_dict_type values/i)
})

test('foundation residue verifier keeps a narrow third-party notices exclusion', async () => {
  const verifier = await repositoryFile('bin/verify-foundation-clean.ps1')
  assert.match(verifier, /THIRD_PARTY_NOTICES\.md/)
  assert.doesNotMatch(verifier, /!\*\*\/\*\.md/)
})

test('OPC banner and license documentation contain no upstream template residue', async () => {
  const banner = await repositoryFile('opc-admin/src/main/resources/banner.txt')
  const readme = await repositoryFile('README.md')

  assert.match(banner, /OPC/)
  assert.doesNotMatch(banner, /佛祖|永无\s*BUG|永不宕机|_ooOoo_|o8888888o/i)
  assert.match(readme, /LICENSE[^\n]*OPC|OPC[^\n]*LICENSE/)
  assert.doesNotMatch(readme, /THIRD_PARTY_NOTICES\.md/)
})

test('CI uses Java 21 and a supported Node 24 release', async () => {
  const workflow = await repositoryFile('.github/workflows/ci.yml')
  const document = parseYaml(workflow)

  assert.match(workflow, /java-version:\s*['"]?21/)
  assert.match(workflow, /cache:\s*['"]?maven/)
  assert.match(workflow, /node-version:\s*['"]?24/)
  assert.match(workflow, /verify-foundation\.ps1/)
  assert.match(workflow, /choco install ripgrep --yes --no-progress/)
  assert.match(workflow, /rg --version/)
  assert.ok(workflow.indexOf('choco install ripgrep') < workflow.indexOf('verify-foundation.ps1'))
  assert.match(workflow, /persist-credentials:\s*false/)
  assert.doesNotMatch(workflow, /uses:\s*actions\/[^@\s]+@v\d/)
  for (const [action, pin] of Object.entries(PINNED_ACTIONS).filter(([name]) => name !== 'actions/upload-artifact')) {
    assert.match(workflow, new RegExp(`uses:\\s*${action.replace('/', '\\/')}@${pin.sha}\\s+# ${pin.version}`))
  }
  assert.deepEqual(document.permissions, { contents: 'read' })
  assert.equal(document.jobs.verify.permissions, undefined)
  const checkout = document.jobs.verify.steps.find(step => step.uses?.startsWith('actions/checkout@'))
  assert.equal(checkout.with['persist-credentials'], false)
  assert.ok(Object.hasOwn(document.on, 'push'))
  assert.ok(Object.hasOwn(document.on, 'pull_request'))
})

test('dependency scan fails at CVSS 7 and always uploads HTML and JSON reports', async () => {
  const workflow = await repositoryFile('.github/workflows/dependency-check.yml')
  const document = parseYaml(workflow)
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
  assert.match(workflow, /choco install ripgrep --yes --no-progress/)
  assert.match(workflow, /rg --version/)
  assert.ok(workflow.indexOf('choco install ripgrep') < workflow.indexOf('Scan Maven dependencies'))
  assert.match(workflow, /persist-credentials:\s*false/)
  assert.doesNotMatch(workflow, /uses:\s*actions\/[^@\s]+@v\d/)
  for (const [action, pin] of Object.entries(PINNED_ACTIONS)) {
    assert.match(workflow, new RegExp(`uses:\\s*${action.replace('/', '\\/')}@${pin.sha}\\s+# ${pin.version}`))
  }
  assert.deepEqual(document.permissions, { contents: 'read' })
  assert.equal(document.jobs.scan.permissions, undefined)
  const checkout = document.jobs.scan.steps.find(step => step.uses?.startsWith('actions/checkout@'))
  assert.equal(checkout.with['persist-credentials'], false)
  assert.ok(Object.hasOwn(document.on, 'workflow_dispatch'))
  assert.ok(Array.isArray(document.on.schedule))
  assert.match(pom, /<id>dependency-check<\/id>/)
  assert.match(pom, /<failBuildOnCVSS>7<\/failBuildOnCVSS>/)
  assert.match(pom, /<failOnError>true<\/failOnError>/)
  assert.match(pom, /<nvdApiKeyEnvironmentVariable>NVD_API_KEY<\/nvdApiKeyEnvironmentVariable>/)
  assert.match(pom, /<formats>[\s\S]*<format>HTML<\/format>[\s\S]*<format>JSON<\/format>[\s\S]*<\/formats>/)
})

test('Dependabot maintains pinned GitHub Actions weekly', async () => {
  const dependabot = await repositoryFile('.github/dependabot.yml')
  const document = await repositoryYaml('.github/dependabot.yml')

  assert.match(dependabot, /package-ecosystem:\s*["']?github-actions/)
  assert.match(dependabot, /directory:\s*["']?\/["']?/)
  assert.match(dependabot, /interval:\s*["']?weekly/)
  assert.equal(document.version, 2)
  const githubActions = document.updates.find(update => update['package-ecosystem'] === 'github-actions')
  assert.equal(githubActions.directory, '/')
  assert.equal(githubActions.schedule.interval, 'weekly')
})
