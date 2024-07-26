package org.thoughtcrime.securesms.components;

import static org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import com.squareup.phrase.Phrase;
import network.loki.messenger.R;

public class SwitchPreferenceCompat extends CheckBoxPreference {

    private static String LOCK_SCREEN_KEY = "pref_android_screen_lock";

    private Preference.OnPreferenceClickListener listener;

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutRes();
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutRes();
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutRes();
    }

    public SwitchPreferenceCompat(Context context) {
        super(context);
        setLayoutRes();
    }

    private void setLayoutRes() {
        setWidgetLayoutResource(R.layout.switch_compat_preference);

        if (this.hasKey()) {
            String key = this.getKey();

            // Substitute app name into lockscreen preference summary
            if (key.equalsIgnoreCase(LOCK_SCREEN_KEY)) {
                Context c = getContext();
                CharSequence substitutedSummaryCS = Phrase.from(c, R.string.lockAppDescriptionAndroid)
                                                        .put(APP_NAME_KEY, c.getString(R.string.app_name))
                                                        .format();
                this.setSummary(substitutedSummaryCS);
            }
        }
    }

    @Override
    public void setOnPreferenceClickListener(Preference.OnPreferenceClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onClick() {
        if (listener == null || !listener.onPreferenceClick(this)) {
            super.onClick();
        }
    }
}
