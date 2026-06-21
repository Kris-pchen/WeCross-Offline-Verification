import JSEncrypt from 'jsencrypt'

/**
 * rsa encrypt
 */
export function rsa_encode(input, pub) {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(pub)
  return encryptor.encrypt(input)
}
