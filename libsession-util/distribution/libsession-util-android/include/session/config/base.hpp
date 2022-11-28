#pragma once

#include <memory>
#include <session/config.hpp>
#include <type_traits>
#include <variant>

#include "base.h"
#include "namespaces.hpp"

namespace session::config {

template <typename T, typename... U>
static constexpr bool is_one_of = (std::is_same_v<T, U> || ...);

/// True for a dict_value direct subtype, but not scalar sub-subtypes.
template <typename T>
static constexpr bool is_dict_subtype = is_one_of<T, config::scalar, config::set, config::dict>;

/// True for a dict_value or any of the types containable within a dict value
template <typename T>
static constexpr bool is_dict_value =
        is_dict_subtype<T> || is_one_of<T, dict_value, int64_t, std::string>;

// Levels for the logging callback
enum class LogLevel { debug, info, warning, error };

/// Our current config state
enum class ConfigState : int {
    /// Clean means the config is confirmed stored on the server and we haven't changed anything.
    Clean = 0,

    /// Dirty means we have local changes, and the changes haven't been serialized yet for sending
    /// to the server.
    Dirty = 1,

    /// Waiting is halfway in-between clean and dirty: the caller has serialized the data, but
    /// hasn't yet reported back that the data has been stored, *and* we haven't made any changes
    /// since the data was serialize.
    Waiting = 2,
};

/// Base config type for client-side configs containing common functionality needed by all config
/// sub-types.
class ConfigBase {
  private:
    // The object (either base config message or MutableConfigMessage) that stores the current
    // config message.  Subclasses do not directly access this: instead they call `dirty()` if they
    // intend to make changes, or the `set_config_field` wrapper.
    std::unique_ptr<ConfigMessage> _config;

    // Tracks our current state
    ConfigState _state = ConfigState::Clean;

  protected:
    // Constructs an empty base config with no config settings and seqno set to 0.
    ConfigBase();

    // Constructs a base config by loading the data from a dump as produced by `dump()`.
    explicit ConfigBase(std::string_view dump);

    // Tracks whether we need to dump again; most mutating methods should set this to true (unless
    // calling set_state, which sets to to true implicitly).
    bool _needs_dump = false;

    // Sets the current state; this also sets _needs_dump to true.
    void set_state(ConfigState s) {
        _state = s;
        _needs_dump = true;
    }

    // If set then we log things by calling this callback
    std::function<void(LogLevel lvl, std::string msg)> logger;

    // Invokes the above if set, does nothing if there is no logger.
    void log(LogLevel lvl, std::string msg) {
        if (logger)
            logger(lvl, std::move(msg));
    }

    // Returns a reference to the current MutableConfigMessage.  If the current message is not
    // already dirty (i.e. Clean or Waiting) then calling this increments the seqno counter.
    MutableConfigMessage& dirty();

    // class for proxying subfield access; this class should never be stored but only used
    // ephemerally (most of its methods are rvalue-qualified).  This lets constructs such as
    // foo["abc"]["def"]["ghi"] = 12;
    // work, auto-vivifying (or trampling, if not a dict) subdicts to reach the target.  It also
    // allows non-vivifying value retrieval via .string(), .integer(), etc. methods.
    class DictFieldProxy {
      private:
        ConfigBase& _conf;
        std::vector<std::string> _inter_keys;
        std::string _last_key;

