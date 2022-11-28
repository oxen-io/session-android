#pragma once

#include <oxenc/bt_serialize.h>
#include <oxenc/bt_value.h>

#include <array>
#include <cassert>
#include <optional>
#include <set>
#include <stdexcept>
#include <variant>
#include <vector>

namespace session::config {

inline constexpr int MAX_MESSAGE_SIZE = 76800;  // 76.8kB = Storage server's limit

// Application data data types:
using scalar = std::variant<int64_t, std::string>;

using set = std::set<scalar>;
struct dict_value;
using dict = std::map<std::string, dict_value>;
using dict_variant = std::variant<dict, set, scalar>;
struct dict_value : dict_variant {
    using dict_variant::dict_variant;
    using dict_variant::operator=;
};

// Helpers for gcc-10 and earlier which don't like visiting a std::variant subtype:
constexpr inline dict_variant& unwrap(dict_value& v) {
    return static_cast<dict_variant&>(v);
}
constexpr inline const dict_variant& unwrap(const dict_value& v) {
    return static_cast<const dict_variant&>(v);
}

using seqno_t = std::int64_t;
using hash_t = std::array<unsigned char, 32>;
using seqno_hash_t = std::pair<seqno_t, hash_t>;

using ustring = std::basic_string<unsigned char>;
using ustring_view = std::basic_string_view<unsigned char>;

class MutableConfigMessage;

/// Base type for all errors that can happen during config parsing
struct config_error : std::runtime_error {
    using std::runtime_error::runtime_error;
};
/// Type thrown for bad signatures (bad or missing signature).
struct signature_error : config_error {
    using config_error::config_error;
};
/// Type thrown for a missing signature when a signature is required.
struct missing_signature : signature_error {
    using signature_error::signature_error;
};
/// Type thrown for an unparseable config (e.g. keys with invalid types, or keys before "#" or after
/// "~").
struct config_parse_error : config_error {
    using config_error::config_error;
};

/// Class for a parsed, read-only config message; also serves as the base class of a
/// MutableConfigMessage which allows setting values.
class ConfigMessage {
  public:
    using lagged_diffs_t = std::map<seqno_hash_t, oxenc::bt_dict>;

#ifndef SESSION_TESTING_EXPOSE_INTERNALS
  protected:
#endif
    dict data_;

    // diff data for *this* message, parsed during construction.  Subclasses may use this for
    // managing their own diff in the `diff()` method.
    oxenc::bt_dict diff_;

    // diffs of previous messages that are included in this message.
    lagged_diffs_t lagged_diffs_;

    // Unknown top-level config keys which we preserve even though we don't understand what they
    // mean.
    oxenc::bt_dict unknown_;

    /// Seqno and hash of the message; we calculate this when loading.  Subclasses put the hash here
    /// (so that they can return a reference to it).
    seqno_hash_t seqno_hash_{0, {0}};

    bool verified_signature_ = false;

    bool merged_ = false;

  public:
    constexpr static int DEFAULT_DIFF_LAGS = 5;

    /// Verification function: this is passed the data that should have been signed and the 64-byte
    /// signature.  Should return true to accept the signature, false to reject it and skip the
    /// message.  It can also throw to abort message construction (that is: returning false skips
    /// the message when loading multiple messages, but can still continue with other messages;
    /// throwing aborts the entire construction).
    using verify_callable = std::function<bool(ustring_view data, ustring_view signature)>;

    /// Signing function: this is passed the data to be signed and returns the 64-byte signature.
    using sign_callable = std::function<std::string(ustring_view data)>;

    ConfigMessage();
    ConfigMessage(const ConfigMessage&) = default;
    ConfigMessage& operator=(const ConfigMessage&) = default;
    ConfigMessage(ConfigMessage&&) = default;
    ConfigMessage& operator=(ConfigMessage&&) = default;

    virtual ~ConfigMessage() = default;

