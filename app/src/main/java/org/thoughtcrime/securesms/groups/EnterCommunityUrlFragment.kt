package org.thoughtcrime.securesms.groups

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentEnterCommunityUrlBinding
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.util.State
import java.util.Locale

class EnterCommunityUrlFragment : Fragment() {
    private lateinit var binding: FragmentEnterCommunityUrlBinding
    private val viewModel by activityViewModels<DefaultGroupsViewModel>()

    var delegate: EnterCommunityUrlDelegate? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentEnterCommunityUrlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatURLEditText.imeOptions = binding.chatURLEditText.imeOptions or 16777216 // Always use incognito keyboard
        binding.chatURLEditText.addTextChangedListener { text -> binding.joinPublicChatButton.isEnabled = !text.isNullOrBlank() }
        binding.chatURLEditText.setOnFocusChangeListener { _, hasFocus ->  binding.defaultRoomsContainer.isVisible = !hasFocus }
        binding.joinPublicChatButton.setOnClickListener { joinPublicChatIfPossible() }
        viewModel.defaultRooms.observe(viewLifecycleOwner) { state ->
            binding.defaultRoomsContainer.isVisible = state is State.Success
            binding.defaultRoomsLoaderContainer.isVisible = state is State.Loading
            binding.defaultRoomsLoader.isVisible = state is State.Loading
            when (state) {
                State.Loading -> {
                    // TODO: Show a binding.loader
                }
                is State.Error -> {
                    // TODO: Hide the binding.loader
                }
                is State.Success -> {
                    populateDefaultGroups(state.value)
                }
            }
        }
    }

    private fun populateDefaultGroups(groups: List<OpenGroupApi.DefaultGroup>) {
        binding.defaultRoomsFlexboxLayout.removeAllViews()
        groups.iterator().forEach { defaultGroup ->
            val chip = layoutInflater.inflate(R.layout.default_group_chip, binding.defaultRoomsFlexboxLayout, false) as Chip
            val drawable = defaultGroup.image?.let { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                RoundedBitmapDrawableFactory.create(resources, bitmap).apply {
                    isCircular = true
                }
            }
            chip.chipIcon = drawable
            chip.text = defaultGroup.name
            chip.setOnClickListener {
                delegate?.handleCommunityUrlEntered(defaultGroup.joinURL)
            }
            binding.defaultRoomsFlexboxLayout.addView(chip)
        }
    }

    // region Convenience
    private fun joinPublicChatIfPossible() {
        val inputMethodManager = requireContext().getSystemService(BaseActionBarActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.chatURLEditText.windowToken, 0)
        val chatURL = binding.chatURLEditText.text.trim().toString().toLowerCase(Locale.US)
        delegate?.handleCommunityUrlEntered(chatURL)
    }
    // endregion
}

fun interface EnterCommunityUrlDelegate {
    fun handleCommunityUrlEntered(url: String)
}
