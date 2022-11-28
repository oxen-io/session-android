#pragma once
#include <string>
#include <string_view>
#include <array>
#include <iterator>
#include <cassert>
#include "byte_type.h"

namespace oxenc {

namespace detail {

/// Compile-time generated lookup tables for base64 conversion.
struct b64_table {
    // Store the 0-63 decoded value of every possible char; all the chars that aren't valid are set
    // to 0.  (If you don't trust your data, check it with is_base64 first, which uses these 0's
    // to detect invalid characters -- which is why we want a full 256 element array).
    char from_b64_lut[256];
    // Store the encoded character of every 0-63 (6 bit) value.
    char to_b64_lut[64];

    // constexpr constructor that fills out the above (and should do it at compile time for any half
    // decent compiler).
    constexpr b64_table() noexcept : from_b64_lut{}, to_b64_lut{} {
        for (unsigned char c = 0; c < 26; c++) {
            from_b64_lut[(unsigned char)('A' + c)] =  0  + c;
            to_b64_lut[  (unsigned char)( 0  + c)] = 'A' + c;
        }
        for (unsigned char c = 0; c < 26; c++) {
            from_b64_lut[(unsigned char)('a' + c)] = 26  + c;
            to_b64_lut[  (unsigned char)(26  + c)] = 'a' + c;
        }
        for (unsigned char c = 0; c < 10; c++) {
            from_b64_lut[(unsigned char)('0' + c)] = 52  + c;
            to_b64_lut[  (unsigned char)(52  + c)] = '0' + c;
        }
        to_b64_lut[62] = '+'; from_b64_lut[(unsigned char) '+'] = 62;
        to_b64_lut[63] = '/'; from_b64_lut[(unsigned char) '/'] = 63;
    }
    // Convert a b64 encoded character into a 0-63 value
    constexpr char from_b64(unsigned char c) const noexcept { return from_b64_lut[c]; }
    // Convert a 0-31 value into a b64 encoded character
    constexpr char to_b64(unsigned char b) const noexcept { return to_b64_lut[b]; }
} constexpr b64_lut;

// This main point of this static assert is to force the compiler to compile-time build the constexpr tables.
static_assert(b64_lut.from_b64('/') == 63 && b64_lut.from_b64('7') == 59 && b64_lut.to_b64(38) == 'm', "");

} // namespace detail

/// Returns the number of characters required to encode a base64 string from the given number of bytes.
inline constexpr size_t to_base64_size(size_t byte_size, bool padded = true) {
    return padded
        ? (byte_size + 2) / 3 * 4 // bytes*4/3, rounded up to the next multiple of 4
        : (byte_size * 4 + 2) / 3; // ⌈bytes*4/3⌉
}
/// Returns the (maximum) number of bytes required to decode a base64 string of the given size.
/// Note that this may overallocate by 1-2 bytes if the size includes 1-2 padding chars.
inline constexpr size_t from_base64_size(size_t b64_size) {
    return b64_size * 3 / 4; // == ⌊bits/8⌋; floor because we ignore trailing "impossible" bits (see below)
}

/// Iterable object for on-the-fly base64 encoding.  Used internally, but also particularly useful
/// when converting from one encoding to another.
template <typename InputIt>
struct base64_encoder final {
private:
    InputIt _it, _end;
    static_assert(sizeof(decltype(*_it)) == 1, "base64_encoder requires chars/bytes input iterator");
    // How much padding (at most) we can add at the end
    int padding;
    // Number of bits held in r; will always be >= 6 until we are at the end.
    int bits{_it != _end ? 8 : 0};
    // Holds bits of data we've already read, which might belong to current or next chars
    uint_fast16_t r{bits ? static_cast<unsigned char>(*_it) : (unsigned char)0};
public:
    using iterator_category = std::input_iterator_tag;
    using difference_type = std::ptrdiff_t;
    using value_type = char;
    using reference = value_type;
    using pointer = void;
    base64_encoder(InputIt begin, InputIt end, bool padded = true)
        : _it{std::move(begin)}, _end{std::move(end)}, padding{padded} {}

    base64_encoder end() { return {_end, _end, false}; }

    bool operator==(const base64_encoder& i) { return _it == i._it && bits == i.bits && padding == i.padding; }
    bool operator!=(const base64_encoder& i) { return !(*this == i); }