    /// Initializes a config message by parsing a serialized message.  Throws on any error.  See the
    /// vector version below for argument descriptions.
    explicit ConfigMessage(
            std::string_view serialized,
            verify_callable verifier = nullptr,
            sign_callable signer = nullptr,
            int lag = DEFAULT_DIFF_LAGS,
            bool signature_optional = false);

    /// Constructs a new ConfigMessage by loading and potentially merging multiple serialized
    /// ConfigMessages together, according to the config conflict resolution rules.  The result
    /// of this call can either be one of the config messages directly (if one is found that
    /// includes all the others), or can be a new config message that merges multiple configs
    /// together.  You can check `.merged()` to see which happened.
    ///
    /// This constructor always requires at least one valid config from the given inputs; if all are
    /// empty,
    ///
    /// verifier - a signature verification function.  If provided and not nullptr this will be
    /// called to verify each signature in the provided messages: any that are missing a signature
    /// or for which the verifier returns false will be dropped from consideration for merging.  If
    /// *all* messages fail verification an exception is raised.
    ///
    /// signer - a signature generation function.  This is not used directly by the ConfigMessage,
    /// but providing it will allow it to be passed automatically to any MutableConfigMessage
    /// derived from this ConfigMessage.
    ///
    /// lag - the lag setting controlling the config merging rules.  Any config message with lagged
    /// diffs that exceeding this lag value will have those early lagged diffs dropping during
    /// loading.
    ///
    /// signature_optional - if true then accept a message with no signature even when a verifier is
    /// set, thus allowing unsigned messages (though messages with an invalid signature are still
    /// not allowed).  This option is ignored when verifier is not set.
    ///
    /// error_callback - if set then any config message parsing error will be passed to this
    /// function for handling: the callback typically warns and, if the overall construction should
    /// abort, rethrows the error.  If this function is omitted then the default skips (without
    /// failing) individual parse errors and only aborts construction if *all* messages fail to
    /// parse.  A simple handler such as `[](const auto& e) { throw e; }` can be used to make any
    /// parse error of any message fatal.
    explicit ConfigMessage(
            const std::vector<std::string_view>& configs,
            verify_callable verifier = nullptr,
            sign_callable signer = nullptr,
            int lag = DEFAULT_DIFF_LAGS,
            bool signature_optional = false,
            std::function<void(const config_error&)> error_handler = nullptr);

    /// Returns a read-only reference to the contained data.  (To get a mutable config object use
    /// MutableConfigMessage).
    const dict& data() const { return data_; }

    /// The verify function; if loading a message with a signature and this is set then it will
    /// be called to verify the signature of the message.  Takes a pointer to the signing data,
    /// the data length, and a pointer to the 64-byte signature.
    verify_callable verifier;

    /// The signing function; this is not directly used by the non-mutable base class, but will be
    /// propagated to mutable config messages that are derived e.g. by calling `.increment()`.  This
    /// is called when serializing a config message to add a signature.  If it is nullptr then no
    /// signature is added to the serialized data.
    sign_callable signer;

    /// How many lagged config diffs that should be carried forward to resolve conflicts,
    /// including this message.  If 0 then config messages won't have any diffs and will not be
    /// mergeable.
    int lag = DEFAULT_DIFF_LAGS;

    /// The diff structure for changes in *this* config message.  Subclasses that need to override
    /// should populate into `diff_` and return a reference to it (internal code assumes `diff_` is
    /// correct immediately after a call to this).
    virtual const oxenc::bt_dict& diff();

    /// Returns the seqno of this message
    const seqno_t& seqno() const { return seqno_hash_.first; }

    /// Calculates the hash of the current message.  For a ConfigMessage this is calculated when the
    /// message is first loaded; for a MutableConfigMessage this serializes the current value to
    /// properly compute the current hash.  Subclasses must ensure that seqno_hash_.second is set to
    /// the correct value when this is called (and typically return a reference to it).
    virtual const hash_t& hash() { return seqno_hash_.second; }

