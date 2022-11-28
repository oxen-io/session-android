#pragma once

#include <cassert>
#include <charconv>
#include <stdexcept>
#include <string_view>
#include <unordered_map>
#include <utility>
#include "variant.h"

namespace oxenc {

    using namespace std::literals;

    class bt_dict_producer;

#if defined(__APPLE__) && defined(__MAC_OS_X_VERSION_MIN_REQUIRED) && __MAC_OS_X_VERSION_MIN_REQUIRED < 101500
#define OXENC_APPLE_TO_CHARS_WORKAROUND
/// Really simplistic version of std::to_chars on Apple, because Apple doesn't allow `std::to_chars`
/// to be used if targetting anything before macOS 10.15.  The buffer must have at least 20 chars of
/// space (for int types up to 64-bit); we return a pointer one past the last char written.
    template <typename IntType>
    char* apple_to_chars10(char* buf, IntType val) {
        static_assert(std::is_integral_v<IntType> && sizeof(IntType) <= 8);
        if constexpr (std::is_signed_v<IntType>) {
            if (val < 0) {
                buf[0] = '-';
                return apple_to_chars10(buf+1, static_cast<std::make_unsigned_t<IntType>>(-val));
            }
        }

        // write it to the buffer in reverse (because we don't know how many chars we'll need yet, but
        // writing in reverse will figure that out).
        char* pos = buf;
        do {
            *pos++ = '0' + static_cast<char>(val % 10);
            val /= 10;
        } while (val > 0);

        // Reverse the digits into the right order
        int swaps = (pos - buf) / 2;
        for (int i = 0; i < swaps; i++)
            std::swap(buf[i], pos[-1 - i]);

        return pos;
    }
#endif


/// Class that allows you to build a bt-encoded list manually, without copying or allocating memory.
/// This is essentially the reverse of bt_list_consumer: where it lets you stream-parse a buffer,
/// this class lets you build directly into a buffer that you own.
///
/// Out-of-buffer-space errors throw
    class bt_list_producer {
        friend class bt_dict_producer;

        // Our pointers to the next write position and the past-the-end pointer of the buffer.
        using buf_span = std::pair<char*, char*>;
        // Our data is a begin/end pointer pair for the root list, or a pointer to our parent if a
        // sublist.
        std::variant<buf_span, bt_list_producer*, bt_dict_producer*> data;
        // Reference to the write buffer; this is simply a reference to the value inside `data` for the
        // root element, and a pointer to the root's value for sublists/subdicts.
        buf_span& buffer;
        // True indicates we have an open child list/dict
        bool has_child = false;
        // The range that contains this currently serialized value; `from` equals wherever the `l` was
        // written that started this list and `to` is one past the `e` that ends it.  Note that `to`
        // will always be ahead of `buf_span.first` because we always write the `e`s to close open lists
        // but these `e`s don't advance the write position (they will be overwritten if we append).
        const char* const from;
        const char* to;

        // Sublist constructors
        bt_list_producer(bt_list_producer* parent, std::string_view prefix = "l"sv);
        bt_list_producer(bt_dict_producer* parent, std::string_view prefix = "l"sv);

        // Common constructor for both list and dict producer
        bt_list_producer(char* begin, char* end, std::string_view prefix);

        // Does the actual appending to the buffer, and throwing if we'd overrun.  If advance is false
        // then we append without moving the buffer pointer (primarily when we append intermediate `e`s
        // that we will overwrite if more data is added).  This means that the next write will overwrite
        // whatever was previously written by an `advance=false` call.
        void buffer_append(std::string_view d, bool advance = true);

        // Appends the 'e's into the buffer to close off open sublists/dicts *without* advancing the
        // buffer position; we do this after each append so that the buffer always contains valid
        // encoded data, even while we are still appending to it, and so that appending something raises
        // a length_error if appending it would not leave enough space for the required e's to close the
        // open list(s)/dict(s).
        void append_intermediate_ends(size_t count = 1);

        // Writes an integer to the given buffer; returns the one-past-the-data pointer.  Up to 20 bytes
        // will be written and must be available in buf.  Used for both string and integer
        // serialization.
        template <typename IntType>
        char* write_integer(IntType val, char* buf) {
            static_assert(sizeof(IntType) <= 64);

#ifndef OXENC_APPLE_TO_CHARS_WORKAROUND
            auto [ptr, ec] = std::to_chars(buf, buf+20, val);
            assert(ec == std::errc());
            return ptr;
#else
            // Hate apple.
            return apple_to_chars10(buf, val);
#endif
        }

