package org.thoughtcrime.securesms.groups

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.FragmentJoinCommunityBinding
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities

@AndroidEntryPoint
class JoinCommunityFragment : Fragment() {

    private lateinit var binding: FragmentJoinCommunityBinding

    lateinit var delegate: StartConversationDelegate

    var lastUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentJoinCommunityBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { delegate.onDialogBackPressed() }
        binding.closeButton.setOnClickListener { delegate.onDialogClosePressed() }

        fun showLoader() {
            binding.loader.visibility = View.VISIBLE
            binding.loader.animate().setDuration(150).alpha(1.0f).start()
        }

        fun hideLoader() {
            binding.loader.animate().setDuration(150).alpha(0.0f).setListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    binding.loader.visibility = View.GONE
                }
            })
        }

        fun joinCommunityIfPossible(url: String) {
            // Currently this won't try again on a failed URL but once we rework the whole
            // fragment into Compose with a ViewModel this won't be an issue anymore as the error
            // and state will come from Flows.
            if(lastUrl == url) return
            lastUrl = url

            lifecycleScope.launch(Dispatchers.Main) {
                val openGroup = try {
                    OpenGroupUrlParser.parseUrl(url)
                } catch (e: OpenGroupUrlParser.Error) {
                    when (e) {
                        is OpenGroupUrlParser.Error.MalformedURL, OpenGroupUrlParser.Error.NoRoom -> {
                            return@launch Toast.makeText(
                                activity,
                                context?.resources?.getString(R.string.communityJoinError),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is OpenGroupUrlParser.Error.InvalidPublicKey, OpenGroupUrlParser.Error.NoPublicKey -> {
                            return@launch Toast.makeText(
                                activity,
                                R.string.communityEnterUrlErrorInvalidDescription,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                showLoader()

                withContext(Dispatchers.IO) {
                    try {
                        val sanitizedServer = openGroup.server.removeSuffix("/")
                        val openGroupID = "$sanitizedServer.${openGroup.room}"
                        OpenGroupManager.add(
                            sanitizedServer,
                            openGroup.room,
                            openGroup.serverPublicKey,
                            requireContext()
                        )
                        val storage = MessagingModuleConfiguration.shared.storage
                        storage.onOpenGroupAdded(sanitizedServer, openGroup.room)
                        val threadID =
                            GroupManager.getOpenGroupThreadID(openGroupID, requireContext())
                        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())

                        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(
                            requireContext()
                        )
                        withContext(Dispatchers.Main) {
                            val recipient = Recipient.from(
                                requireContext(),
                                Address.fromSerialized(groupID),
                                false
                            )
                            openConversationActivity(requireContext(), threadID, recipient)
                            delegate.onDialogClosePressed()
                        }
                    } catch (e: Exception) {
                        Log.e("Loki", "Couldn't join community.", e)
                        withContext(Dispatchers.Main) {
                            hideLoader()
                            val txt = context?.getSubbedString(R.string.groupErrorJoin,
                                GROUP_NAME_KEY to url)
                            Toast.makeText(activity, txt, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val urlDelegate = { url: String -> joinCommunityIfPossible(url) }
        binding.viewPager.adapter = JoinCommunityFragmentAdapter(
            parentFragment = this,
            enterCommunityUrlDelegate = urlDelegate,
            scanQrCodeDelegate = urlDelegate
        )
        val mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.communityUrl)
                1 -> getString(R.string.qrScan)
                else -> throw IllegalStateException()
            }
        }
        mediator.attach()
    }

    private fun openConversationActivity(context: Context, threadId: Long, recipient: Recipient) {
        val intent = Intent(context, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
        context.startActivity(intent)
    }

}