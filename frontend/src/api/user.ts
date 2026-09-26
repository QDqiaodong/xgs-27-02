import axios from './axios'
import type { UserResponse, CreateUserRequest } from '@/types'

export const getUsers = (): Promise<UserResponse[]> => {
  return axios.get('/users') as Promise<UserResponse[]>
}

export const getUserById = (id: number): Promise<UserResponse> => {
  return axios.get(`/users/${id}`) as Promise<UserResponse>
}

export const createUser = (data: CreateUserRequest): Promise<UserResponse> => {
  return axios.post('/users', data) as Promise<UserResponse>
}

export const updateUser = (id: number, data: CreateUserRequest): Promise<UserResponse> => {
  return axios.put(`/users/${id}`, data) as Promise<UserResponse>
}

export const deleteUser = (id: number): Promise<void> => {
  return axios.delete(`/users/${id}`) as Promise<void>
}

export const getCurrentUser = (): Promise<UserResponse> => {
  return axios.get('/users/me') as Promise<UserResponse>
}

export const changePassword = (oldPassword: string, newPassword: string): Promise<void> => {
  return axios.post('/users/me/change-password', { oldPassword, newPassword }) as Promise<void>
}