        // Serializes an integer value and appends it to the output buffer.  Does not call
        // append_intermediate_ends().
        template <typename IntType, std::enable_if_t<std::is_integral_v<IntType>, int> = 0>
        void append_impl(IntType val) {
            char buf[22]; // 'i' + base10 representation + 'e'
            buf[0] = 'i';
            auto* ptr = write_integer(val, buf+1);
            *ptr++ = 'e';
            buffer_append({buf, static_cast<size_t>(ptr-buf)});
        }

        // Appends a string value, but does not call append_intermediate_ends()
        void append_impl(std::string_view s) {
            char buf[21]{}; // length + ':'
            auto *ptr = write_integer(s.size(), buf);
            *ptr++ = ':';
            buffer_append({buf, static_cast<size_t>(ptr - buf)});
            buffer_append(s);
        }

    public:
        bt_list_producer() = delete;
        bt_list_producer(const bt_list_producer&) = delete;
        bt_list_producer& operator=(const bt_list_producer&) = delete;
        bt_list_producer& operator=(bt_list_producer&&) = delete;
        bt_list_producer(bt_list_producer&& other);

        /// Constructs a list producer that writes into the range [begin, end).  If a write would go
        /// beyond the end of the buffer an exception is raised.  Note that this will happen during
        /// construction if the given buffer is not large enough to contain the `le` encoding of an
        /// empty list.
        bt_list_producer(char* begin, char* end) : bt_list_producer{begin, end, "l"sv} {}

        /// Constructs a list producer that writes into the range [begin, begin+size).  If a write would
        /// go beyond the end of the buffer an exception is raised.
        bt_list_producer(char* begin, size_t len) : bt_list_producer{begin, begin + len, "l"sv} {}

        ~bt_list_producer();

        /// Returns a string_view into the currently serialized data buffer.  Note that the returned
        /// view includes the `e` list end serialization markers which will be overwritten if the list
        /// (or an active sublist/subdict) is appended to.
        std::string_view view() const {
            return {from, static_cast<size_t>(to-from)};
        }

        /// Returns the end position in the buffer.
        const char* end() const { return to; }

        /// Appends an element containing binary string data
        void append(std::string_view data)
        {
            if (has_child) throw std::logic_error{"Cannot append to list when a sublist is active"};
            append_impl(data);
            append_intermediate_ends();
        }

        bt_list_producer& operator+=(std::string_view data) { append(data); return *this; }

        /// Appends an integer
        template <typename IntType, std::enable_if_t<std::is_integral_v<IntType>, int> = 0>
        void append(IntType i) {
            if (has_child) throw std::logic_error{"Cannot append to list when a sublist is active"};
            append_impl(i);
            append_intermediate_ends();
        }

        template <typename IntType, std::enable_if_t<std::is_integral_v<IntType>, int> = 0>
        bt_list_producer& operator+=(IntType i) { append(i); return *this; }

        /// Appends elements from the range [from, to) to the list.  This does *not* append the elements
        /// as a sublist: for that you should use something like: `l.append_list().append(from, to);`
        template <typename ForwardIt>
        void append(ForwardIt from, ForwardIt to) {
            if (has_child) throw std::logic_error{"Cannot append to list when a sublist is active"};
            while (from != to)
                append_impl(*from++);
            append_intermediate_ends();
        }

        /// Appends a sublist to this list.  Returns a new bt_list_producer that references the parent
        /// list.  The parent cannot be added to until the sublist is destroyed.  This is meant to be
        /// used via RAII:
        ///
        ///     buf data[16];
        ///     bt_list_producer list{data, sizeof(data)};
        ///     {
        ///         auto sublist = list.append_list();
        ///         sublist.append(42);
        ///     }
        ///     list.append(1);
        ///     // `data` now contains: `lli42eei1ee`
        ///
        /// If doing more complex lifetime management, take care not to allow the child instance to
        /// outlive the parent.
        bt_list_producer append_list();

        /// Appends a dict to this list.  Returns a new bt_dict_producer that references the parent
        /// list.  The parent cannot be added to until the subdict is destroyed.  This is meant to be
        /// used via RAII (see append_list() for details).
        ///
        /// If doing more complex lifetime management, take care not to allow the child instance to
        /// outlive the parent.
        bt_dict_producer append_dict();

        /// Appends a bt_value, bt_dict, or bt_list to this bt_list.  You must include the
        /// bt_value_producer.h header (either directly or via bt.h) to use this method.
        template <typename T>
        void append_bt(const T& bt);
    };


/// Class that allows you to build a bt-encoded dict manually, without copying or allocating memory.
/// This is essentially the reverse of bt_dict_consumer: where it lets you stream-parse a buffer,
/// this class lets you build directly into a buffer that you own.
///
/// Note that bt-encoded dicts *must* be produced in (ASCII) ascending key order, but that this is
/// only tracked/enforced for non-release builds (i.e. without -DNDEBUG).
    class bt_dict_producer : bt_list_producer {
        friend class bt_list_producer;

