export const PASSWORD_MIN_CODE_POINTS = 5
export const PASSWORD_MAX_CODE_POINTS = 64
export const PASSWORD_MAX_UTF8_BYTES = 72
export const PASSWORD_BOUNDS_MESSAGE = '密码长度必须介于 5 和 64 个字符之间'
export const PASSWORD_BYTES_MESSAGE = '密码的 UTF-8 编码不能超过 72 字节'

const utf8Encoder = new TextEncoder()

export function validatePasswordBounds(value) {
  const codePointCount = Array.from(value || '').length
  if (codePointCount < PASSWORD_MIN_CODE_POINTS || codePointCount > PASSWORD_MAX_CODE_POINTS) {
    return PASSWORD_BOUNDS_MESSAGE
  }
  if (utf8Encoder.encode(value).length > PASSWORD_MAX_UTF8_BYTES) {
    return PASSWORD_BYTES_MESSAGE
  }
}

export function passwordBoundsValidator(_rule, value, callback) {
  const message = validatePasswordBounds(value)
  if (message) {
    callback(new Error(message))
    return
  }
  callback()
}
