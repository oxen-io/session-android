package org.thoughtcrime.securesms.home.search

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import network.loki.messenger.databinding.ViewGlobalSearchInputBinding
import org.thoughtcrime.securesms.util.SimpleTextWatcher
import org.thoughtcrime.securesms.util.addTextChangedListener

class GlobalSearchInputLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs),
        View.OnFocusChangeListener,
        TextView.OnEditorActionListener {

    var binding: ViewGlobalSearchInputBinding = ViewGlobalSearchInputBinding.inflate(LayoutInflater.from(context), this, true)

    var listener: GlobalSearchInputLayoutListener? = null

    private val _query = MutableStateFlow<CharSequence>("")
    val query: StateFlow<CharSequence> = _query

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.searchInput.onFocusChangeListener = this
        binding.searchInput.addTextChangedListener(::setQuery)
        binding.searchInput.setOnEditorActionListener(this)
        binding.searchInput.filters = arrayOf<InputFilter>(LengthFilter(100)) // 100 char search limit
        binding.searchCancel.setOnClickListener { clearSearch(true) }
        binding.searchClear.setOnClickListener { clearSearch(false) }
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        if (v === binding.searchInput) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).apply {
                if (hasFocus) showSoftInput(v, 0)
                else hideSoftInputFromWindow(windowToken, 0)
            }
            listener?.onInputFocusChanged(hasFocus)
        }
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (v === binding.searchInput && actionId == EditorInfo.IME_ACTION_SEARCH) {
            binding.searchInput.clearFocus()
            return true
        }
        return false
    }

    fun clearSearch(clearFocus: Boolean) {
        binding.searchInput.text = null
        setQuery("")
        if (clearFocus) {
            binding.searchInput.clearFocus()
        }
    }

    private fun setQuery(query: String) {
        _query.value = query
    }

    interface GlobalSearchInputLayoutListener {
        fun onInputFocusChanged(hasFocus: Boolean)
    }

}