        // Subdict constructors

        bt_dict_producer(bt_list_producer* parent) : bt_list_producer{parent, "d"sv} {}
        bt_dict_producer(bt_dict_producer* parent) : bt_list_producer{parent, "d"sv} {}

        // Checks a just-written key string to make sure it is monotonically increasing from the last
        // key.  Does nothing in a release build.
#ifdef NDEBUG
        constexpr void check_incrementing_key(size_t) const {}
#else
        // String view into the buffer where we wrote the previous key.
        std::string_view last_key;
        void check_incrementing_key(size_t size) {
            std::string_view this_key{buffer.first - size, size};
            assert(!last_key.data() || this_key > last_key);
            last_key = this_key;
        }
#endif

    public:
        /// Constructs a dict producer that writes into the range [begin, end).  If a write would go
        /// beyond the end of the buffer an exception is raised.  Note that this will happen during
        /// construction if the given buffer is not large enough to contain the `de` encoding of an
        /// empty list.
        bt_dict_producer(char* begin, char* end) : bt_list_producer{begin, end, "d"sv} {}

        /// Constructs a list producer that writes into the range [begin, begin+size).  If a write would
        /// go beyond the end of the buffer an exception is raised.
        bt_dict_producer(char* begin, size_t len) : bt_list_producer{begin, begin + len, "d"sv} {}

        /// Returns a string_view into the currently serialized data buffer.  Note that the returned
        /// view includes the `e` dict end serialization markers which will be overwritten if the dict
        /// (or an active sublist/subdict) is appended to.
        std::string_view view() const { return bt_list_producer::view(); }

        /// Returns the end position in the buffer.
        const char* end() const { return bt_list_producer::end(); }

        /// Appends a key-value pair with a string or integer value.  The key must be > the last key
        /// added, but this is only enforced (with an assertion) in debug builds.
        template <typename T, std::enable_if_t<std::is_convertible_v<T, std::string_view> || std::is_integral_v<T>, int> = 0>
        void append(std::string_view key, const T& value) {
            if (has_child) throw std::logic_error{"Cannot append to list when a sublist is active"};
            append_impl(key);
            check_incrementing_key(key.size());
            append_impl(value);
            append_intermediate_ends();
        }

        /// Appends pairs from the range [from, to) to the dict.  Elements must have a .first
        /// convertible to a string_view, and a .second that is either string view convertible or an
        /// integer.  This does *not* append the elements as a subdict: for that you should use
        /// something like: `l.append_dict().append(key, from, to);`
        ///
        /// Also note that the range *must* be sorted by keys, which means either using an ordered
        /// container (e.g. std::map) or a manually ordered container (such as a vector or list of
        /// pairs).  unordered_map, however, is not acceptable.
        template <typename ForwardIt, std::enable_if_t<!std::is_convertible_v<ForwardIt, std::string_view>, int> = 0>
        void append(ForwardIt from, ForwardIt to) {
            if (has_child) throw std::logic_error{"Cannot append to list when a sublist is active"};
            using KeyType = std::remove_cv_t<std::decay_t<decltype(from->first)>>;
            using ValType = std::decay_t<decltype(from->second)>;
            static_assert(std::is_convertible_v<decltype(from->first), std::string_view>);
            static_assert(std::is_convertible_v<ValType, std::string_view> || std::is_integral_v<ValType>);
            using BadUnorderedMap = std::unordered_map<KeyType, ValType>;
            static_assert(!( // Disallow unordered_map iterators because they are not going to be ordered.
                              std::is_same_v<typename BadUnorderedMap::iterator, ForwardIt> ||
                              std::is_same_v<typename BadUnorderedMap::const_iterator, ForwardIt>));
            while (from != to) {
                const auto& [k, v] = *from++;
                append_impl(k);
                check_incrementing_key(k.size());
                append_impl(v);
            }
            append_intermediate_ends();
        }

        /// Appends a sub-dict value to this dict with the given key.  Returns a new bt_dict_producer
        /// that references the parent dict.  The parent cannot be added to until the subdict is
        /// destroyed.  Key must be (ascii-comparison) larger than the previous key.
        ///
        /// This is meant to be used via RAII:
        ///
        ///     buf data[32];
        ///     bt_dict_producer dict{data, sizeof(data)};
        ///     {
        ///         auto subdict = dict.begin_dict("myKey");
        ///         subdict.append("x", 42);
        ///     }
        ///     dict.append("y", "");
        ///     // `data` now contains: `d5:myKeyd1:xi42ee1:y0:e`
        ///
        /// If doing more complex lifetime management, take care not to allow the child instance to
        /// outlive the parent.
        bt_dict_producer append_dict(std::string_view key) {
            if (has_child) throw std::logic_error{"Cannot call append_dict while another nested list/dict is active"};
            append_impl(key);
            check_incrementing_key(key.size());
            return bt_dict_producer{this};
        }

