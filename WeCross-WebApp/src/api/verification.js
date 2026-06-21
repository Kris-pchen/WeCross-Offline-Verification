import request from '@/utils/request'

export function getVerificationHealth() {
  return request({
    url: '/api/verification/health',
    method: 'get'
  })
}

export function verifyMerkle(data) {
  return request({
    url: '/api/verification/merkle',
    method: 'post',
    data
  })
}

export function verifyGroth16(data) {
  return request({
    url: '/api/verification/groth16',
    method: 'post',
    data
  })
}

export function verifyThresholdSignature(data) {
  return request({
    url: '/api/verification/threshold-signature',
    method: 'post',
    data
  })
}

export function verifyAll(data) {
  return request({
    url: '/api/verification/full',
    method: 'post',
    data
  })
}
