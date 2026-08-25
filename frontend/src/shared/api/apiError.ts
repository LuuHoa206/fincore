import axios from 'axios'

type ApiErrorBody = {
  message?: string
  fieldErrors?: Record<string, string>
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError<ApiErrorBody>(error)) {
    return fallback
  }
  if (!error.response) {
    return 'Không thể kết nối máy chủ. Vui lòng kiểm tra backend và thử lại.'
  }
  const fieldMessage = Object.values(error.response.data?.fieldErrors ?? {})[0]
  return fieldMessage ?? error.response.data?.message ?? fallback
}
