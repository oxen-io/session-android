#pragma once

#include <cstdint>

namespace session::config {

enum class Namespace : std::int16_t {
    UserProfile = 2,
    ClosedGroupInfo = 11,
};

}  // namespace session::config
