#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/// XEd25519-signed a message given a curve25519 privkey and message.  Writes the 64-byte signature
/// to `sig` on success and returns 0.  Returns non-zero on failure.
__attribute__((warn_unused_result)) int session_xed25519_sign(
        unsigned char* signature /* 64 byte buffer */,
        const unsigned char* curve25519_privkey /* 32 bytes */,
        const unsigned char* msg,
        const unsigned int msg_len);

/// Verifies an XEd25519-signed message given a 64-byte signature, 32-byte curve25519 pubkey, and
/// message.  Returns 0 if the signature verifies successfully, non-zero on failure.
__attribute__((warn_unused_result)) int session_xed25519_verify(
        const unsigned char* signature /* 64 bytes */,
        const unsigned char* pubkey /* 32-bytes */,
        const unsigned char* msg,
        const unsigned int msg_len);

/// Given a curve25519 pubkey, this writes the associated XEd25519-derived Ed25519 pubkey into
/// ed25519_pubkey.  Note, however, that there are *two* possible Ed25519 pubkeys that could result
/// in a given curve25519 pubkey: this always returns the positive value.  You can get the other
/// possibility (the negative) by flipping the sign bit, i.e. `returned_pubkey[31] |= 0x80`.
/// Returns 0 on success, non-0 on failure.
__attribute__((warn_unused_result)) int session_xed25519_pubkey(
        unsigned char* ed25519_pubkey /* 32-byte output buffer */,
        const unsigned char* curve25519_pubkey /* 32 bytes */);

#ifdef __cplusplus
}
#endif
