#pragma once
#include <string>
#include <string_view>
#include <array>
#include <iterator>
#include <cassert>
#include "byte_type.h"

namespace oxenc {

namespace detail {

/// Compile-time generated lookup tables for base32z conversion.  This is case insensitive (though
/// for byte -> b32z conversion we always produce lower case).
struct b32z_table {
    // Store the 0-31 decoded value of every possible char; all the chars that aren't valid are set
    // to 0.  (If you don't trust your data, check it with is_base32z first, which uses these 0's
    // to detect invalid characters -- which is why we want a full 256 element array).
    char from_b32z_lut[256];
    // Store the encoded character of every 0-31 (5 bit) value.
    char to_b32z_lut[32];

    // constexpr constructor that fills out the above (and should do it at compile time for any half
    // decent compiler).
    constexpr b32z_table() noexcept : from_b32z_lut{},
              to_b32z_lut{
                  'y', 'b', 'n', 'd', 'r', 'f', 'g', '8', 'e', 'j', 'k', 'm', 'c', 'p', 'q', 'x',
                  'o', 't', '1', 'u', 'w', 'i', 's', 'z', 'a', '3', '4', '5', 'h', '7', '6', '9'
              }
    {
        for (unsigned char c = 0; c < 32; c++) {
            unsigned char x = to_b32z_lut[c];
            from_b32z_lut[x] = c;
            if (x >= 'a' && x <= 'z')
                from_b32z_lut[x - 'a' + 'A'] = c;
        }
    }
    // Convert a b32z encoded character into a 0-31 value
    constexpr char from_b32z(unsigned char c) const noexcept { return from_b32z_lut[c]; }
    // Convert a 0-31 value into a b32z encoded character
    constexpr char to_b32z(unsigned char b) const noexcept { return to_b32z_lut[b]; }
} constexpr b32z_lut;

// This main point of this static assert is to force the compiler to compile-time build the constexpr tables.
static_assert(b32z_lut.from_b32z('w') == 20 && b32z_lut.from_b32z('T') == 17 && b32z_lut.to_b32z(5) == 'f', "");

} // namespace detail

/// Returns the number of characters required to encode a base32z string from the given number of bytes.
inline constexpr size_t to_base32z_size(size_t byte_size) { return (byte_size*8 + 4) / 5; } // ⌈bits/5⌉ because 5 bits per byte
/// Returns the (maximum) number of bytes required to decode a base32z string of the given size.
inline constexpr size_t from_base32z_size(size_t b32z_size) { return b32z_size*5 / 8; } // ⌊bits/8⌋

/// Iterable object for on-the-fly base32z encoding.  Used internally, but also particularly useful
/// when converting from one encoding to another.
template <typename InputIt>
struct base32z_encoder final {
private:
    InputIt _it, _end;
    static_assert(sizeof(decltype(*_it)) == 1, "base32z_encoder requires chars/bytes input iterator");
    // Number of bits held in r; will always be >= 5 until we are at the end.
    int bits{_it != _end ? 8 : 0};
    // Holds bits of data we've already read, which might belong to current or next chars
    uint_fast16_t r{bits ? static_cast<unsigned char>(*_it) : (unsigned char)0};
public:
    using iterator_category = std::input_iterator_tag;
    using difference_type = std::ptrdiff_t;
    using value_type = char;
    using reference = value_type;
    using pointer = void;
    base32z_encoder(InputIt begin, InputIt end) : _it{std::move(begin)}, _end{std::move(end)} {}

    base32z_encoder end() { return {_end, _end}; }

    bool operator==(const base32z_encoder& i) { return _it == i._it && bits == i.bits; }
    bool operator!=(const base32z_encoder& i) { return !(*this == i); }

