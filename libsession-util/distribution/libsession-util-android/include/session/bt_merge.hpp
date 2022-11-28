#pragma once
#include <oxenc/bt_value.h>

#include <cassert>
#ifndef NDEBUG
#include <algorithm>
#endif

namespace session::bt {

using oxenc::bt_dict;
using oxenc::bt_list;

/// Merges two bt dicts together: the returned dict includes all keys in a or b.  Keys in *both*
/// dicts get their value from `a`, otherwise the value is that of the dict that contains the key.
bt_dict merge(const bt_dict& a, const bt_dict& b);

/// Merges two ordered bt_lists together using a predicate to determine order.  The input lists must
/// be sorted to begin with.  `cmp` must be callable with a pair of `const bt_value&` arguments and
/// must return true if the first argument should be considered less than the second argument.  By
/// default this skips elements from b that compare equal to a value of a, but you can include all
/// the duplicates by specifying the `duplicates` parameter as true.
template <typename Compare>
bt_list merge_sorted(const bt_list& a, const bt_list& b, Compare cmp, bool duplicates = false) {
    bt_list result;
    auto it_a = a.begin();
    auto it_b = b.begin();

    assert(std::is_sorted(it_a, a.end(), cmp));
    assert(std::is_sorted(it_b, b.end(), cmp));

    if (duplicates) {
        while (it_a != a.end() && it_b != b.end()) {
            if (!cmp(*it_a, *it_b))  // *b <= *a
                result.push_back(*it_b++);
            else  // *a < *b
                result.push_back(*it_a++);
        }
    } else {
        while (it_a != a.end() && it_b != b.end()) {
            if (cmp(*it_b, *it_a))  // *b < *a
                result.push_back(*it_b++);
            else if (cmp(*it_a, *it_b))  // *a < *b
                result.push_back(*it_a++);
            else         // *a == *b
                ++it_b;  // skip it
        }
    }

    if (it_a != a.end())
        result.insert(result.end(), it_a, a.end());
    else if (it_b != b.end())
        result.insert(result.end(), it_b, b.end());

    return result;
}

}  // namespace session::bt