    base64_encoder& operator++() {
        if (bits == 0) {
            padding--;
            return *this;
        }
        assert(bits >= 6);
        // Discard the most significant 6 bits
        bits -= 6;
        r &= (1 << bits) - 1;
        // If we end up with less than 6 significant bits then try to pull another 8 bits:
        if (bits < 6 && _it != _end) {
            if (++_it != _end) {
                r = (r << 8) | static_cast<unsigned char>(*_it);
                bits += 8;
            } else if (bits > 0) {
                // No more input bytes, so shift `r` to put the bits we have into the most
                // significant bit position for the final character, and figure out how many padding
                // bytes we want to append.  E.g. if we have "11" we want
                // the last character to be encoded "110000".
                if (padding) {
                    // padding should be:
                    // 3n+0 input => 4n output, no padding, handled below
                    // 3n+1 input => 4n+2 output + 2 padding; we'll land here with 2 trailing bits
                    // 3n+2 input => 4n+3 output + 1 padding; we'll land here with 4 trailing bits
                    padding = 3 - bits / 2;
                }
                r <<= (6 - bits);
                bits = 6;
            } else {
                padding = 0; // No excess bits, so input was a multiple of 3 and thus no padding
            }
        }
        return *this;
    }
    base64_encoder operator++(int) { base64_encoder copy{*this}; ++*this; return copy; }

    char operator*() {
        if (bits == 0 && padding)
            return '=';
        // Right-shift off the excess bits we aren't accessing yet
        return detail::b64_lut.to_b64(r >> (bits - 6));
    }
};

/// Converts bytes into a base64 encoded character sequence, writing them starting at `out`.
/// Returns the final value of out (i.e. the iterator positioned just after the last written base64
/// character).
template <typename InputIt, typename OutputIt>
OutputIt to_base64(InputIt begin, InputIt end, OutputIt out, bool padded = true) {
    static_assert(sizeof(decltype(*begin)) == 1, "to_base64 requires chars/bytes");
    auto it = base64_encoder{begin, end, padded};
    return std::copy(it, it.end(), out);
}

/// Creates and returns a base64 string from an iterator pair of a character sequence.  The
/// resulting string will have '=' padding, if appropriate.
template <typename It>
std::string to_base64(It begin, It end) {
    std::string base64;
    if constexpr (std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>) {
        using std::distance;
        base64.reserve(to_base64_size(distance(begin, end)));
    }
    to_base64(begin, end, std::back_inserter(base64));
    return base64;
}

/// Creates and returns a base64 string from an iterator pair of a character sequence.  The
/// resulting string will not be padded.
template <typename It>
std::string to_base64_unpadded(It begin, It end) {
    std::string base64;
    if constexpr (std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>) {
        using std::distance;
        base64.reserve(to_base64_size(distance(begin, end), false));
    }
    to_base64(begin, end, std::back_inserter(base64), false);
    return base64;
}

/// Creates a base64 string from an iterable, std::string-like object.  The string will have '='
/// padding, if appropriate.
template <typename CharT>
std::string to_base64(std::basic_string_view<CharT> s) { return to_base64(s.begin(), s.end()); }
inline std::string to_base64(std::string_view s) { return to_base64<>(s); }

/// Creates a base64 string from an iterable, std::string-like object.  The string will not be
/// padded.
template <typename CharT>
std::string to_base64_unpadded(std::basic_string_view<CharT> s) { return to_base64_unpadded(s.begin(), s.end()); }
inline std::string to_base64_unpadded(std::string_view s) { return to_base64_unpadded<>(s); }

/// Returns true if the range is a base64 encoded value; we allow (but do not require) '=' padding,
/// but only at the end, only 1 or 2, and only if it pads out the total to a multiple of 4.
/// Otherwise the string must contain only valid base64 characters, and must not have a length of
/// 4n+1 (because that cannot be produced by base64 encoding).
template <typename It>
constexpr bool is_base64(It begin, It end) {
    static_assert(sizeof(decltype(*begin)) == 1, "is_base64 requires chars/bytes");
    using std::distance;
    using std::prev;
    size_t count = 0;
    constexpr bool random = std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>;
    if constexpr (random) {
        count = distance(begin, end) % 4;
        if (count == 1)
            return false;
    }

    // Allow 1 or 2 padding chars *if* they pad it to a multiple of 4.
    if (begin != end && distance(begin, end) % 4 == 0) {
        auto last = prev(end);
        if (static_cast<unsigned char>(*last) == '=')
            end = last--;
        if (static_cast<unsigned char>(*last) == '=')
            end = last;
    }

    for (; begin != end; ++begin) {
        auto c = static_cast<unsigned char>(*begin);
        if (detail::b64_lut.from_b64(c) == 0 && c != 'A')
            return false;
        if constexpr (!random)
            count++;
    }

    if constexpr (!random)
        if (count % 4 == 1) // base64 encoding will produce 4n, 4n+2, 4n+3, but never 4n+1
            return false;

    return true;
}

/// Returns true if the string-like value is a base64 encoded value
template <typename CharT>
constexpr bool is_base64(std::basic_string_view<CharT> s) { return is_base64(s.begin(), s.end()); }
constexpr bool is_base64(std::string_view s) { return is_base64(s.begin(), s.end()); }

/// Iterable object for on-the-fly base64 decoding.  Used internally, but also particularly useful
/// when converting from one encoding to another.  The input range must be a valid base64 encoded
/// string (with or without padding).
///
/// Note that we ignore "padding" bits without requiring that they actually be 0.  For instance, the
/// bytes "\ff\ff" are ideally encoded as "//8=" (16 bits of 1s + 2 padding 0 bits, then a full
/// 6-bit padding char).  We don't, however, require that the padding bits be 0.  That is, "///=",
/// "//9=", "//+=", etc. will all decode to the same \ff\ff output string.
template <typename InputIt>
struct base64_decoder final {
private:
    InputIt _it, _end;
    static_assert(sizeof(decltype(*_it)) == 1, "base64_decoder requires chars/bytes input iterator");
    uint_fast16_t in = 0;
    int bits = 0; // number of bits loaded into `in`; will be in [8, 12] until we hit the end
public:
    using iterator_category = std::input_iterator_tag;
    using difference_type = std::ptrdiff_t;
    using value_type = char;
    using reference = value_type;
    using pointer = void;
    base64_decoder(InputIt begin, InputIt end) : _it{std::move(begin)}, _end{std::move(end)} {
        if (_it != _end)
            load_byte();
    }