    base32z_encoder& operator++() {
        assert(bits >= 5);
        // Discard the most significant 5 bits
        bits -= 5;
        r &= (1 << bits) - 1;
        // If we end up with less than 5 significant bits then try to pull another 8 bits:
        if (bits < 5 && _it != _end) {
            if (++_it != _end) {
                r = (r << 8) | static_cast<unsigned char>(*_it);
                bits += 8;
            } else if (bits > 0) {
                // No more input bytes, so shift `r` to put the bits we have into the most
                // significant bit position for the final character.  E.g. if we have "11" we want
                // the last character to be encoded "11000".
                r <<= (5 - bits);
                bits = 5;
            }
        }
        return *this;
    }
    base32z_encoder operator++(int) { base32z_encoder copy{*this}; ++*this; return copy; }

    char operator*() {
        // Right-shift off the excess bits we aren't accessing yet
        return detail::b32z_lut.to_b32z(r >> (bits - 5));
    }
};

/// Converts bytes into a base32z encoded character sequence, writing them starting at `out`.
/// Returns the final value of out (i.e. the iterator positioned just after the last written base32z
/// character).
template <typename InputIt, typename OutputIt>
OutputIt to_base32z(InputIt begin, InputIt end, OutputIt out) {
    static_assert(sizeof(decltype(*begin)) == 1, "to_base32z requires chars/bytes");
    base32z_encoder it{begin, end};
    return std::copy(it, it.end(), out);
}

/// Creates a base32z string from an iterator pair of a byte sequence.
template <typename It>
std::string to_base32z(It begin, It end) {
    std::string base32z;
    if constexpr (std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>) {
        using std::distance;
        base32z.reserve(to_base32z_size(distance(begin, end)));
    }
    to_base32z(begin, end, std::back_inserter(base32z));
    return base32z;
}

/// Creates a base32z string from an iterable, std::string-like object
template <typename CharT>
std::string to_base32z(std::basic_string_view<CharT> s) { return to_base32z(s.begin(), s.end()); }
inline std::string to_base32z(std::string_view s) { return to_base32z<>(s); }

/// Returns true if the given [begin, end) range is an acceptable base32z string: specifically every
/// character must be in the base32z alphabet, and the string must be a valid encoding length that
/// could have been produced by to_base32z (i.e. some lengths are impossible).
template <typename It>
constexpr bool is_base32z(It begin, It end) {
    static_assert(sizeof(decltype(*begin)) == 1, "is_base32z requires chars/bytes");
    size_t count = 0;
    constexpr bool random = std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>;
    if constexpr (random) {
        using std::distance;
        count = distance(begin, end) % 8;
        if (count == 1 || count == 3 || count == 6) // see below
            return false;
    }
    for (; begin != end; ++begin) {
        auto c = static_cast<unsigned char>(*begin);
        if (detail::b32z_lut.from_b32z(c) == 0 && !(c == 'y' || c == 'Y'))
            return false;
        if constexpr (!random)
            count++;
    }
    // Check for a valid length.
    // - 5n + 0 bytes encodes to 8n chars (no padding bits)
    // - 5n + 1 bytes encodes to 8n+2 chars (last 2 bits are padding)
    // - 5n + 2 bytes encodes to 8n+4 chars (last 4 bits are padding)
    // - 5n + 3 bytes encodes to 8n+5 chars (last 1 bit is padding)
    // - 5n + 4 bytes encodes to 8n+7 chars (last 3 bits are padding)
    if constexpr (!random)
        if (count %= 8; count == 1 || count == 3 || count == 6)
            return false;
    return true;
}

/// Returns true if all elements in the string-like value are base32z characters
template <typename CharT>
constexpr bool is_base32z(std::basic_string_view<CharT> s) { return is_base32z(s.begin(), s.end()); }
constexpr bool is_base32z(std::string_view s) { return is_base32z<>(s); }

/// Iterable object for on-the-fly base32z decoding.  Used internally, but also particularly useful
/// when converting from one encoding to another.  The input range must be a valid base32z
/// encoded string.
///
/// Note that we ignore "padding" bits without requiring that they actually be 0.  For instance, the
/// bytes "\ff\ff" are ideally encoded as "999o" (16 bits of 1s + 4 padding 0 bits), but we don't
/// require that the padding bits be 0.  That is, "9999", "9993", etc. will all decode to the same
/// \ff\ff output string.
template <typename InputIt>
struct base32z_decoder final {
private:
    InputIt _it, _end;
    static_assert(sizeof(decltype(*_it)) == 1, "base32z_decoder requires chars/bytes input iterator");
    uint_fast16_t in = 0;
    int bits = 0; // number of bits loaded into `in`; will be in [8, 12] until we hit the end
public:
    using iterator_category = std::input_iterator_tag;
    using difference_type = std::ptrdiff_t;
    using value_type = char;
    using reference = value_type;
    using pointer = void;
    base32z_decoder(InputIt begin, InputIt end) : _it{std::move(begin)}, _end{std::move(end)} {
        if (_it != _end)
            load_byte();
    }