        /// Appends a list to this dict with the given key (which must be ascii-larger than the previous
        /// key).  Returns a new bt_list_producer that references the parent dict.  The parent cannot be
        /// added to until the sublist is destroyed.
        ///
        /// This is meant to be used via RAII (see append_dict() for details).
        ///
        /// If doing more complex lifetime management, take care not to allow the child instance to
        /// outlive the parent.
        bt_list_producer append_list(std::string_view key)
        {
            if (has_child) throw std::logic_error{"Cannot call append_list while another nested list/dict is active"};
            append_impl(key);
            check_incrementing_key(key.size());
            return bt_list_producer{this};
        }

        /// Appends a bt_value, bt_dict, or bt_list to this bt_dict.  You must include the
        /// bt_value_producer.h header (either directly or via bt.h) to use this method.
        template <typename T>
        void append_bt(std::string_view key, const T& bt);
    };

    inline bt_list_producer::bt_list_producer(bt_list_producer* parent, std::string_view prefix)
        : data{parent}, buffer{parent->buffer}, from{buffer.first} {
        parent->has_child = true;
        buffer_append(prefix);
        append_intermediate_ends();
    }

    inline bt_list_producer::bt_list_producer(bt_dict_producer* parent, std::string_view prefix)
        : data{parent}, buffer{parent->buffer}, from{buffer.first} {
        parent->has_child = true;
        buffer_append(prefix);
        append_intermediate_ends();
    }

    inline bt_list_producer::bt_list_producer(bt_list_producer&& other)
        : data{std::move(other.data)}, buffer{other.buffer}, from{other.from}, to{other.to} {
        if (other.has_child) throw std::logic_error{"Cannot move bt_list/dict_producer with active sublists/subdicts"};
        var::visit([](auto& x) {
            if constexpr (!std::is_same_v<buf_span&, decltype(x)>)
                             x = nullptr;
        }, other.data);
    }

    inline void bt_list_producer::buffer_append(std::string_view d, bool advance) {
        var::visit(
            [d, advance, this](auto& x) {
                if constexpr (std::is_same_v<buf_span&, decltype(x)>) {
                    size_t avail = std::distance(x.first, x.second);
                    if (d.size() > avail)
                        throw std::length_error{"Cannot write bt_producer: buffer size exceeded"};
                    std::copy(d.begin(), d.end(), x.first);
                    to = x.first + d.size();
                    if (advance)
                        x.first += d.size();
                } else {
                    x->buffer_append(d, advance);
                }
            },
            data);
    }

    inline void bt_list_producer::append_intermediate_ends(size_t count) {
        static constexpr std::string_view eee = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"sv;
        return var::visit([this, count](auto& x) mutable {
            if constexpr (std::is_same_v<buf_span&, decltype(x)>) {
                for (; count > eee.size(); count -= eee.size())
                    buffer_append(eee, false);
                buffer_append(eee.substr(0, count), false);
            } else {
                // x is a parent pointer
                x->append_intermediate_ends(count + 1);
                to = x->to - 1; // Our `to` should be one 'e' before our parent's `to`.
            }
        }, data);
    }

    inline bt_list_producer::~bt_list_producer() {
        var::visit(
            [this](auto& x) {
                if constexpr (!std::is_same_v<buf_span&, decltype(x)>) {
                    if (!x)
                        return;
                    assert(!has_child);
                    assert(x->has_child);
                    x->has_child = false;
                    // We've already written the intermediate 'e', so just increment
                    // the buffer to finalize it.
                    buffer.first++;
                }
            },
            data);
    }

    inline bt_list_producer::bt_list_producer(char* begin, char* end, std::string_view prefix)
        : data{buf_span{begin, end}}, buffer{*std::get_if<buf_span>(&data)}, from{buffer.first} {
        buffer_append(prefix);
        append_intermediate_ends();
    }

    inline bt_list_producer bt_list_producer::append_list() {
        if (has_child) throw std::logic_error{"Cannot call append_list while another nested list/dict is active"};
        return bt_list_producer{this};
    }

    inline bt_dict_producer bt_list_producer::append_dict() {
        if (has_child) throw std::logic_error{"Cannot call append_dict while another nested list/dict is active"};
        return bt_dict_producer{this};
    }

} // namespace oxenc
