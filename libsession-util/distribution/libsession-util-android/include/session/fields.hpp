#pragma once

#include <array>
#include <chrono>
#include <cstdint>
#include <string>

namespace session {

using namespace std::literals;

/// An uploaded file is its URL + decryption key
struct Uploaded {
    std::string url;
    std::string key;
};

/// A conversation disappearing messages setting
struct Disappearing {
    /// The possible modes of a disappearing messages setting.
    enum class Mode : int { None = 0, AfterSend = 1, AfterRead = 2 };

    /// The mode itself
    Mode mode = Mode::None;

    /// The timer value; this is only used when mode is not None.
    std::chrono::seconds timer = 0s;
};

/// A Session ID: an x25519 pubkey, with a 05 identifying prefix.  On the wire we send just the
/// 32-byte pubkey value (i.e. not hex, without the prefix).
struct SessionID {
    /// The fixed session netid, 0x05
    static constexpr unsigned char netid = 0x05;

    /// The raw x25519 pubkey, as bytes
    std::array<unsigned char, 32> pubkey;

    /// Returns the full pubkey in hex, including the netid prefix.
    std::string hex() const;
};

}  // namespace session
