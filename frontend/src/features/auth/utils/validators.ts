import type { FormItemRule } from 'element-plus'

/** 用户名：3-32 位字母、数字或下划线（与后端一致） */
export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,32}$/
/** 密码：8-32 位，必须同时含字母和数字，不允许空格（与后端一致） */
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[^\s]{8,32}$/
/** 邮箱 */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isUsernameValid(value: string): boolean {
  return USERNAME_PATTERN.test(value)
}

export function isPasswordValid(value: string): boolean {
  return PASSWORD_PATTERN.test(value)
}

export function isEmailValid(value: string): boolean {
  return EMAIL_PATTERN.test(value)
}

/** 必填规则 */
export function requiredRule(message: string, trigger: 'blur' | 'change' = 'blur'): FormItemRule {
  return { required: true, message, trigger }
}

/** 格式规则（值为空时交给必填规则，不重复报错） */
export function patternRule(
  message: string,
  test: (value: string) => boolean,
  trigger: 'blur' | 'change' = 'blur',
): FormItemRule {
  return {
    trigger,
    validator: (_rule, value, callback) => {
      if (!value || typeof value !== 'string') {
        callback()
        return
      }
      if (!test(value)) {
        callback(new Error(message))
        return
      }
      callback()
    },
  }
}

/** 确认密码规则（与密码字段比较） */
export function confirmPasswordRule(
  getPassword: () => string,
  message = '两次输入的密码不一致',
): FormItemRule {
  return {
    trigger: 'blur',
    validator: (_rule, value, callback) => {
      if (!value || typeof value !== 'string') {
        callback()
        return
      }
      if (value !== getPassword()) {
        callback(new Error(message))
        return
      }
      callback()
    },
  }
}

/** 协议勾选规则 */
export function agreementRule(message: string): FormItemRule {
  return {
    trigger: 'change',
    validator: (_rule, value, callback) => {
      if (!value) {
        callback(new Error(message))
        return
      }
      callback()
    },
  }
}

/** 把秒数转成易读的等待时间 */
export function formatWait(seconds: number): string {
  if (seconds <= 0) return '稍后'
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.ceil(seconds / 60)
  return `${minutes} 分钟`
}