    base32z_decoder end() { return {_end, _end}; }

    bool operator==(const base32z_decoder& i) { return _it == i._it; }
    bool operator!=(const base32z_decoder& i) { return _it != i._it; }

    base32z_decoder& operator++() {
        // Discard 8 most significant bits
        bits -= 8;
        in &= (1 << bits) - 1;
        if (++_it != _end)
            load_byte();
        return *this;
    }
    base32z_decoder operator++(int) { base32z_decoder copy{*this}; ++*this; return copy; }

    char operator*() {
        return in >> (bits - 8);
    }

private:
    void load_in() {
        in = in << 5
            | detail::b32z_lut.from_b32z(static_cast<unsigned char>(*_it));
        bits += 5;
    }

    void load_byte() {
        load_in();
        if (bits < 8 && ++_it != _end)
            load_in();

        // If we hit the _end iterator above then we hit the end of the input with fewer than 8 bits
        // accumulated to make a full byte.  For a properly encoded base32z string this should only
        // be possible with 0-4 bits of all 0s; these are essentially "padding" bits (e.g. encoding
        // 2 byte (16 bits) requires 4 b32z chars (20 bits), where only the first 16 bits are
        // significant).  Ideally any padding bits should be 0, but we don't check that and rather
        // just ignore them.
        //
        // It also isn't possible to get here with 5-7 bits if the string passes `is_base32z`
        // because the length checks we do there disallow such a length as valid.  (If you were to
        // pass such a string to us anyway then we are technically UB, but the current
        // implementation just ignore the extra bits as if they are extra padding).
    }
};

/// Converts a sequence of base32z digits to bytes.  Undefined behaviour if any characters are not
/// valid base32z alphabet characters.  It is permitted for the input and output ranges to overlap
/// as long as `out` is no later than `begin`.
///
template <typename InputIt, typename OutputIt>
OutputIt from_base32z(InputIt begin, InputIt end, OutputIt out) {
    static_assert(sizeof(decltype(*begin)) == 1, "from_base32z requires chars/bytes");
    base32z_decoder it{begin, end};
    auto bend = it.end();
    while (it != bend)
        *out++ = static_cast<detail::byte_type_t<OutputIt>>(*it++);
    return out;
}

/// Convert a base32z sequence into a std::string of bytes.  Undefined behaviour if any characters
/// are not valid (case-insensitive) base32z characters.
template <typename It>
std::string from_base32z(It begin, It end) {
    std::string bytes;
    if constexpr (std::is_base_of_v<std::random_access_iterator_tag, typename std::iterator_traits<It>::iterator_category>) {
        using std::distance;
        bytes.reserve(from_base32z_size(distance(begin, end)));
    }
    from_base32z(begin, end, std::back_inserter(bytes));
    return bytes;
}

/// Converts base32z digits from a std::string-like object into a std::string of bytes.  Undefined
/// behaviour if any characters are not valid (case-insensitive) base32z characters.
template <typename CharT>
std::string from_base32z(std::basic_string_view<CharT> s) { return from_base32z(s.begin(), s.end()); }
inline std::string from_base32z(std::string_view s) { return from_base32z<>(s); }

inline namespace literals {
    inline std::string operator""_b32z(const char* x, size_t n) {
        std::string_view in{x, n};
        if (!is_base32z(in))
            throw std::invalid_argument{"base32z literal is not base32z"};
        return from_base32z(in);
    }
}

}
