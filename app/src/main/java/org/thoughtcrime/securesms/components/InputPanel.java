package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

public class InputPanel extends LinearLayout {

    public InputPanel(Context context) {
        super(context);
    }

    public InputPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InputPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public interface MediaListener {
        void onMediaSelected(@NonNull Uri uri, String contentType);
    }
}
