#pragma once

// Specializations for assigning from a char into an output iterator, used by hex/base32z/base64
// decoding to bytes.

#include <iterator>
#include <type_traits>

namespace oxenc::detail {

// Fallback - we just try a char
template <typename OutputIt, typename = void>
struct byte_type { using type = char; };

// Support for things like std::back_inserter:
template <typename OutputIt>
struct byte_type<OutputIt, std::void_t<typename OutputIt::container_type>> {
    using type = typename OutputIt::container_type::value_type; };

// iterator, raw pointers:
template <typename OutputIt>
struct byte_type<OutputIt, std::enable_if_t<std::is_reference_v<typename std::iterator_traits<OutputIt>::reference>>> {
    using type = std::remove_reference_t<typename std::iterator_traits<OutputIt>::reference>; };

template <typename OutputIt>
using byte_type_t = typename byte_type<OutputIt>::type;

}