        // See if we can find the key without needing to create anything, so that we can attempt to
        // access values without mutating anything (which allows, among other things, for assigning
        // of the existing value to not dirty anything).  Returns nullptr if the value or something
        // along its path would need to be created, or has the wrong type; otherwise a const pointer
        // to the value.  The templated type, if provided, can be one of the types a dict_value can
        // hold to also check that the returned value has a particular type; if omitted you get back
        // the dict_value pointer itself.
        template <typename T = dict_value, typename = std::enable_if_t<is_dict_value<T>>>
        const T* get_clean() const {
            const config::dict* data = &_conf._config->data();
            // All but the last need to be dicts:
            for (const auto& key : _inter_keys) {
                auto it = data->find(key);
                data = it != data->end() ? std::get_if<config::dict>(&it->second) : nullptr;
                if (!data)
                    return nullptr;
            }

            const dict_value* val;
            // The last can be any value type:
            if (auto it = data->find(_last_key); it != data->end())
                val = &it->second;
            else
                return nullptr;

            if constexpr (std::is_same_v<T, dict_value>)
                return val;
            else if constexpr (is_dict_subtype<T>) {
                if (auto* v = std::get_if<T>(val))
                    return v;
            } else {  // int64 or std::string, i.e. the config::scalar sub-types.
                if (auto* scalar = std::get_if<config::scalar>(val))
                    return std::get_if<T>(scalar);
            }
            return nullptr;
        }

        // Returns a lvalue reference to the value, stomping its way through the dict as it goes to
        // create subdicts as needed to reach the target value.  If given a template type then we
        // also cast the final dict_value variant into the given type (and replace if with a
        // default-constructed value if it has the wrong type) then return a reference to that.
        template <typename T = dict_value, typename = std::enable_if_t<is_dict_value<T>>>
        T& get_dirty() {
            config::dict* data = &_conf.dirty().data();
            for (const auto& key : _inter_keys) {
                auto& val = (*data)[key];
                data = std::get_if<config::dict>(&val);
                if (!data)
                    data = &val.emplace<config::dict>();
            }
            auto& val = (*data)[_last_key];

            if constexpr (std::is_same_v<T, dict_value>)
                return val;
            else if constexpr (is_dict_subtype<T>) {
                if (auto* v = std::get_if<T>(&val))
                    return *v;
                return val.emplace<T>();
            } else {  // int64 or std::string, i.e. the config::scalar sub-types.
                if (auto* scalar = std::get_if<config::scalar>(&val)) {
                    if (auto* v = std::get_if<T>(scalar))
                        return *v;
                    return scalar->emplace<T>();
                }
                return val.emplace<scalar>().emplace<T>();
            }
        }

        template <typename T>
        void assign_if_changed(T value) {
            // Try to avoiding dirtying the config if this assignment isn't changing anything
            if (!_conf.is_dirty())
                if (auto current = get_clean<T>(); current && *current == value)
                    return;

            get_dirty<T>() = std::move(value);
        }

        void insert_if_missing(config::scalar&& value) {
            if (!_conf.is_dirty())
                if (auto current = get_clean<config::set>(); current && current->count(value))
                    return;

            get_dirty<config::set>().insert(std::move(value));
        }

        void set_erase_impl(const config::scalar& value) {
            if (!_conf.is_dirty())
                if (auto current = get_clean<config::set>(); current && !current->count(value))
                    return;

            config::dict* data = &_conf.dirty().data();

            for (const auto& key : _inter_keys) {
                auto it = data->find(key);
                data = it != data->end() ? std::get_if<config::dict>(&it->second) : nullptr;
                if (!data)
                    return;
            }

            auto it = data->find(_last_key);
            if (it == data->end())
                return;
            auto& val = it->second;
            if (auto* current = std::get_if<config::set>(&val))
                current->erase(value);
            else
                val.emplace<config::set>();
        }

      public:
        DictFieldProxy(ConfigBase& b, std::string key) : _conf{b}, _last_key{std::move(key)} {}

        /// Descends into a dict, returning a copied proxy object for the path to the requested
        /// field.  Nothing is created by doing this unless you actually assign to a value.
        DictFieldProxy operator[](std::string subkey) const& {
            DictFieldProxy subfield{_conf, std::move(subkey)};
            subfield._inter_keys.reserve(_inter_keys.size() + 1);
            subfield._inter_keys.insert(
                    subfield._inter_keys.end(), _inter_keys.begin(), _inter_keys.end());
            subfield._inter_keys.push_back(_last_key);
            return subfield;
        }

