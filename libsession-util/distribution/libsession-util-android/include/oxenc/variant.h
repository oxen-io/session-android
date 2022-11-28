#pragma once
// Workarounds for macos compatibility.  On macOS we aren't allowed to touch anything in
// std::variant that could throw if compiling with a target <10.14 because Apple fails hard at
// properly updating their STL.  Thus, if compiling in such a mode, we have to introduce
// workarounds.
//
// This header defines a `var` namespace with `var::get` and `var::visit` implementations.  On
// everything except broken backwards macos, this is just an alias to `std`.  On broken backwards
// macos, we provide implementations that throw std::runtime_error in failure cases since the
// std::bad_variant_access exception can't be touched.
//
// You also get a BROKEN_APPLE_VARIANT macro defined if targetting a problematic mac architecture.

#include <variant>

#ifdef __APPLE__
#  include <AvailabilityVersions.h>
#  if defined(__APPLE__) && MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_14
#    define BROKEN_APPLE_VARIANT
#  endif
#endif

#ifndef BROKEN_APPLE_VARIANT

namespace var = std; // Oh look, actual C++17 support

#else

// Oh look, apple.

namespace var {

// Apple won't let us use std::visit or std::get if targetting some version of macos earlier than
// 10.14 because Apple is awful about not updating their STL.  So we have to provide our own, and
// then call these without `std::` -- on crappy macos we'll come here, on everything else we'll ADL
// to the std:: implementation.
template <typename T, typename... Types>
constexpr T& get(std::variant<Types...>& var) {
    if (auto* v = std::get_if<T>(&var)) return *v;
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <typename T, typename... Types>
constexpr const T& get(const std::variant<Types...>& var) {
    if (auto* v = std::get_if<T>(&var)) return *v;
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <typename T, typename... Types>
constexpr const T&& get(const std::variant<Types...>&& var) {
    if (auto* v = std::get_if<T>(&var)) return std::move(*v);
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <typename T, typename... Types>
constexpr T&& get(std::variant<Types...>&& var) {
    if (auto* v = std::get_if<T>(&var)) return std::move(*v);
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <size_t I, typename... Types>
constexpr auto& get(std::variant<Types...>& var) {
    if (auto* v = std::get_if<I>(&var)) return *v;
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <size_t I, typename... Types>
constexpr const auto& get(const std::variant<Types...>& var) {
    if (auto* v = std::get_if<I>(&var)) return *v;
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <size_t I, typename... Types>
constexpr const auto&& get(const std::variant<Types...>&& var) {
    if (auto* v = std::get_if<I>(&var)) return std::move(*v);
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}
template <size_t I, typename... Types>
constexpr auto&& get(std::variant<Types...>&& var) {
    if (auto* v = std::get_if<I>(&var)) return std::move(*v);
    throw std::runtime_error{"Bad variant access -- variant does not contain the requested type"};
}

template <size_t I, size_t... More, class Visitor, class Variant>
constexpr auto visit_helper(Visitor&& vis, Variant&& var) {
    if (var.index() == I)
        return vis(var::get<I>(std::forward<Variant>(var)));
    else if constexpr (sizeof...(More) > 0)
        return visit_helper<More...>(std::forward<Visitor>(vis), std::forward<Variant>(var));
    else
        throw std::runtime_error{"Bad visit -- variant is valueless"};
}

template <size_t... Is, class Visitor, class Variant>
constexpr auto visit_helper(Visitor&& vis, Variant&& var, std::index_sequence<Is...>) {
    return visit_helper<Is...>(std::forward<Visitor>(vis), std::forward<Variant>(var));
}

// Only handle a single variant here because multi-variant invocation is notably harder (and we
// don't need it).
template <class Visitor, class Variant>
constexpr auto visit(Visitor&& vis, Variant&& var) {
    return visit_helper(std::forward<Visitor>(vis), std::forward<Variant>(var),
            std::make_index_sequence<std::variant_size_v<std::remove_reference_t<Variant>>>{});
}

} // namespace var

#endif
