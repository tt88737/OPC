import assert from 'node:assert/strict'
import test from 'node:test'

async function loadPolicy() {
  const policy = await import('../src/utils/passwordPolicy.js')
  return policy
}

test('accepts passwords at the code point and UTF-8 byte boundaries', async () => {
  const { validatePasswordBounds } = await loadPolicy()

  assert.equal(validatePasswordBounds('abcde'), undefined)
  assert.equal(validatePasswordBounds('a'.repeat(64)), undefined)
  assert.equal(validatePasswordBounds('a'.repeat(48) + 'é'.repeat(12)), undefined)
})

test('rejects passwords outside 5..64 code points', async () => {
  const { validatePasswordBounds, PASSWORD_BOUNDS_MESSAGE } = await loadPolicy()

  assert.equal(validatePasswordBounds('abcd'), PASSWORD_BOUNDS_MESSAGE)
  assert.equal(validatePasswordBounds('a'.repeat(65)), PASSWORD_BOUNDS_MESSAGE)
  assert.equal(validatePasswordBounds('😀'.repeat(65)), PASSWORD_BOUNDS_MESSAGE)
})

test('counts supplementary characters as code points instead of UTF-16 units', async () => {
  const { validatePasswordBounds } = await loadPolicy()

  assert.equal(validatePasswordBounds('😀'.repeat(5)), undefined)
})

test('accepts 72 UTF-8 bytes and rejects 73 UTF-8 bytes', async () => {
  const { validatePasswordBounds, PASSWORD_BYTES_MESSAGE } = await loadPolicy()
  const atLimit = 'a'.repeat(48) + 'é'.repeat(12)
  const overLimit = `${atLimit}b`

  assert.equal(Buffer.byteLength(atLimit, 'utf8'), 72)
  assert.equal(Buffer.byteLength(overLimit, 'utf8'), 73)
  assert.equal(validatePasswordBounds(atLimit), undefined)
  assert.equal(validatePasswordBounds(overLimit), PASSWORD_BYTES_MESSAGE)
})
