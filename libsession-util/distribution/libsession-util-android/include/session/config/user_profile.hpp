#pragma once

#include <memory>
#include <session/config.hpp>

#include "base.hpp"
#include "namespaces.hpp"

namespace session::config {

/// keys used in this config, either currently or in the past (so that we don't reuse):
///
/// n - user profile name
/// p - user profile url
/// q - user profile decryption key (binary)

class UserProfile final : public ConfigBase {

  public:
    /// Constructs a new, blank user profile.
    UserProfile() = default;

    /// Constructs a user profile from existing data
    explicit UserProfile(std::string_view dumped) : ConfigBase{dumped} {}

    Namespace storage_namespace() const override { return Namespace::UserProfile; }

    /// Returns the user profile name, or nullptr if there is no profile name set.
    const std::string* get_name() const;

    /// Sets the user profile name
    void set_name(std::string_view new_name);

    /// Gets the user's current profile pic URL and decryption key.  Returns nullptr for *both*
    /// values if *either* value is unset or empty in the config data.
    std::pair<const std::string*, const std::string*> get_profile_pic() const;

    /// Sets the user's current profile pic to a new URL and decryption key.  Clears both if either
    /// one is empty.
    void set_profile_pic(std::string url, std::string key);
};

}  // namespace session::config
