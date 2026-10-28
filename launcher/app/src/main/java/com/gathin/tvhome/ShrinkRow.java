package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * A horizontal LinearLayout that, when its children do not fit, narrows the ones tagged "shrink" or "shrink:N"
 * (N = how hard it gives way, like CSS flex-shrink) instead of letting the last child get squeezed to nothing.
 * The shrinking children are wrap_content views whose text ellipsizes, so long labels end in "…" and the
 * fixed parts after them (a unit, a time, a countdown) stay readable.
 */
public class ShrinkRow extends LinearLayout {
    public ShrinkRow(Context context) { super(context); }
    public ShrinkRow(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int n = getChildCount();
        for (int i = 0; i < n; i++) {
            View c = getChildAt(i);
            if (factor(c) > 0) c.getLayoutParams().width = LayoutParams.WRAP_CONTENT;
        }
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        int avail = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int total = 0;
        float weighted = 0;
        int[] natural = new int[n];
        for (int i = 0; i < n; i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            if (lp.weight > 0 && lp.width == 0) {
                total += lp.leftMargin + lp.rightMargin;   // a spacer takes what is left, nothing more
                continue;
            }
            measureChildWithMargins(c, unspecified, 0, heightSpec, 0);
            natural[i] = c.getMeasuredWidth();
            total += natural[i] + lp.leftMargin + lp.rightMargin;
            weighted += natural[i] * factor(c);
        }
        int overflow = total - avail;
        if (overflow > 0 && weighted > 0) {
            for (int i = 0; i < n; i++) {
                View c = getChildAt(i);
                float f = factor(c);
                if (f <= 0 || c.getVisibility() == GONE) continue;
                int cut = Math.round(overflow * natural[i] * f / weighted);
                c.getLayoutParams().width = Math.max(0, natural[i] - cut);
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private static float factor(View c) {
        Object tag = c.getTag();
        if (!(tag instanceof String) || !((String) tag).startsWith("shrink")) return 0;
        String s = (String) tag;
        int colon = s.indexOf(':');
        if (colon < 0) return 1;
        try {
            return Float.parseFloat(s.substring(colon + 1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
