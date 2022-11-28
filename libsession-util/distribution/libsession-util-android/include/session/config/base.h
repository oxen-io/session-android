#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32) || defined(WIN32)
#define LIBSESSION_EXPORT __declspec(dllexport)
#else
#define LIBSESSION_EXPORT __attribute__((visibility("default")))
#endif
#define LIBSESSION_C_API extern "C" LIBSESSION_EXPORT

typedef int64_t seqno_t;

// Config object base type: this type holds the internal object and is initialized by the various
// config-dependent settings (e.g. config_user_profile_init) then passed to the various functions.
typedef struct config_object {
    // Internal opaque object pointer; calling code should leave this alone.
    void* internals;
    // When an error occurs in the C API this string will be set to the specific error message.  May
    // be NULL.
    const char* last_error;
} config_object;

// Common functions callable on any config instance:

/// Frees a config object created with one of the config-dependent ..._init functions (e.g.
/// user_profile_init).
void config_free(config_object* conf);

/// Returns the numeric namespace in which config messages of this type should be stored.
int16_t config_storage_namespace(const config_object* conf);

/// Merges the config object with one or more remotely obtained config strings.  After this call the
/// config object may be unchanged, complete replaced, or updated and needing a push, depending on
/// the messages that are merged; the caller should check config_needs_push().
///
/// `configs` is an array of pointers to the start of the strings; `lengths` is an array of string
/// lengths; `count` is the length of those two arrays.
void config_merge(config_object* conf, const char** configs, const size_t* lengths, size_t count);

/// Returns true if this config object contains updated data that has not yet been confirmed stored
/// on the server.
bool config_needs_push(const config_object* conf);

/// Obtains the configuration data that needs to be pushed to the server.  A new buffer of the
/// appropriate size is malloc'd and set to `out` The output is written to a new malloc'ed buffer of
/// the appropriate size; the buffer and the output length are set in the `out` and `outlen`
/// parameters.  Note that this is binary data, *not* a null-terminated C string.
///
/// Generally this call should be guarded by a call to `config_needs_push`, however it can be used
/// to re-obtain the current serialized config even if no push is needed (for example, if the client
/// wants to re-submit it after a network error).
///
/// NB: The returned buffer belongs to the caller: that is, the caller *MUST* free() it when done
/// with it.
seqno_t config_push(config_object* conf, char** out, size_t* outlen);

/// Reports that data obtained from `config_push` has been successfully stored on the server.  The
/// seqno value is the one returned by the config_push call that yielded the config data.
void config_confirm_pushed(config_object* conf, seqno_t seqno);

/// Returns a binary dump of the current state of the config object.  This dump can be used to
/// resurrect the object at a later point (e.g. after a restart).  Allocates a new buffer and sets
/// it in `out` and the length in `outlen`.  Note that this is binary data, *not* a null-terminated
/// C string.
///
/// NB: It is the caller's responsibility to `free()` the buffer when done with it.
///
/// Immediately after this is called `config_needs_dump` will start returning true (until the
/// configuration is next modified).
void config_dump(config_object* conf, char** out, size_t* outlen);

/// Returns true if something has changed since the last call to `dump()` that requires calling
/// and saving the `config_dump()` data again.
bool config_needs_dump(const config_object* conf);

#ifdef __cplusplus
}  // extern "C"
#endif