    /// After loading multiple config files this flag indicates whether or not we had to produce a
    /// new, merged configuration message (true) or did not need to merge (false).  (For config
    /// messages that were not loaded from serialized data this is always true).
    bool merged() const { return merged_; }

    /// Returns true if this message contained a valid, verified signature when it was parsed.
    /// Returns false otherwise (e.g. not loaded from verification at all; loaded without a
    /// verification function; or had no signature and a signature wasn't required).
    bool verified_signature() const { return verified_signature_; }

    /// Constructs a new MutableConfigMessage from this config message with an incremented seqno.
    /// The new config message's diff will reflect changes made after this construction.
    virtual MutableConfigMessage increment() const;

    /// Serializes this config's data.  Note that if the ConfigMessage was constructed from signed,
    /// serialized input, this will only produce an exact copy of the original serialized input if
    /// it uses the identical, deterministic signing function used to construct the original.
    ///
    /// The optional `enable_signing` argument can be specified as false to disable signing (this is
    /// typically for a local serialization value that isn't being pushed to the server).  Note that
    /// signing is always disabled if there is no signing callback set, regardless of the value of
    /// this argument.
    virtual std::string serialize(bool enable_signing = true);

  protected:
    std::string serialize_impl(const oxenc::bt_dict& diff, bool enable_signing = true);
};

// Constructor tag
struct increment_seqno_t {};
inline constexpr increment_seqno_t increment_seqno{};

class MutableConfigMessage : public ConfigMessage {
  protected:
    dict orig_data_{data_};

    friend class ConfigMessage;

  public:
    MutableConfigMessage(const MutableConfigMessage&) = default;
    MutableConfigMessage& operator=(const MutableConfigMessage&) = default;
    MutableConfigMessage(MutableConfigMessage&&) = default;
    MutableConfigMessage& operator=(MutableConfigMessage&&) = default;

    /// Constructs a new, empty config message.  Takes various fields to pre-fill the various
    /// properties during construction (these are for convenience and equivalent to setting them via
    /// properties/methods after construction).
    ///
    /// seqno -- the message's seqno, default 0
    /// lags -- number of lags to keep (when deriving messages, e.g. via increment())
    /// signer -- if specified and not nullptr then this message will be signed when serialized
    /// using the given signing function.  If omitted no signing takes place.
    explicit MutableConfigMessage(
            seqno_t seqno = 0, int lag = DEFAULT_DIFF_LAGS, sign_callable signer = nullptr) {
        this->lag = lag;
        this->seqno(seqno);
        this->signer = signer;
    }

    /// Wraps the ConfigMessage constructor with the same arguments but always produces a
    /// MutableConfigMessage.  In particular this means that if the base constructor performed a
    /// merge (and thus incremented seqno) then the config stays as is, but contained in a Mutable
    /// message that can be changed.  If it did *not* merge (i.e. the highest seqno message it found
    /// did not conflict with any other messages) then this construction is equivalent to doing a
    /// base load followed by a .increment() call.  In other words: this constructor *always* gives
    /// you an incremented seqno value from the highest valid input config message.
    ///
    /// This is almost equivalent to ConfigMessage{args...}.increment(), except that this
    /// constructor only increments seqno once while the indirect version would increment twice in
    /// the case of a required merge conflict resolution.
    explicit MutableConfigMessage(
            const std::vector<std::string_view>& configs,
            verify_callable verifier = nullptr,
            sign_callable signer = nullptr,
            int lag = DEFAULT_DIFF_LAGS,
            bool signature_optional = false,
            std::function<void(const config_error&)> error_handler = nullptr);