        // Same as above, but when called on an rvalue reference we just mutate the current proxy to
        // the new dict path.
        DictFieldProxy&& operator[](std::string subkey) && {
            _inter_keys.push_back(std::move(_last_key));
            _last_key = std::move(subkey);
            return std::move(*this);
        }

        /// Returns a const pointer to the string if one exists at the given location, nullptr
        /// otherwise.
        const std::string* string() const { return get_clean<std::string>(); }

        /// returns the value as a string_view or a fallback if the value doesn't exist (or isn't a
        /// string).  The returned view is directly into the value (or fallback) and so mustn't be
        /// used beyond the validity of either.
        std::string_view string_view_or(std::string_view fallback) const {
            if (auto* s = string())
                return {*s};
            return fallback;
        }

        /// Returns a copy of the value as a string, if it exists and is a string; returns
        /// `fallback` otherwise.
        std::string string_or(std::string fallback) const {
            if (auto* s = string())
                return *s;
            return std::move(fallback);
        }

        /// Returns a const pointer to the integer if one exists at the given location, nullptr
        /// otherwise.
        const int64_t* integer() const { return get_clean<int64_t>(); }

        /// Returns the value as an integer or a fallback if the value doesn't exist (or isn't an
        /// integer).
        int64_t integer_or(int64_t fallback) const {
            if (auto* i = integer())
                return *i;
            return fallback;
        }

        /// Returns a const pointer to the set if one exists at the given location, nullptr
        /// otherwise.
        const config::set* set() const { return get_clean<config::set>(); }
        /// Returns a const pointer to the dict if one exists at the given location, nullptr
        /// otherwise.  (You typically don't need to use this but can rather just use [] to descend
        /// into the dict).
        const config::dict* dict() const { return get_clean<config::dict>(); }

        /// Replaces the current value with the given string.  This also auto-vivifies any
        /// intermediate dicts needed to reach the given key, including replacing non-dict values if
        /// they currently exist along the path.
        void operator=(std::string value) { assign_if_changed(std::move(value)); }
        /// Same as above, but takes a string_view for convenience.
        void operator=(std::string_view value) { *this = std::string{value}; }
        /// Replace the current value with the given integer.  See above.
        void operator=(int64_t value) { assign_if_changed(value); }
        /// Replace the current value with the given set.  See above.
        void operator=(config::set value) { assign_if_changed(std::move(value)); }
        /// Replace the current value with the given dict.  See above.  This often isn't needed
        /// because of how other assignment operations work.
        void operator=(config::dict value) { assign_if_changed(std::move(value)); }

        /// Returns true if there is a value at the current key.  If a template type T is given, it
        /// only returns true if that value also is a `T`.
        template <typename T = dict_value, typename = std::enable_if_t<is_dict_value<T>>>
        bool exists() const {
            return get_clean<T>() != nullptr;
        }

        // Alias for `exists<T>()`
        template <typename T>
        bool is() const {
            return exists<T>();
        }

        /// Removes the value at the current location, regardless of what it currently is.  This
        /// does nothing if the current location does not have a value.
        void erase() {
            if (!_conf.is_dirty() && !get_clean())
                return;

            config::dict* data = &_conf.dirty().data();
            for (const auto& key : _inter_keys) {
                auto it = data->find(key);
                data = it != data->end() ? std::get_if<config::dict>(&it->second) : nullptr;
                if (!data)
                    return;
            }
            data->erase(_last_key);
        }

        /// Adds a value to the set at the current location.  If the current value is not a set or
        /// does not exist then dicts will be created to reach it and a new set will be created.
        void set_insert(std::string_view value) {
            insert_if_missing(config::scalar{std::string{value}});
        }
        void set_insert(int64_t value) { insert_if_missing(config::scalar{value}); }

