package org.thoughtcrime.securesms.home

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentNewConversationBinding
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.contacts.ContactClickListener
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem
import org.thoughtcrime.securesms.contacts.ContactSelectionListLoader
import org.thoughtcrime.securesms.mms.GlideApp

@AndroidEntryPoint
class NewConversationDialogFragment : BottomSheetDialogFragment(), LoaderManager.LoaderCallbacks<List<ContactSelectionListItem>>,
    ContactClickListener {

    private lateinit var binding: FragmentNewConversationBinding
    private var delegate: NewConversationDelegate? = null

    private val listAdapter by lazy {
        val result = ContactSelectionListAdapter(requireActivity(), false)
        result.glide = GlideApp.with(this)
        result.contactClickListener = this
        result
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialog ->
            val dialogc = dialog as BottomSheetDialog
            val bottomSheet = dialogc.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return@setOnShowListener
            val bottomSheetBehavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.peekHeight = Resources.getSystem().displayMetrics.heightPixels
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        return bottomSheetDialog
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NewConversationDelegate) {
            delegate = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Session_BottomSheet)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNewConversationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.closeButton.setOnClickListener { dismiss() }
        binding.newMessageButton.setOnClickListener { delegate?.createNewMessage() }
        binding.newGroupButton.setOnClickListener { delegate?.createNewGroup() }
        binding.joinCommunityButton.setOnClickListener { delegate?.joinCommunity() }
        binding.contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = listAdapter
        }
    }

    override fun onStart() {
        super.onStart()
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onStop() {
        super.onStop()
        LoaderManager.getInstance(this).destroyLoader(0)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<ContactSelectionListItem>> {
        return ContactSelectionListLoader(requireActivity(), ContactSelectionListLoader.DisplayMode.FLAG_CONTACTS, null)
    }

    override fun onLoadFinished(loader: Loader<List<ContactSelectionListItem>>, items: List<ContactSelectionListItem>) {
        update(items)
    }

    override fun onLoaderReset(loader: Loader<List<ContactSelectionListItem>>) {
        update(listOf())
    }

    private fun update(items: List<ContactSelectionListItem>) {
        if (activity?.isDestroyed == true) {
            Log.e(
                NewConversationDialogFragment::class.java.name,
                "Received a loader callback after the fragment was detached from the activity.",
                IllegalStateException())
            return
        }
        listAdapter.items = items.filterIsInstance<ContactSelectionListItem.Contact>()
            .sortedBy { it.recipient.address }
            .groupBy { it.recipient.address.serialize()[0] }
            .flatMap { listOf(ContactSelectionListItem.Header("${it.key}")) + it.value}
    }

    override fun onContactClick(contact: Recipient) {
        listAdapter.onContactClick(contact)
    }

    override fun onContactSelected(contact: Recipient) {
        delegate?.contactSelected(contact.address.serialize())
    }

    override fun onContactDeselected(contact: Recipient) {
    }
}

// region Delegate
interface NewConversationDelegate {
    fun contactSelected(address: String)
    fun joinCommunity()
    fun createNewMessage()
    fun createNewGroup()
}
// endregion