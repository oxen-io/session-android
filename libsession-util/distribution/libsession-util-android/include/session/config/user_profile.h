#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include "base.h"

/// Constructs a user profile config object and sets a pointer to it in `conf`.  To restore an
/// existing dump produced by a past instantiation's call to `dump()` pass the dump value via `dump`
/// and `dumplen`; to construct a new, empty profile pass NULL and 0.
///
/// `error` must either be NULL or a pointer to a buffer of at least 256 bytes.
///
/// Returns 0 on success; returns a non-zero error code and sets error (if not NULL) to the
/// exception message on failure.
///
/// When done with the object the `config_object` must be destroyed by passing the pointer to
/// config_free() (in `session/config/base.h`).
int user_profile_init(config_object** conf, const char* dump, size_t dumplen, char* error)
        __attribute__((warn_unused_result));

/// Returns a pointer to the currently-set name (null-terminated), or NULL if there is no name at
/// all.  Should be copied right away as the pointer may not remain valid beyond other API calls.
const char* user_profile_get_name(const config_object* conf);

/// Sets the user profile name to the null-terminated C string.  Returns 0 on success, non-zero on
/// error (and sets the config_object's error string).
int user_profile_set_name(config_object* conf, const char* name);

typedef struct user_profile_pic {
    // Null-terminated C string containing the uploaded URL of the pic.  Will be NULL if there is no
    // profile pic.
    const char* url;
    // The profile pic decryption key, in bytes.  This is a byte buffer of length `keylen`, *not* a
    // null-terminated C string.  Will be NULL if there is no profile pic.
    const char* key;
    size_t keylen;
} user_profile_pic;

// Obtains the current profile pic.  The pointers in the returned struct will be NULL if a profile
// pic is not currently set, and otherwise should be copied right away (they will not be valid
// beyond other API calls on this config object).
user_profile_pic user_profile_get_pic(const config_object* conf);

// Sets a user profile
int user_profile_set_pic(config_object* conf, user_profile_pic pic);

#ifdef __cplusplus
}  // extern "C"
#endif
