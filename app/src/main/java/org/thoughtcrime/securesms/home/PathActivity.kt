package org.thoughtcrime.securesms.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityPathBinding
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.getColorFromAttr
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.util.GlowViewUtilities
import org.thoughtcrime.securesms.util.IpToCountryName
import org.thoughtcrime.securesms.util.PathDotView
import org.thoughtcrime.securesms.util.UiModeUtilities
import org.thoughtcrime.securesms.util.animateSizeChange
import org.thoughtcrime.securesms.util.disableClipping
import org.thoughtcrime.securesms.util.getAccentColor
import org.thoughtcrime.securesms.util.getColorWithID
import javax.inject.Inject

@AndroidEntryPoint
class PathActivity: PassphraseRequiredActionBarActivity() {
    private lateinit var binding: ActivityPathBinding

    @Inject
    lateinit var ipToCountryName: IpToCountryName

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivityPathBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar!!.title = resources.getString(R.string.activity_path_title)
        binding.learnMoreButton.setOnClickListener { learnMore() }

        update(listOf(null, null, null))

        lifecycleScope.launch {
            OnionRequestAPI.pathFlow
                .map { it.map { ipToCountryName[it.ip] } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        update(it)
                    }
                }
        }
    }

    private fun update(countryNames: List<String?>) {
        binding.pathRowsContainer.removeAllViews()

        binding.spinner.isVisible = countryNames.isEmpty()

        if (countryNames.isEmpty()) return

        val dotAnimationRepeatInterval = countryNames.count().toLong() * 1000 + 1000
        val pathRows = countryNames.mapIndexed { index, countryName ->
            getPathRow(countryName, LineView.Location.Middle, index.toLong() * 1000 + 2000, dotAnimationRepeatInterval, index == 0)
        }
        val youRow = getPathRow(resources.getString(R.string.activity_path_device_row_title), null, LineView.Location.Top, 1000, dotAnimationRepeatInterval)
        val destinationRow = getPathRow(resources.getString(R.string.activity_path_destination_row_title), null, LineView.Location.Bottom, countryNames.count().toLong() * 1000 + 2000, dotAnimationRepeatInterval)
        val rows = listOf( youRow ) + pathRows + listOf( destinationRow )
        for (row in rows) {
            binding.pathRowsContainer.addView(row)
        }
    }

    private fun getPathRow(title: String, subtitle: String?, location: LineView.Location, dotAnimationStartDelay: Long, dotAnimationRepeatInterval: Long): LinearLayout {
        val mainContainer = LinearLayout(this)
        mainContainer.orientation = LinearLayout.HORIZONTAL
        mainContainer.gravity = Gravity.CENTER_VERTICAL
        mainContainer.disableClipping()
        val mainContainerLayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        mainContainer.layoutParams = mainContainerLayoutParams
        val lineView = LineView(this, location, dotAnimationStartDelay, dotAnimationRepeatInterval)
        val lineViewLayoutParams = LinearLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.path_row_expanded_dot_size), resources.getDimensionPixelSize(R.dimen.path_row_height))
        lineView.layoutParams = lineViewLayoutParams
        mainContainer.addView(lineView)
        val titleTextView = TextView(this)
        titleTextView.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary))
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.medium_font_size))
        titleTextView.text = title
        titleTextView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
        val titleContainer = LinearLayout(this)
        titleContainer.orientation = LinearLayout.VERTICAL
        titleContainer.addView(titleTextView)
        val titleContainerLayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        titleContainerLayoutParams.marginStart = resources.getDimensionPixelSize(R.dimen.large_spacing)
        titleContainer.layoutParams = titleContainerLayoutParams
        mainContainer.addView(titleContainer)
        if (subtitle != null) {
            val subtitleTextView = TextView(this)
            subtitleTextView.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary))
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.small_font_size))
            subtitleTextView.text = subtitle
            subtitleTextView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
            titleContainer.addView(subtitleTextView)
        }
        return mainContainer
    }

    private fun getPathRow(countryName: String?, location: LineView.Location, dotAnimationStartDelay: Long, dotAnimationRepeatInterval: Long, isGuardSnode: Boolean): LinearLayout {
        val title = if (isGuardSnode) resources.getString(R.string.activity_path_guard_node_row_title) else resources.getString(R.string.activity_path_service_node_row_title)
        val subtitle = countryName ?: resources.getString(R.string.activity_path_resolving_progress)
        return getPathRow(title, subtitle, location, dotAnimationStartDelay, dotAnimationRepeatInterval)
    }

    private fun learnMore() {
        try {
            val url = "https://getsession.org/faq/#onion-routing"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
        }
    }

    private class LineView : RelativeLayout {
        private lateinit var location: Location
        private var dotAnimationStartDelay: Long = 0
        private var dotAnimationRepeatInterval: Long = 0
        private var job: Job? = null

        private val dotView by lazy {
            val result = PathDotView(context)
            result.setBackgroundResource(R.drawable.accent_dot)
            result.mainColor = context.getAccentColor()
            result
        }

        enum class Location {
            Top, Middle, Bottom
        }

        constructor(context: Context, location: Location, dotAnimationStartDelay: Long, dotAnimationRepeatInterval: Long) : super(context) {
            this.location = location
            this.dotAnimationStartDelay = dotAnimationStartDelay
            this.dotAnimationRepeatInterval = dotAnimationRepeatInterval
            setUpViewHierarchy()
        }

        constructor(context: Context) : super(context) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
            throw Exception("Use LineView(context:location:dotAnimationStartDelay:dotAnimationRepeatInterval:) instead.")
        }

        private fun setUpViewHierarchy() {
            disableClipping()
            val lineView = View(context)
            lineView.setBackgroundColor(context.getColorFromAttr(android.R.attr.textColorPrimary))
            val lineViewHeight = when (location) {
                Location.Top, Location.Bottom -> resources.getDimensionPixelSize(R.dimen.path_row_height) / 2
                Location.Middle -> resources.getDimensionPixelSize(R.dimen.path_row_height)
            }
            val lineViewLayoutParams = LayoutParams(1, lineViewHeight)
            when (location) {
                Location.Top -> lineViewLayoutParams.addRule(ALIGN_PARENT_BOTTOM)
                Location.Middle, Location.Bottom -> lineViewLayoutParams.addRule(ALIGN_PARENT_TOP)
            }
            lineViewLayoutParams.addRule(CENTER_HORIZONTAL)
            lineView.layoutParams = lineViewLayoutParams
            addView(lineView)
            val dotViewSize = resources.getDimensionPixelSize(R.dimen.path_row_dot_size)
            val dotViewLayoutParams = LayoutParams(dotViewSize, dotViewSize)
            dotViewLayoutParams.addRule(CENTER_IN_PARENT)
            dotView.layoutParams = dotViewLayoutParams
            addView(dotView)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            startAnimation()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()

            stopAnimation()
        }

        private fun startAnimation() {
            job?.cancel()
            job = GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    while (isActive) {
                        delay(dotAnimationStartDelay)
                        expand()
                        delay(EXPAND_ANIM_DELAY_MILLS)
                        collapse()
                        delay(dotAnimationRepeatInterval)
                    }
                }
            }
        }

        private fun stopAnimation() {
            job?.cancel()
            job = null
        }

        private fun expand() {
            dotView.animateSizeChange(R.dimen.path_row_dot_size, R.dimen.path_row_expanded_dot_size)
            @ColorRes val startColorID = if (UiModeUtilities.isDayUiMode(context)) R.color.transparent_black_30 else R.color.black
            val startColor = context.resources.getColorWithID(startColorID, context.theme)
            val endColor = context.getAccentColor()
            GlowViewUtilities.animateShadowColorChange(dotView, startColor, endColor)
        }

        private fun collapse() {
            dotView.animateSizeChange(R.dimen.path_row_expanded_dot_size, R.dimen.path_row_dot_size)
            @ColorRes val endColorID = if (UiModeUtilities.isDayUiMode(context)) R.color.transparent_black_30 else R.color.black
            val startColor = context.getAccentColor()
            val endColor = context.resources.getColorWithID(endColorID, context.theme)
            GlowViewUtilities.animateShadowColorChange(dotView, startColor, endColor)
        }

        companion object {
            private const val EXPAND_ANIM_DELAY_MILLS = 1000L
        }
    }
}
