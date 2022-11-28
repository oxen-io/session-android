#pragma once

#ifdef __cplusplus
extern "C" {
#endif

enum config_error {
    /// Value returned for no error
    SESSION_ERR_NONE = 0,
    /// Error indicating that initialization failed because the dumped data being loaded is invalid.
    SESSION_ERR_INVALID_DUMP = 1,
    /// Error indicated a bad value, e.g. if trying to set something invalid in a config field.
    SESSION_ERR_BAD_VALUE = 2,
};

// Returns a generic string for a given integer error code as returned by some functions.  Depending
// on the call, a more details error string may be available in the config_object's `last_error`
// field.
const char* config_errstr(int err);

#ifdef __cplusplus
}  // extern "C"
#endif
