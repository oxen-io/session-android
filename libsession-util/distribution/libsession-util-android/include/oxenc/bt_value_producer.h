#include "bt_producer.h"
#include "bt_value.h"
#include "variant.h"
#include <type_traits>

/// This header provides the implementations of append_bt(bt_value/bt_list/bt_dict) for
/// bt_serialize.  (It is optional to avoid unnecessary includes when not wanted).

namespace oxenc {

    namespace detail {

        void serialize_list(bt_list_producer& out, const bt_list& l);
        void serialize_dict(bt_dict_producer& out, const bt_dict& l);

        struct dict_appender {
            bt_dict_producer& out;
            std::string_view key;
            dict_appender(bt_dict_producer& out, std::string_view key)
                : out{out}, key{key} {}

            void operator()(const bt_dict& d) {
                auto subdict = out.append_dict(key);
                serialize_dict(subdict, d);
            }
            void operator()(const bt_list& l) {
                auto sublist = out.append_list(key);
                serialize_list(sublist, l);
            }
            template <typename T>
            void operator()(const T& other) {
                out.append(key, other);
            }
        };

        struct list_appender {
            bt_list_producer& out;
            explicit list_appender(bt_list_producer& out)
                : out{out} {}

            void operator()(const bt_dict& d) {
                auto subdict = out.append_dict();
                serialize_dict(subdict, d);
            }
            void operator()(const bt_list& l) {
                auto sublist = out.append_list();
                serialize_list(sublist, l);
            }
            template <typename T>
            void operator()(const T& other) {
                out.append(other);
            }
        };

        inline void serialize_dict(bt_dict_producer& out, const bt_dict& d) {
            for (const auto& [k, v]: d)
                var::visit(dict_appender{out, k}, static_cast<const bt_variant&>(v));
        }

        inline void serialize_list(bt_list_producer& out, const bt_list& l) {
            for (auto& val : l)
                var::visit(list_appender{out}, static_cast<const bt_variant&>(val));
        }
    }

    template <>
    inline void bt_list_producer::append_bt(const bt_dict& bt) {
        auto subdict = append_dict();
        detail::serialize_dict(subdict, bt);
    }

    template <>
    inline void bt_list_producer::append_bt(const bt_list& bt) {
        auto sublist = append_list();
        detail::serialize_list(sublist, bt);
    }

    template <>
    inline void bt_list_producer::append_bt(const bt_value& bt) {
        var::visit(detail::list_appender{*this}, static_cast<const bt_variant&>(bt));
    }

    template <>
    inline void bt_dict_producer::append_bt(std::string_view key, const bt_dict& bt) {
        auto subdict = append_dict(key);
        detail::serialize_dict(subdict, bt);
    }

    template <>
    inline void bt_dict_producer::append_bt(std::string_view key, const bt_list& bt) {
        auto sublist = append_list(key);
        detail::serialize_list(sublist, bt);
    }

    template <>
    inline void bt_dict_producer::append_bt(std::string_view key, const bt_value& bt) {
        var::visit(detail::dict_appender{*this, key}, static_cast<const bt_variant&>(bt));
    }
}
