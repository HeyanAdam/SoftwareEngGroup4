import axios from 'axios'

export const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error),
)
