#pragma once

#include <cstdint>
#include <cstring>
#include <type_traits>

#if defined(_MSC_VER) && (!defined(__clang__) || defined(__c2__))
#  include <cstdlib>

#  define bswap_16(x) _byteswap_ushort(x)
#  define bswap_32(x) _byteswap_ulong(x)
#  define bswap_64(x) _byteswap_uint64(x)
#elif defined(__clang__) || defined(__GNUC__)
#  define bswap_16(x) __builtin_bswap16(x)
#  define bswap_32(x) __builtin_bswap32(x)
#  define bswap_64(x) __builtin_bswap64(x)
#elif defined(__linux__)
extern "C" {
#  include <byteswap.h>
} // extern "C"
#else
#  error "Don't know how to byteswap on this platform!"
#endif

namespace oxenc {

    /// True if this is a little-endian platform
    inline constexpr bool little_endian =
#if defined(__LITTLE_ENDIAN__)
        true
#elif defined(__BIG_ENDIAN__)
        false
#elif defined(__BYTE_ORDER) && defined(__LITTLE_ENDIAN) && __BYTE_ORDER == __LITTLE_ENDIAN
        true
#elif defined(__BYTE_ORDER__) && defined(__ORDER_LITTLE_ENDIAN__) && __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
        true
#elif defined(__BYTE_ORDER) && defined(__BIG_ENDIAN) && __BYTE_ORDER == __BIG_ENDIAN
        false
#elif defined(__BYTE_ORDER__) && defined(__ORDER_BIG_ENDIAN__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
        false
#elif defined(_WIN32)
        true
#else
#  error "Error: don't know which endian this is"
#endif
        ;

    /// True if this is a big-endian platform
    inline constexpr bool big_endian = !little_endian;

    /// True if the type is integral and of a size we support swapping.  (We also allow size-1
    /// values to be passed here for completeness, though nothing is ever swapped for such a value).
    template <typename T> constexpr bool is_endian_swappable =
        std::is_integral_v<T> && (sizeof(T) == 1 || sizeof(T) == 2 || sizeof(T) == 4 || sizeof(T) == 8);

    /// Byte swaps an integer value unconditionally.  You usually want to use one of the other
    /// endian-aware functions rather than this.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void byteswap_inplace(T& val) {
        if constexpr (sizeof(T) == 2)
            val = bswap_16(val);
        else if constexpr (sizeof(T) == 4)
            val = bswap_32(val);
        else if constexpr (sizeof(T) == 8)
            val = bswap_64(val);
    }

    /// Converts a host-order integer value into a little-endian value, mutating it.  Does nothing
    /// on little-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void host_to_little_inplace(T& val) {
        if constexpr (!little_endian)
            byteswap_inplace(val);
    }

    /// Converts a host-order integer value into a little-endian value, returning it.  Does no
    /// converstion on little-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T host_to_little(T val) {
        host_to_little_inplace(val);
        return val;
    }

    /// Converts a little-endian integer value into a host-order (native) integer value, mutating
    /// it.  Does nothing on little-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void little_to_host_inplace(T& val) {
        if constexpr (!little_endian)
            byteswap_inplace(val);
    }

    /// Converts a little-order integer value into a host-order (native) integer value, returning
    /// it.  Does no conversion on little-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T little_to_host(T val) {
        little_to_host_inplace(val);
        return val;
    }

    /// Converts a host-order integer value into a big-endian value, mutating it.  Does nothing on
    /// big-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void host_to_big_inplace(T& val) {
        if constexpr (!big_endian)
            byteswap_inplace(val);
    }

    /// Converts a host-order integer value into a big-endian value, returning it.  Does no
    /// conversion on big-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T host_to_big(T val) {
        host_to_big_inplace(val);
        return val;
    }

    /// Converts a big-endian value into a host-order (native) integer value, mutating it.  Does
    /// nothing on big-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void big_to_host_inplace(T& val) {
        if constexpr (!big_endian)
            byteswap_inplace(val);
    }

    /// Converts a big-order integer value into a host-order (native) integer value, returning it.
    /// Does no conversion on big-endian platforms.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T big_to_host(T val) {
        big_to_host_inplace(val);
        return val;
    }

    /// Loads a host-order integer value from a memory location containing little-endian bytes.
    /// (There is no alignment requirement on the given pointer address).
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T load_little_to_host(const void* from) {
        T val;
        std::memcpy(&val, from, sizeof(T));
        little_to_host_inplace(val);
        return val;
    }

    /// Loads a little-endian integer value from a memory location containing host order bytes.
    /// (There is no alignment requirement on the given pointer address).
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T load_host_to_little(const void* from) {
        T val;
        std::memcpy(&val, from, sizeof(T));
        host_to_little_inplace(val);
        return val;
    }

    /// Loads a host-order integer value from a memory location containing big-endian bytes.  (There
    /// is no alignment requirement on the given pointer address).
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T load_big_to_host(const void* from) {
        T val;
        std::memcpy(&val, from, sizeof(T));
        big_to_host_inplace(val);
        return val;
    }

    /// Loads a big-endian integer value from a memory location containing host order bytes.  (There
    /// is no alignment requirement on the given pointer address).
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    T load_host_to_big(const void* from) {
        T val;
        std::memcpy(&val, from, sizeof(T));
        host_to_big_inplace(val);
        return val;
    }

    /// Writes a little-endian integer value into the given memory location, copying and converting
    /// it (if necessary) from the given host-order integer value.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void write_host_as_little(T val, void* to) {
        host_to_little_inplace(val);
        std::memcpy(to, &val, sizeof(T));
    }

    /// Writes a big-endian integer value into the given memory location, copying and converting it
    /// (if necessary) from the given host-order integer value.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void write_host_as_big(T val, void* to) {
        host_to_big_inplace(val);
        std::memcpy(to, &val, sizeof(T));
    }

    /// Writes a host-order integer value into the given memory location, copying and converting it
    /// (if necessary) from the given little-endian integer value.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void write_little_as_host(T val, void* to) {
        little_to_host_inplace(val);
        std::memcpy(to, &val, sizeof(T));
    }

    /// Writes a host-order integer value into the given memory location, copying and converting it
    /// (if necessary) from the given big-endian integer value.
    template <typename T, typename = std::enable_if_t<is_endian_swappable<T>>>
    void write_big_as_host(T val, void* to) {
        big_to_host_inplace(val);
        std::memcpy(to, &val, sizeof(T));
    }

} // namespace oxenc
