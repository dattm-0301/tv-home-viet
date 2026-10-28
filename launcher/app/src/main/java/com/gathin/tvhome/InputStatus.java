package com.gathin.tvhome;

/** Connection metadata is not the currently playing input or proof of video playback. */
final class InputStatus {
    static int text(int state) {
        switch (state) {
            case 0: return R.string.connected;
            // Some firmware reports standby even for empty ports; avoid asserting a cable exists.
            case 1: return R.string.input_standby_uncertain;
            case 2: return R.string.not_connected;
            default: return 0;
        }
    }
}
