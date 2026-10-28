package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * The two lines of a status button in the top dock: a small spaced-out uppercase label ("NGUỒN VÀO") over the
 * value ("HDMI 1"). One TextView, so the existing code that sizes, ellipsizes and re-icons the pills keeps working;
 * the value is the second line and ellipsizes at the button's maximum width.
 */
final class DockText {
    private DockText() { }

    /** Sets label and value; does nothing when both are unchanged, so a refresh does not lay the dock out again. */
    static void set(TextView button, String label, CharSequence value) {
        String key = label + "\n" + value;
        if (key.equals(button.getTag(R.id.btnInputs))) return;
        button.setTag(R.id.btnInputs, key);
        Resources r = button.getResources();
        float size = r.getDimension(R.dimen.home_status_label_text);
        Typeface face = UiFont.bold(button.getContext());
        SpannableStringBuilder text = new SpannableStringBuilder();
        String upper = label;
        text.append(upper);
        text.setSpan(new LabelSpan(size, face, 0f, 0.72f), 0, upper.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append('\n').append(value);
        button.setText(text);
        button.setContentDescription(label + ", " + value);
    }

    /** The label: own size, typeface and letter spacing, drawn in the button's current text colour at an alpha. */
    private static final class LabelSpan extends ReplacementSpan {
        private final float size, spacing, alpha;
        private final Typeface face;
        private final TextPaint own = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        LabelSpan(float size, Typeface face, float spacing, float alpha) {
            this.size = size;
            this.face = face;
            this.spacing = spacing;
            this.alpha = alpha;
        }

        private TextPaint paint(Paint base) {
            own.set(base);
            own.setTextSize(size);
            own.setTypeface(face);
            own.setLetterSpacing(spacing);
            return own;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            TextPaint p = paint(paint);
            if (fm != null) {
                Paint.FontMetricsInt own = p.getFontMetricsInt();
                fm.ascent = fm.top = own.ascent;
                fm.descent = fm.bottom = own.descent + Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 1, android.content.res.Resources.getSystem().getDisplayMetrics()));
                fm.leading = 0;
            }
            return Math.round(p.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint paint) {
            TextPaint p = paint(paint);
            int color = paint.getColor();
            p.setColor((Math.round(((color >>> 24) & 0xFF) * alpha) << 24) | (color & 0xFFFFFF));
            canvas.drawText(text, start, end, x, y, p);
        }
    }
}
