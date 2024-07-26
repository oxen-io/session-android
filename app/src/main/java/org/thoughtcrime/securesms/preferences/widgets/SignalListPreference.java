package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.widget.TextView;
import network.loki.messenger.R;

public class SignalListPreference extends ListPreference {

    private TextView rightSummaryTV;
    private CharSequence summary;
    private OnPreferenceClickListener clickListener;
    private CharSequence summarySpecifiedInLayoutXML;

    public SignalListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    public SignalListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public SignalListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public SignalListPreference(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        summarySpecifiedInLayoutXML = this.getSummary();
        if (summarySpecifiedInLayoutXML == null) { summarySpecifiedInLayoutXML = ""; }
        setWidgetLayoutResource(R.layout.preference_right_summary_widget);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        this.rightSummaryTV = (TextView)view.findViewById(R.id.right_summary);
        setSummary(this.summary);
    }

    @Override
    public void setSummary(CharSequence incomingSummary) {
        // Set the left "subtitle" summary such as "The information shown in notifications." etc.
        super.setSummary(summarySpecifiedInLayoutXML);

        // Then set the right summary to be the incoming drop-down selected option
        this.summary = incomingSummary;
        if (this.rightSummaryTV != null) {
            this.rightSummaryTV.setText(incomingSummary);
        }
    }

    @Override
    public void setOnPreferenceClickListener (OnPreferenceClickListener
    onPreferenceClickListener){
        this.clickListener = onPreferenceClickListener;
    }

    @Override
    protected void onClick () {
        if (clickListener == null || !clickListener.onPreferenceClick(this)) {
            super.onClick();
        }
    }
}
