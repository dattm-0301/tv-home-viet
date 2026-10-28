package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

/** A remote-friendly 0–100 slider: ◀ ▶ change it by 5. Track, accent fill and a white thumb with a soft ring. */
public class BrightnessSlider extends View {
    interface Listener { void onChanged(int value); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int value = 50;
    private Listener listener;

    public BrightnessSlider(Context c) { this(c, null); }

    public BrightnessSlider(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
    }

    void setListener(Listener l) { listener = l; }

    void setValue(int v) {
        value = Math.max(0, Math.min(100, v));
        invalidate();
    }

    int getValue() { return value; }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int next = Math.max(5, Math.min(100, value + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 5 : -5)));
            if (next != value) {
                setValue(next);
                if (listener != null) listener.onChanged(next);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float thumb = 9 * density, track = 3 * density, cy = getHeight() / 2f;
        float left = thumb + 3 * density, right = getWidth() - thumb - 3 * density;
        float x = left + (right - left) * value / 100f;
        paint.setColor(0x29FFFFFF);
        canvas.drawRoundRect(left, cy - track, right, cy + track, track, track, paint);
        paint.setColor(getContext().getColor(R.color.home_accent));
        canvas.drawRoundRect(left, cy - track, x, cy + track, track, track, paint);
        paint.setColor(isFocused() ? 0x80F3C77A : 0x2EF3C77A);
        canvas.drawCircle(x, cy, thumb + (isFocused() ? 4 : 3) * density, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(x, cy, thumb, paint);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        invalidate();
    }
}
