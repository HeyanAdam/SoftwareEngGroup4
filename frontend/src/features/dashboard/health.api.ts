import { request } from '../../shared/api/request'

export async function getHealth() {
  const response = await request.get('/health/ready')
  return response.data
}