        /// Removes a value from the set at the current location.  If the current value does not
        /// exist then nothing happens.  If it does exist, but is not a set, it will be replaced
        /// with an empty set.  Otherwise the given value will be removed from the set, if present.
        void set_erase(std::string_view value) {
            set_erase_impl(config::scalar{std::string{value}});
        }
        void set_erase(int64_t value) { set_erase_impl(scalar{value}); }

        /// Emplaces a value at the current location.  As with assignment, this creates dicts as
        /// needed along the keys to reach the target.  The existing value (if present) is destroyed
        /// to make room for the new one.
        template <
                typename T,
                typename... Args,
                typename = std::enable_if_t<
                        is_one_of<T, config::set, config::dict, int64_t, std::string>>>
        T& emplace(Args&&... args) {
            if constexpr (is_one_of<T, int64_t, std::string>)
                return get_dirty<scalar>().emplace<T>(std::forward<Args>(args)...);

            return get_dirty().emplace<T>(std::forward<Args>(args)...);
        }
    };

    /// Wrapper for the ConfigBase's root `data` field to provide data access.  Only provides a []
    /// that gets you into a DictFieldProxy.
    class DictFieldRoot {
        ConfigBase& _conf;
        DictFieldRoot(DictFieldRoot&&) = delete;
        DictFieldRoot(const DictFieldRoot&) = delete;
        DictFieldRoot& operator=(DictFieldRoot&&) = delete;
        DictFieldRoot& operator=(const DictFieldRoot&) = delete;

      public:
        DictFieldRoot(ConfigBase& b) : _conf{b} {}

        /// Access a dict element.  This returns a proxy object for accessing the value, but does
        /// *not* auto-vivify the path (unless/until you assign to it).
        DictFieldProxy operator[](std::string key) const& {
            return DictFieldProxy{_conf, std::move(key)};
        }
    };

    // Called when dumping to obtain any extra data that a subclass needs to store to reconstitute
    // the object.  The base implementation does nothing.  The counterpart to this,
    // `load_extra_data()`, is called when loading from a dump that has extra data; a subclass
    // should either override both (if it needs to serialize extra data) or neither (if it needs no
    // extra data).  Internally this extra data (if non-empty) is stored in the "+" key of the dump.
    virtual oxenc::bt_dict extra_data() const { return {}; }

    // Called when constructing from a dump that has extra data.  The base implementation does
    // nothing.
    virtual void load_extra_data(oxenc::bt_dict extra) {}

  public:
    virtual ~ConfigBase() = default;

    // Proxy class providing read and write access to the contained config data.
    const DictFieldRoot data{*this};

    // Accesses the storage namespace where this config type is to be stored/loaded from.  See
    // namespaces.hpp for the underlying integer values.
    virtual Namespace storage_namespace() const = 0;

    // How many config lags should be used for this object; default to 5.  Implementing subclasses
    // can override to return a different constant if desired.  More lags require more "diff"
    // storage in the config messages, but also allow for a higher tolerance of simultaneous message
    // conflicts.
    virtual int config_lags() const { return 5; }

    // This takes all of the messages pulled down from the server and does whatever is necessary to
    // merge (or replace) the current values.
    //
    // After this call the caller should check `needs_push()` to see if the data on hand was updated
    // and needs to be pushed to the server again.
    //
    // Will throw on serious error (i.e. if neither the current nor any of the given configs are
    // parseable).
    virtual void merge(const std::vector<std::string_view>& configs);

    // Returns true if we are currently dirty (i.e. have made changes that haven't been serialized
    // yet).
    bool is_dirty() const { return _state == ConfigState::Dirty; }

    // Returns true if we are curently clean (i.e. our current config is stored on the server and
    // unmodified).
    bool is_clean() const { return _state == ConfigState::Clean; }