    base64_decoder end() { return {_end, _end}; }

    bool operator==(const base64_decoder& i) { return _it == i._it; }
    bool operator!=(const base64_decoder& i) { return _it != i._it; }

    base64_decoder& operator++() {
        // Discard 8 most significant bits
        bits -= 8;
        in &= (1 << bits) - 1;
        if (++_it != _end)
            load_byte();
        return *this;
    }
    base64_decoder operator++(int) { base64_decoder copy{*this}; ++*this; return copy; }

    char operator*() {
        return in >> (bits - 8);
    }

private:
    void load_in() {
        // We hit padding trying to read enough for a full byte, so we're done.  (And since you were
        // already supposed to have checked validity with is_base64, the padding can only be at the
        // end).
        auto c = static_cast<unsigned char>(*_it);
        if (c == '=') {
            _it = _end;
            bits = 0;
            return;
        }

        in = in << 6
            | detail::b64_lut.from_b64(c);
        bits += 6;
    }

    void load_byte() {
        load_in();
        if (bits && bits < 8 && ++_it != _end)
            load_in();

        // If we hit the _end iterator above then we hit the end of the input (or hit padding) with
        // fewer than 8 bits accumulated to make a full byte.  For a properly encoded base64 string
        // this should only be possible with 0, 2, or 4 bits of all 0s; these are essentially
        // "padding" bits (e.g.  encoding 2 byte (16 bits) requires 3 b64 chars (18 bits), where
        // only the first 16 bits are significant).  Ideally any padding bits should be 0, but we
        // don't check that and rather just ignore them.
    }
};

/// Converts a sequence of base64 digits to bytes.  Undefined behaviour if any characters are not
/// valid base64 alphabet characters.  It is permitted for the input and output ranges to overlap as
/// long as `out` is no later than `begin`.  Trailing padding characters are permitted but not
/// required.  Returns the final value of out (that is, the iterator positioned just after the
/// last written character).
///
/// It is possible to provide "impossible" base64 encoded values; for example "YWJja" which has 30
/// bits of data even though a base64 encoded byte string should have 24 (4 chars) or 36 (6 chars)
/// bits for a 3- and 4-byte input, respectively.  We ignore any such "impossible" bits, and
/// similarly ignore impossible bits in the bit "overhang"; that means "YWJjZA==" (the proper
/// encoding of "abcd") and "YWJjZB", "YWJjZC", ..., "YWJjZP" all decode to the same "abcd" value:
/// the last 4 bits of the last character are essentially considered padding.
template <typename InputIt, typename OutputIt>
OutputIt from_base64(InputIt begin, InputIt end, OutputIt out) {
    static_assert(sizeof(decltype(*begin)) == 1, "from_base64 requires chars/bytes");
    base64_decoder it{begin, end};
    auto bend = it.end();
    while (it != bend)
        *out++ = static_cast<detail::byte_type_t<OutputIt>>(*it++);
    return out;
}

/// Converts base64 digits from a iterator pair of characters into a std::string of bytes.
/// Undefined behaviour if any characters are not valid base64 characters.
template <typename It>
std::string from_base64(It begin, It end) {
    std::string bytes;
    if constexpr (std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>) {
        using std::distance;
        bytes.reserve(from_base64_size(distance(begin, end)));
    }
    from_base64(begin, end, std::back_inserter(bytes));
    return bytes;
}

/// Converts base64 digits from a std::string-like object into a std::string of bytes.  Undefined
/// behaviour if any characters are not valid base64 characters.
template <typename CharT>
std::string from_base64(std::basic_string_view<CharT> s) { return from_base64(s.begin(), s.end()); }
inline std::string from_base64(std::string_view s) { return from_base64<>(s); }

inline namespace literals {
    inline std::string operator""_b64(const char* x, size_t n) {
        std::string_view in{x, n};
        if (!is_base64(in))
            throw std::invalid_argument{"base64 literal is not base64"};
        return from_base64(in);
    }
}

}