    /// Wrapper around the above that takes a single string view to load a single message, doesn't
    /// take an error handler and instead always throws on parse errors (the above also throws for
    /// an erroneous single message, but with a less specific "no valid config messages" error).
    explicit MutableConfigMessage(
            std::string_view config,
            verify_callable verifier = nullptr,
            sign_callable signer = nullptr,
            int lag = DEFAULT_DIFF_LAGS,
            bool signature_optional = false);

    /// Does the same as the base incrementing, but also records any diff info from the current
    /// MutableConfigMessage.  *this* object gets pruned and signed as part of this call.  If the
    /// sign argument is omitted/nullptr then the current object's `sign` callback gets copied into
    /// the new object.  After this call you typically do not want to further modify *this (because
    /// any modifications will change the hash, making *this no longer a parent of the new object).
    MutableConfigMessage increment() const override;

    /// Constructor that does the same thing as the `m.increment()` factory method.  The second
    /// value should be the literal `increment_seqno` value (to select this constructor).
    explicit MutableConfigMessage(const ConfigMessage& m, increment_seqno_t);

    using ConfigMessage::data;
    /// Returns a mutable reference to the underlying config data.
    dict& data() { return data_; }

    using ConfigMessage::seqno;

    /// Sets the seqno of the message to a specific value.  You usually want to use `.increment()`
    /// from an existing config message rather than manually adjusting the seqno.
    void seqno(seqno_t new_seqno) { seqno_hash_.first = new_seqno; }

    /// Returns the current diff for this data relative to its original data.  The data is pruned
    /// implicitly by this call.
    const oxenc::bt_dict& diff() override;

    /// Prunes empty dicts/sets from data.  This is called automatically when serializing or
    /// calculating a diff.  Returns true if the data was actually changed, false if nothing needed
    /// pruning.
    bool prune();

    /// Calculates the hash of the current message.  Can optionally be given the already-serialized
    /// value, if available; if empty/omitted, `serialize()` will be called to compute it.
    const hash_t& hash() override;

  protected:
    const hash_t& hash(std::string_view serialized);
    void increment_impl();
};

/// Encrypts a config message using XChaCha20-Poly1305, using a blake2b keyed hash of the message
/// for the nonce (rather than pure random) so that different clients will encrypt the same data to
/// the same encrypted value (thus allowing for server-side deduplication of identical messages).
///
/// `key_base` must be 32 bytes.  This value is a fixed key that all clients that might receive this
/// message can calculate independently (for instance a value derived from a secret key, or a shared
/// random key).  This key will be hashed with the message size and domain suffix (see below) to
/// determine the actual encryption key.
///
/// `domain` is a short string (1-24 chars) used for the keyed hash.  Typically this is the type of
/// config, e.g. "closed-group" or "contacts".  The full key will be
/// "session-config-encrypted-message-[domain]".  This value is also used for the encrypted key (see
/// above).
///
/// The returned result will consist of encrypted data with authentication tag and appended nonce,
/// suitable for being passed to decrypt() to authenticate and decrypt.
///
/// Throw std::invalid_argument on bad input (i.e. from invalid key_base or domain).
ustring encrypt(ustring_view message, ustring_view key_base, std::string_view domain);

/// Same as above but works with strings/string_views instead of ustring/ustring_view
std::string encrypt(std::string_view message, std::string_view key_base, std::string_view domain);

/// Thrown if decrypt() fails.
struct decrypt_error : std::runtime_error {
    using std::runtime_error::runtime_error;
};

/// Takes a value produced by `encrypt()` and decrypts it.  `key_base` and `domain` must be the same
/// given to encrypt or else decryption fails.  Upon decryption failure a std::
ustring decrypt(ustring_view ciphertext, ustring_view key_base, std::string_view domain);

/// Same as above but using std::string/string_view
std::string decrypt(
        std::string_view ciphertext, std::string_view key_base, std::string_view domain);

}  // namespace session::config

namespace oxenc::detail {

template <>
struct bt_serialize<session::config::dict_value> : bt_serialize<session::config::dict_variant> {};

}  // namespace oxenc::detail
