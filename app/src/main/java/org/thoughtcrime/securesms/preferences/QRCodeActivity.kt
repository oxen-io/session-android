package org.thoughtcrime.securesms.preferences

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityQrCodeBinding
import network.loki.messenger.databinding.FragmentViewMyQrCodeBinding
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.FileProviderUtil
import org.thoughtcrime.securesms.util.QRCodeUtilities
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.util.toPx
import java.io.File
import java.io.FileOutputStream

class QRCodeActivity : PassphraseRequiredActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private lateinit var binding: ActivityQrCodeBinding
    private val adapter = QRCodeActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivityQrCodeBinding.inflate(layoutInflater)
        // Set content view
        setContentView(binding.root)
        // Set title
        supportActionBar!!.title = resources.getString(R.string.activity_qr_code_title)
        // Set up view pager
        binding.viewPager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(hexEncodedPublicKey: String) {
        createPrivateChatIfPossible(hexEncodedPublicKey)
    }

    fun createPrivateChatIfPossible(hexEncodedPublicKey: String) {
        if (!PublicKeyValidation.isValid(hexEncodedPublicKey)) { return Toast.makeText(this, R.string.invalid_session_id, Toast.LENGTH_SHORT).show() }
        val recipient = Recipient.from(this, Address.fromSerialized(hexEncodedPublicKey), false)
        val intent = Intent(this, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.ADDRESS, recipient.address)
        intent.setDataAndType(getIntent().data, getIntent().type)
        val existingThread = DatabaseComponent.get(this).threadDatabase().getThreadIdIfExistsFor(recipient)
        intent.putExtra(ConversationActivityV2.THREAD_ID, existingThread)
        startActivity(intent)
        finish()
    }
    // endregion
}

// region Adapter
private class QRCodeActivityAdapter(val activity: QRCodeActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> ViewMyQRCodeFragment()
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = activity.resources.getString(R.string.activity_qr_code_view_scan_qr_code_explanation)
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence? {
        return when (index) {
            0 -> activity.resources.getString(R.string.activity_qr_code_view_my_qr_code_tab_title)
            1 -> activity.resources.getString(R.string.activity_qr_code_view_scan_qr_code_tab_title)
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region View My QR Code Fragment
class ViewMyQRCodeFragment : Fragment() {
    private lateinit var binding: FragmentViewMyQrCodeBinding

    private val hexEncodedPublicKey: String
        get() {
            return TextSecurePreferences.getLocalNumber(requireContext())!!
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentViewMyQrCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val size = toPx(280, resources)
        val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
        binding.qrCodeImageView.setImageBitmap(qrCode)
//        val explanation = SpannableStringBuilder("This is your unique public QR code. Other users can scan this to start a conversation with you.")
//        explanation.setSpan(StyleSpan(Typeface.BOLD), 8, 34, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.explanationTextView.text = resources.getString(R.string.fragment_view_my_qr_code_explanation)
        binding.shareButton.setOnClickListener { shareQRCode() }
    }

    private fun shareQRCode() {
        val directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val fileName = "$hexEncodedPublicKey.png"
        val file = File(directory, fileName)
        file.createNewFile()
        val fos = FileOutputStream(file)
        val size = toPx(280, resources)
        val qrCode = QRCodeUtilities.encode(hexEncodedPublicKey, size, false, false)
        qrCode.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, FileProviderUtil.getUriFor(requireActivity(), file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = "image/png"
        startActivity(Intent.createChooser(intent, resources.getString(R.string.fragment_view_my_qr_code_share_title)))
    }
}
// endregion