import assert from 'node:assert/strict'
import { copyFile, mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const rootUrl = new URL('../', import.meta.url)
const frontendRoot = fileURLToPath(new URL('../frontend/', import.meta.url))
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
  const repositoryVerifier = await repositoryFile('bin/verify-repository.ps1')
  const stages = [
    'verify-repository.ps1',
    'verify-version-consistency.ps1',
    'verify-foundation-clean.ps1',
    'verify-security-profiles.ps1',
    'verify-swagger-annotations.ps1',
    'verify-api-contracts.ps1',
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
  assert.match(repositoryVerifier, /['"]bin\/verify-version-consistency\.ps1['"]/)
  assert.match(repositoryVerifier, /['"]bin\/verify-api-contracts\.ps1['"]|verify-api-contracts\.ps1/)
})

test('Java and frontend quality gates run before tests and builds', async () => {
  const pom = await repositoryFile('pom.xml')
  const frontendPackage = JSON.parse(await repositoryFile('frontend/package.json'))
  const verifier = await repositoryFile('bin/verify-foundation.ps1')

  assert.match(pom, /<artifactId>maven-checkstyle-plugin<\/artifactId>/)
  assert.match(pom, /<version>3\.6\.0<\/version>/)
  assert.match(pom, /<configLocation>\$\{maven\.multiModuleProjectDirectory\}\/config\/checkstyle\/checkstyle\.xml<\/configLocation>/)
  assert.equal(frontendPackage.scripts.lint, 'eslint .')
  assert.equal(frontendPackage.scripts['format:check'], 'prettier --check .')

  const orderedStages = [
    'mvnw.cmd checkstyle:check',
    'npm run lint',
    'npm run format:check',
    'mvnw.cmd clean verify',
    'foundation contract tests',
    'npm test',
    'npm run build:prod'
  ]
  let previous = -1
  for (const stage of orderedStages) {
    const current = verifier.indexOf(stage)
    assert.ok(current > previous, `${stage} must appear after the previous quality stage`)
    previous = current
  }
})

test('quality gate configuration is repository-owned and migration-safe', async () => {
  const checkstyle = await repositoryFile('config/checkstyle/checkstyle.xml')
  const eslint = await repositoryFile('frontend/eslint.config.js')
  const eslintConfig = (await import(new URL('frontend/eslint.config.js', rootUrl))).default
  const prettierConfig = JSON.parse(await repositoryFile('frontend/.prettierrc.json'))
  const prettierIgnore = await repositoryFile('frontend/.prettierignore')
  const frontendPackage = JSON.parse(await repositoryFile('frontend/package.json'))
  const packageLock = JSON.parse(await repositoryFile('frontend/package-lock.json'))

  assert.match(checkstyle, /<module name="Checker">/)
  assert.match(checkstyle, /<module name="TreeWalker">/)
  for (const rule of ['EmptyStatement', 'NoFinalizer', 'OneStatementPerLine', 'OuterTypeFilename']) {
    assert.match(checkstyle, new RegExp(`<module name="${rule}"\\s*\/>`))
  }
  assert.match(eslint, /import js from ['"]@eslint\/js['"]/)
  assert.match(eslint, /import pluginVue from ['"]eslint-plugin-vue['"]/)
  assert.match(eslint, /pluginVue\.configs\[['"]flat\/essential['"]\]/)
  assert.match(eslint, /autoImportGlobals\.globals/)
  assert.match(eslint, /export default\s*\[/)
  const eslintIgnorePatterns = eslintConfig.flatMap(configuration => configuration.ignores ?? [])
  for (const generatedPattern of ['node_modules/**', 'dist/**', 'coverage/**']) {
    assert.ok(eslintIgnorePatterns.includes(generatedPattern), `${generatedPattern} must be ignored by ESLint`)
  }
  for (const configuration of eslintConfig) {
    const disabledRules = Object.entries(configuration.rules ?? {}).filter(([rule, value]) => {
      const severity = Array.isArray(value) ? value[0] : value
      return (rule === 'no-undef' || rule === 'no-unused-vars' || rule.startsWith('vue/')) &&
        (severity === 'off' || severity === 0)
    })
    if (disabledRules.length === 0) continue
    assert.ok(configuration.files?.length, `${disabledRules.map(([rule]) => rule).join(', ')} need exact files`)
    for (const file of configuration.files) {
      assert.doesNotMatch(file, /[*?\[\]{}!]/, `${file} must be an exact ESLint exception`)
      const fileInfo = await stat(new URL(`frontend/${file}`, rootUrl))
      assert.ok(fileInfo.isFile(), `${file} must name an existing ESLint exception file`)
    }
  }
  assert.equal(prettierConfig.endOfLine, 'auto')

  const ignoreEntries = prettierIgnore
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line && !line.startsWith('#'))
  const generatedDirectories = ['node_modules/', 'dist/', 'coverage/', 'public/']
  for (const generatedPath of generatedDirectories) {
    assert.ok(ignoreEntries.includes(generatedPath), `${generatedPath} must be ignored`)
  }
  const legacyFiles = ignoreEntries.filter(entry => !generatedDirectories.includes(entry))
  for (const entry of legacyFiles) {
    assert.ok(!entry.endsWith('/'), `${entry} must not ignore a directory`)
    assert.doesNotMatch(entry, /[*?\[\]{}!]/, `${entry} must not use a glob pattern`)
    assert.doesNotMatch(entry, /(^|[\\/])\.\.([\\/]|$)|^[\\/]/, `${entry} must stay inside frontend`)
    const fileInfo = await stat(new URL(`frontend/${entry}`, rootUrl))
    assert.ok(fileInfo.isFile(), `${entry} must name an existing file`)
  }
  assert.equal(legacyFiles.length, 151)

  const qualityTools = ['@eslint/js', 'eslint', 'eslint-plugin-vue', 'globals', 'prettier']
  for (const tool of qualityTools) {
    const declaredVersion = frontendPackage.devDependencies[tool]
    assert.match(declaredVersion, /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/, `${tool} must use an exact version`)
    assert.equal(packageLock.packages[''].devDependencies[tool], declaredVersion)
    assert.equal(packageLock.packages[`node_modules/${tool}`].version, declaredVersion)
  }
})

test('ESLint rejects new JavaScript and Vue correctness violations', async () => {
  const fixtureRoot = await mkdtemp(join(frontendRoot, '.foundation-eslint-'))
  const eslintExecutable = join(frontendRoot, 'node_modules', 'eslint', 'bin', 'eslint.js')
  try {
    const javascriptFixture = join(fixtureRoot, 'new-module.js')
    const vueFixture = join(fixtureRoot, 'NewComponent.vue')
    await writeFile(javascriptFixture, 'const unusedValue = missingGlobal\n', 'utf8')
    await writeFile(vueFixture, '<template><div id="first" id="second" /></template>\n', 'utf8')

    const javascriptResult = spawnSync(process.execPath, [eslintExecutable, javascriptFixture], {
      cwd: frontendRoot,
      encoding: 'utf8'
    })
    assert.notEqual(javascriptResult.status, 0, javascriptResult.stderr || javascriptResult.stdout)
    assert.match(javascriptResult.stdout, /no-undef/)
    assert.match(javascriptResult.stdout, /no-unused-vars/)

    const vueResult = spawnSync(process.execPath, [eslintExecutable, vueFixture], {
      cwd: frontendRoot,
      encoding: 'utf8'
    })
    assert.notEqual(vueResult.status, 0, vueResult.stderr || vueResult.stdout)
    assert.match(vueResult.stdout, /vue\/no-duplicate-attributes/)
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true })
  }
})

test('repository pins Maven wrapper and documents wrapper builds', async () => {
  const wrapperProperties = await repositoryFile('.mvn/wrapper/maven-wrapper.properties')
  const readme = await repositoryFile('README.md')
  const verifier = await repositoryFile('bin/verify-foundation.ps1')

  assert.match(wrapperProperties, /distributionUrl=.*apache-maven-3\.9\./)
  assert.match(await repositoryFile('mvnw'), /Maven Wrapper/)
  assert.match(await repositoryFile('mvnw.cmd'), /Maven Wrapper/)
  assert.match(readme, /mvnw(?:\.cmd)?\s+(?:clean\s+)?(?:test|package|verify)/)
  assert.match(verifier, /mvnw\.cmd\s+clean\s+verify/)
})

test('contribution and release governance documents are tracked and actionable', async () => {
  const trackedFiles = spawnSync('git', ['ls-files', '--error-unmatch', 'CONTRIBUTING.md', 'docs/release-and-rollback.md'], {
    cwd: fileURLToPath(rootUrl),
    encoding: 'utf8'
  })
  assert.equal(trackedFiles.status, 0, trackedFiles.stderr || trackedFiles.stdout)

  const contributing = await repositoryFile('CONTRIBUTING.md')
  const release = await repositoryFile('docs/release-and-rollback.md')
  const unifiedVerification = /powershell -NoProfile -ExecutionPolicy Bypass -File \.\\bin\\verify-foundation\.ps1/

  assert.match(contributing, /main[^\n]*(?:Pull Request|PR)|(?:Pull Request|PR)[^\n]*main/i)
  assert.match(contributing, /分支命名/)
  assert.match(contributing, /Conventional Commits/)
  assert.match(contributing, /最小[^\n]*(?:范围|变更)|(?:范围|变更)[^\n]*最小/)
  assert.match(contributing, /审查/)
  assert.match(contributing, unifiedVerification)
  assert.match(contributing, /失败[^\n]*(?:不得|禁止)[^\n]*(?:绕过|忽略)/)
  assert.match(contributing, /(?:仅支持|只支持)[^\n]*Windows PowerShell/)
  assert.match(contributing, /非 Windows[^\n]*(?:Windows VM|CI)[^\n]*完整门禁/)
  assert.match(contributing, /\.\/mvnw[^\n]*(?:不能|不得)[^\n]*(?:替代|代替)/)
  assert.match(contributing, /(?:敏感信息|密钥|凭据)[^\n]*(?:不得|禁止)[^\n]*提交/)
  for (const toolchain of [/Java 21/, /Node(?:\.js)? 24\.15(?:\.0)?/, /Maven Wrapper/, /npm ci/]) {
    assert.match(contributing, toolchain)
  }

  assert.match(release, /SemVer|语义化版本/)
  assert.match(release, /tag/)
  assert.match(release, /mvnw\.cmd clean verify/)
  assert.match(release, unifiedVerification)
  assert.match(release, /verify-version-consistency\.ps1 -ExpectedVersion vMAJOR\.MINOR\.PATCH/)
  assert.match(release, /(?:仅支持|只支持)[^\n]*Windows PowerShell/)
  assert.match(release, /非 Windows[^\n]*(?:Windows VM|CI)[^\n]*完整门禁/)
  assert.match(release, /\.\/mvnw[^\n]*(?:不能|不得)[^\n]*(?:替代|代替)/)
  assert.match(release, /(?:构建产物|artifact)[^\n]*(?:校验|哈希|SHA-?256)/i)
  assert.match(release, /生产[^\n]*配置[^\n]*检查/)
  assert.match(release, /数据库[^\n]*(?:变更|迁移)[^\n]*备份|备份[^\n]*数据库/)
  assert.match(release, /健康检查/)
  assert.match(release, /应用[^\n]*配置[^\n]*数据库/)
  assert.match(release, /数据异常[^\n]*(?:摘流|隔离写流量)[^\n]*(?:证据|现场)/)
  assert.match(release, /安全边界失效[^\n]*(?:摘流|隔离)[^\n]*(?:证据|现场)/)
  assert.match(release, /应用[^\n]*匹配配置[^\n]*(?:同一|一个)回滚单元[^\n]*原子/)
  assert.match(release, /平台[^\n]*不能[^\n]*原子[^\n]*(?:摘流|写隔离)[^\n]*兼容[^\n]*顺序[^\n]*全部成功[^\n]*恢复流量/)
  assert.match(release, /应用[^\n]*匹配配置[^\n]*回滚[^\n]*健康检查[^\n]*数据完整性检查/)
  assert.match(release, /数据库[^\n]*(?:恢复|前向修复)[^\n]*数据影响范围[^\n]*恢复点[^\n]*RPO\/RTO[^\n]*审批/)
  assert.match(release, /不可逆迁移[^\n]*(?:专门|专项)[^\n]*(?:流程|处置)/)
  assert.doesNotMatch(release, /回滚顺序为应用\s*→\s*配置/)
  assert.doesNotMatch(release, /只有[^\n]*(?:应用|配置|服务)[^\n]*(?:不能|未能|未)[^\n]*恢复[^\n]*(?:才|方)[^\n]*(?:回滚|恢复)数据库/)
  assert.match(release, /不可逆[^\n]*(?:迁移|变更)/)
  assert.match(release, /(?:失败证据|审计记录)/)
  assert.doesNotMatch(release, /(?:已经|当前已|现已)(?:引入|使用|集成)[^\n]*(?:Flyway|Liquibase)/i)
})

test('README and security policy expose governance entry points and production boundaries', async () => {
  const readme = await repositoryFile('README.md')
  const security = await repositoryFile('SECURITY.md')

  assert.match(readme, /CONTRIBUTING\.md/)
  assert.match(readme, /docs\/release-and-rollback\.md/)
  assert.match(readme, /powershell -NoProfile -ExecutionPolicy Bypass -File \.\\bin\\verify-foundation\.ps1/)
  assert.match(readme, /Java 21/)
  assert.match(readme, /Node(?:\.js)? 24\.15(?:\.0)?/)
  assert.match(readme, /JaCoCo/)
  assert.match(readme, /5%/)

  assert.match(security, /漏洞报告/)
  assert.match(security, /https:\/\/github\.com\/tt88737\/OPC\/security\/advisories\/new/)
  assert.match(security, /密钥泄露/)
  assert.match(security, /(?:最小权限|最小特权)/)
  assert.match(security, /生产[^\n]*(?:边界|环境)/)
  assert.match(security, /(?:轮换|吊销)/)
})

test('governance findings record the stable Node toolchain standard', async () => {
  const findings = await repositoryFile('findings.md')

  assert.match(findings, /标准工具链[^\n]*Node(?:\.js)? 24\.15\.0/)
})

test('governance documents record the enforced main branch policy', async () => {
  const taskPlan = await repositoryFile('task_plan.md')
  const progress = await repositoryFile('progress.md')
  const findings = await repositoryFile('findings.md')
  const contributing = await repositoryFile('CONTRIBUTING.md')
  const policyDocuments = `${progress}\n${contributing}`

  assert.match(taskPlan, /Task 5\s*\|\s*已完成/)
  assert.match(progress, /2026-11-14/)
  assert.match(policyDocuments, /verify/)
  assert.match(policyDocuments, /(?:至少|需要|要求)[^\n]*1[^\n]*(?:批准|审查)|1[^\n]*(?:批准|审查)/)
  assert.match(policyDocuments, /日常[^\n]*(?:至少|需要|要求)[^\n]*1[^\n]*(?:批准|审查)/)
  assert.match(policyDocuments, /管理员[^\n]*(?:事故|安全修复)[^\n]*(?:无可用|无法获得)[^\n]*(?:审查者|审批)/)
  assert.match(policyDocuments, /管理员[^\n]*(?:仅|只能)[^\n]*(?:PR|Pull Request)[^\n]*(?:绕过|免除)[^\n]*(?:批准|审批|审查)/i)
  assert.match(policyDocuments, /管理员[^\n]*(?:绕过|免除)[^\n]*(?:批准|审批|审查)[^\n]*verify[^\n]*(?:必须|仍须|仍需)[^\n]*(?:通过|成功)/i)
  assert.match(policyDocuments, /(?:理由|原因)[^\n]*风险[^\n]*验证/)
  assert.match(policyDocuments, /事后[^\n]*(?:补审|审查)[^\n]*复盘/)
  assert.match(policyDocuments, /(?:禁止|不得)[^\n]*直接推送/)
  assert.match(policyDocuments, /(?:禁止|禁用)[^\n]*强制推送/)
  assert.match(policyDocuments, /(?:禁止|禁用)[^\n]*删除/)
  assert.match(policyDocuments, /(?:讨论|会话|对话)[^\n]*(?:解决|完成)/)
  assert.match(findings, /远端状态[^\n]*(?:API|接口)[^\n]*(?:复核|核验)/i)
  assert.doesNotMatch(`${taskPlan}\n${progress}\n${findings}\n${contributing}`, /(?:尚未|未)(?:配置|完成或核验)|在其完成前|管理员不可绕过(?:保护|审批|审查)/)
})

async function runVersionConsistencyFixture({
  pomVersion = '3.9.2',
  frontendVersion = '3.9.2',
  applicationYaml = 'opc:\n  version: 3.9.2\n',
  expectedVersion
} = {}) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'opc-version-consistency-'))
  const pomPath = join(fixtureRoot, 'pom.xml')
  const frontendPackagePath = join(fixtureRoot, 'package.json')
  const applicationYamlPath = join(fixtureRoot, 'application.yml')
  try {
    await writeFile(pomPath, [
      '<project xmlns="http://maven.apache.org/POM/4.0.0">',
      `<modelVersion>4.0.0</modelVersion><groupId>com.opc</groupId><artifactId>opc</artifactId><version>${pomVersion}</version>`,
      '</project>'
    ].join(''), 'utf8')
    await writeFile(frontendPackagePath, JSON.stringify({ name: 'opc', version: frontendVersion }), 'utf8')
    await writeFile(applicationYamlPath, applicationYaml, 'utf8')

    const args = [
      '-NoProfile', '-ExecutionPolicy', 'Bypass',
      '-File', fileURLToPath(new URL('../bin/verify-version-consistency.ps1', import.meta.url)),
      '-PomPath', pomPath,
      '-FrontendPackagePath', frontendPackagePath,
      '-ApplicationYamlPath', applicationYamlPath
    ]
    if (expectedVersion !== undefined) args.push('-ExpectedVersion', expectedVersion)
    return spawnSync(powerShellExecutable, args, { encoding: 'utf8' })
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true })
  }
}

test('version consistency verifier accepts aligned versions and normalized release tags', async () => {
  for (const expectedVersion of [undefined, '3.9.2', 'v3.9.2']) {
    const result = await runVersionConsistencyFixture({ expectedVersion })
    assert.equal(result.status, 0, result.stderr || result.stdout)
    assert.match(result.stdout, /3\.9\.2/)
  }
})

test('version consistency verifier rejects repository and expected version mismatches', async () => {
  const repositoryMismatch = await runVersionConsistencyFixture({ frontendVersion: '3.9.3' })
  const expectedMismatch = await runVersionConsistencyFixture({ expectedVersion: 'v3.9.3' })

  assert.notEqual(repositoryMismatch.status, 0)
  assert.match(repositoryMismatch.stderr + repositoryMismatch.stdout, /mismatch/i)
  assert.notEqual(expectedMismatch.status, 0)
  assert.match(expectedMismatch.stderr + expectedMismatch.stdout, /expected.*3\.9\.3|3\.9\.3.*expected/i)

  for (const expectedVersion of ['3.9', 'v3.9.2-beta']) {
    const invalidExpected = await runVersionConsistencyFixture({ expectedVersion })
    assert.notEqual(invalidExpected.status, 0)
    assert.match(invalidExpected.stderr + invalidExpected.stdout, /expected.*semver|semver.*expected/i)
  }
})

test('version consistency verifier rejects ambiguous, non-scalar, and missing YAML versions', async () => {
  const invalidDocuments = [
    'opc:\n  version: 3.9.2\nopc:\n  version: 3.9.2\n',
    'opc:\n  version: 3.9.2\n  version: 3.9.2\n',
    'opc:\n  version:3.9.2\n',
    'opc:\n version: 3.9.2\n',
    'opc:\n    version: 3.9.2\n',
    'opc:\n\tversion: 3.9.2\n',
    'opc:\n  version:\n    major: 3\n',
    'opc:\n  name: OPC\n'
  ]

  for (const applicationYaml of invalidDocuments) {
    const result = await runVersionConsistencyFixture({ applicationYaml })
    assert.notEqual(result.status, 0, applicationYaml)
    assert.match(result.stderr + result.stdout, /application.*version|opc.*mapping|duplicate|scalar|tab|indentation/i)
  }
})

test('backend coverage uses a measured aggregate line baseline', async () => {
  const pom = await repositoryFile('pom.xml')
  const coveragePom = await repositoryFile('opc-coverage/pom.xml')
  const verifier = await repositoryFile('bin/verify-foundation.ps1')

  assert.match(pom, /<jacoco\.version>\d+\.\d+\.\d+<\/jacoco\.version>/)
  const minimum = pom.match(/<jacoco\.minimum\.line\.ratio>(0\.\d+)<\/jacoco\.minimum\.line\.ratio>/)
  assert.ok(minimum, 'the aggregate LINE threshold must be a POM property')
  assert.ok(Number(minimum[1]) > 0 && Number(minimum[1]) < 1)
  assert.match(pom, /<artifactId>jacoco-maven-plugin<\/artifactId>/)
  assert.match(pom, /<goal>prepare-agent<\/goal>/)
  assert.match(pom, /<module>opc-coverage<\/module>\s*<\/modules>/)
  assert.match(coveragePom, /<goal>report-aggregate<\/goal>/)
  assert.match(coveragePom, /<formats>[\s\S]*<format>HTML<\/format>[\s\S]*<format>XML<\/format>[\s\S]*<\/formats>/)
  for (const module of ['opc-admin', 'opc-framework', 'opc-system', 'opc-quartz', 'opc-generator', 'opc-common']) {
    assert.match(coveragePom, new RegExp(`<dependency>[\\s\\S]*?<artifactId>${module}</artifactId>[\\s\\S]*?<version>\\$\\{opc\\.version\\}</version>[\\s\\S]*?</dependency>`))
  }
  assert.match(verifier, /mvnw\.cmd['"]?\)?\s+clean\s+verify/)
  assert.match(verifier, /verify-aggregate-coverage\.ps1/)
})

test('CI always uploads the real aggregate JaCoCo report with a pinned action', async () => {
  const workflow = await repositoryFile('.github/workflows/ci.yml')
  const document = await repositoryYaml('.github/workflows/ci.yml')
  const upload = document.jobs.verify.steps.find(step => step.uses?.startsWith('actions/upload-artifact@'))

  assert.ok(upload, 'CI must upload the aggregate coverage report')
  assert.equal(upload.if, 'always()')
  assert.equal(upload.uses, `actions/upload-artifact@${PINNED_ACTIONS['actions/upload-artifact'].sha}`)
  assert.equal(upload.with.name, 'jacoco-aggregate-report')
  assert.equal(upload.with.path, 'opc-coverage/target/site/jacoco-aggregate')
  assert.match(workflow, new RegExp(`uses:\\s*actions/upload-artifact@${PINNED_ACTIONS['actions/upload-artifact'].sha}\\s+# ${PINNED_ACTIONS['actions/upload-artifact'].version}`))
})

async function runAggregateCoverageFixture({
  missed,
  covered,
  groups = 6,
  groupNames,
  emptyPackageIndex = -1,
  threshold = '0.20'
}) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'opc-aggregate-coverage-'))
  const pomPath = join(fixtureRoot, 'pom.xml')
  const reportPath = join(fixtureRoot, 'jacoco.xml')
  const names = groupNames ?? Array.from({ length: groups }, (_, index) => `module-${index}`)
  const groupXml = names.map((name, index) =>
    `<group name="${name}">${index === emptyPackageIndex ? '' : `<package name="com/opc/module${index}" />`}</group>`
  ).join('')
  await writeFile(pomPath, [
    '<project xmlns="http://maven.apache.org/POM/4.0.0">',
    `<properties><jacoco.minimum.line.ratio>${threshold}</jacoco.minimum.line.ratio></properties>`,
    '<modules>',
    ...Array.from({ length: 6 }, (_, index) => `<module>module-${index}</module>`),
    '<module>opc-coverage</module>',
    '</modules>',
    '</project>'
  ].join(''), 'utf8')
  await writeFile(reportPath, [
    '<report name="aggregate">', groupXml,
    `<counter type="INSTRUCTION" missed="${missed}" covered="${covered}" />`,
    `<counter type="LINE" missed="${missed}" covered="${covered}" />`,
    '</report>'
  ].join(''), 'utf8')

  const result = spawnSync(powerShellExecutable, [
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', fileURLToPath(new URL('../bin/verify-aggregate-coverage.ps1', import.meta.url)),
    '-ReportPath', reportPath,
    '-PomPath', pomPath
  ], { encoding: 'utf8' })
  await rm(fixtureRoot, { recursive: true, force: true })
  return result
}

test('aggregate coverage verifier accepts a populated multi-module report above the POM baseline', async () => {
  const result = await runAggregateCoverageFixture({ missed: 70, covered: 30 })

  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.match(result.stdout, /30\.00%/)
  assert.match(result.stdout, /20\.00%/)
})

test('aggregate coverage verifier rejects empty, below-baseline, or incomplete reports', async () => {
  const empty = await runAggregateCoverageFixture({ missed: 0, covered: 0 })
  const belowBaseline = await runAggregateCoverageFixture({ missed: 90, covered: 10 })
  const missingModule = await runAggregateCoverageFixture({ missed: 70, covered: 30, groups: 5 })

  assert.notEqual(empty.status, 0)
  assert.match(empty.stderr + empty.stdout, /empty/i)
  assert.notEqual(belowBaseline.status, 0)
  assert.match(belowBaseline.stderr + belowBaseline.stdout, /below/i)
  assert.notEqual(missingModule.status, 0)
  assert.match(missingModule.stderr + missingModule.stdout, /missing.*module-5|module set/i)
})

test('aggregate coverage verifier rejects an expected module without packages', async () => {
  const result = await runAggregateCoverageFixture({ missed: 70, covered: 30, emptyPackageIndex: 3 })

  assert.notEqual(result.status, 0)
  assert.match(result.stderr + result.stdout, /no business packages.*module-3/i)
})

test('aggregate coverage verifier rejects thresholds outside the inclusive upper unit interval', async () => {
  for (const threshold of ['-0.01', '0', '1.01']) {
    const result = await runAggregateCoverageFixture({ missed: 0, covered: 100, threshold })

    assert.notEqual(result.status, 0, `threshold ${threshold} must fail`)
    assert.match(result.stderr + result.stdout, /greater than 0.*at most 1/i)
  }
})

test('aggregate coverage verifier rejects empty and duplicate group names', async () => {
  const emptyName = await runAggregateCoverageFixture({
    missed: 70,
    covered: 30,
    groupNames: ['module-0', 'module-1', '', 'module-3', 'module-4', 'module-5']
  })
  const duplicateName = await runAggregateCoverageFixture({
    missed: 70,
    covered: 30,
    groupNames: ['module-0', 'module-1', 'module-2', 'module-3', 'module-4', 'module-4']
  })

  assert.notEqual(emptyName.status, 0)
  assert.match(emptyName.stderr + emptyName.stdout, /group names.*empty/i)
  assert.notEqual(duplicateName.status, 0)
  assert.match(duplicateName.stderr + duplicateName.stdout, /duplicate.*module-4/i)
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
  assert.match(workflow, /node-version:\s*['"]?24\.15\.0/)
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
