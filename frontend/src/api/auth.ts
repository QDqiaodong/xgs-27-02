import axios from './axios'
import type { LoginResponse } from '@/types'

export const login = (username: string, password: string): Promise<LoginResponse> => {
  return axios.post('/auth/login', { username, password })
}