    // Returns true if this object contains updated data that has not yet been confirmed stored on
    // the server.  This will be true whenever `is_clean()` is false: that is, if we are currently
    // "dirty" (i.e.  have changes that haven't been pushed) or are still awaiting confirmation of
    // storage of the most recent serialized push data.
    virtual bool needs_push() const;

    // Returns the data to push to the server along with the seqno value of the data.  If the config
    // is currently dirty (i.e. has previously unsent modifications) then this marks it as
    // awaiting-confirmation instead of dirty so that any future change immediately increments the
    // seqno.
    virtual std::pair<std::string, seqno_t> push();

    // Should be called after the push is confirmed stored on the storage server swarm to let the
    // object know the data is stored.  (Once this is called `needs_push` will start returning false
    // until something changes).  Takes the seqno that was pushed so that the object can ensure that
    // the latest version was pushed (i.e. in case there have been other changes since the `push()`
    // call that returned this seqno).
    //
    // It is safe to call this multiple times with the same seqno value, and with out-of-order
    // seqnos (e.g. calling with seqno 122 after having called with 123; the duplicates and earlier
    // ones will just be ignored).
    virtual void confirm_pushed(seqno_t seqno);

    // Returns a dump of the current state for storage in the database; this value would get passed
    // into the constructor to reconstitute the object (including the push/not pushed status).  This
    // method is *not* virtual: if subclasses need to store extra data they should set it in the
    // `subclass_data` field.
    std::string dump();

    // Returns true if something has changed since the last call to `dump()` that requires calling
    // and saving the `dump()` data again.
    virtual bool needs_dump() const { return _needs_dump; }
};

// The C++ struct we hold opaquely inside the C internals struct.  This is designed so that any
// internals<T> has the same layout so that it doesn't matter whether we unbox to an
// internals<ConfigBase> or internals<SubType>.
template <
        typename ConfigT = ConfigBase,
        std::enable_if_t<std::is_base_of_v<ConfigBase, ConfigT>, int> = 0>
struct internals final {
    std::unique_ptr<ConfigBase> config;
    std::string error;

    // Dereferencing falls through to the ConfigBase object
    ConfigT* operator->() {
        if constexpr (std::is_same_v<ConfigT, ConfigBase>)
            return config.get();
        else {
            auto* c = dynamic_cast<ConfigT*>(config.get());
            assert(c);
            return c;
        }
    }
    const ConfigT* operator->() const {
        if constexpr (std::is_same_v<ConfigT, ConfigBase>)
            return config.get();
        else {
            auto* c = dynamic_cast<ConfigT*>(config.get());
            assert(c);
            return c;
        }
    }
    ConfigT& operator*() { return *operator->(); }
    const ConfigT& operator*() const { return *operator->(); }
};

template <typename T = ConfigBase, std::enable_if_t<std::is_base_of_v<ConfigBase, T>, int> = 0>
inline internals<T>& unbox(config_object* conf) {
    return *static_cast<internals<T>*>(conf->internals);
}
template <typename T = ConfigBase, std::enable_if_t<std::is_base_of_v<ConfigBase, T>, int> = 0>
inline const internals<T>& unbox(const config_object* conf) {
    return *static_cast<const internals<T>*>(conf->internals);
}

// Sets an error message in the internals.error string and updates the last_error pointer in the
// outer (C) config_object struct to point at it.
void set_error(config_object* conf, std::string e);

// Same as above, but gets the error string out of an exception and passed through a return value.
// Intended to simplify catch-and-return-error such as:
//     try {
//         whatever();
//     } catch (const std::exception& e) {
//         return set_error(conf, LIB_SESSION_ERR_OHNOES, e);
//     }
inline int set_error(config_object* conf, int errcode, const std::exception& e) {
    set_error(conf, e.what());
    return errcode;
}

// Copies a value contained in a string into a new malloced char buffer, returning the buffer and
// size via the two pointer arguments.
void copy_out(const std::string& data, char** out, size_t* outlen);

}  // namespace session::config
