/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.gestures;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.LabeledSeekBarPreference;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.search.SearchIndexable;

/**
 * A fragment to include all the settings related to Gesture Navigation mode.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class GestureNavigationSettingsFragment extends DashboardFragment {

    public static final String TAG = "GestureNavigationSettingsFragment";

    public static final String GESTURE_NAVIGATION_SETTINGS =
            "com.android.settings.GESTURE_NAVIGATION_SETTINGS";

    private static final String LEFT_EDGE_SEEKBAR_KEY = "gesture_left_back_sensitivity";
    private static final String RIGHT_EDGE_SEEKBAR_KEY = "gesture_right_back_sensitivity";
    private static final String BACK_REGION_SEEKBAR_KEY = "gesture_back_height";

    private WindowManager mWindowManager;
    private BackGestureIndicatorView mIndicatorView;

    private float[] mBackGestureInsetScales;
    private float[] mBackRegionScales;
    private float mDefaultBackGestureInset;
    private int mCurrentRightWidth;
    private int mCurrentLeftWidth;

    public GestureNavigationSettingsFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIndicatorView = new BackGestureIndicatorView(getActivity());
        mWindowManager = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        final Resources res = getActivity().getResources();
        mDefaultBackGestureInset = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_backGestureInset);
        mBackGestureInsetScales = getFloatArray(res.obtainTypedArray(
                com.android.internal.R.array.config_backGestureInsetScales));
        mBackRegionScales = getFloatArray(res.obtainTypedArray(
                com.android.internal.R.array.config_backRegionScales));

        initSeekBarPreference(LEFT_EDGE_SEEKBAR_KEY);
        initSeekBarPreference(RIGHT_EDGE_SEEKBAR_KEY);
        initSeekBarPreference(BACK_REGION_SEEKBAR_KEY);
    }

    @Override
    public void onResume() {
        super.onResume();

        mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                getActivity().getWindow().getAttributes()));
    }

    @Override
    public void onPause() {
        super.onPause();

        mWindowManager.removeView(mIndicatorView);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.gesture_navigation_settings;
    }

    @Override
    public int getHelpResource() {
        // TODO(b/146001201): Replace with gesture navigation help page when ready.
        return R.string.help_uri_default;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURE_NAV_BACK_SENSITIVITY_DLG;
    }

    private void initSeekBarPreference(final String key) {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(key);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);

        final String settingsKey = key == BACK_REGION_SEEKBAR_KEY
                ? Settings.Secure.BACK_GESTURE_HEIGHT
                : (key == LEFT_EDGE_SEEKBAR_KEY
                ? Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT
                : Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT);
        float initScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), settingsKey, key == BACK_REGION_SEEKBAR_KEY ? 0.0f : 1.0f);

        // needed if we just change the height
        float currentWidthScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT, 1.0f);
        mCurrentRightWidth = (int) (mDefaultBackGestureInset * currentWidthScale);
        currentWidthScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT, 1.0f);
        mCurrentLeftWidth = (int) (mDefaultBackGestureInset * currentWidthScale);

        // Find the closest value to initScale
        float minDistance = Float.MAX_VALUE;
        int minDistanceIndex = -1;
        final float[] prefScales = key == BACK_REGION_SEEKBAR_KEY ? mBackRegionScales : mBackGestureInsetScales;
        for (int i = 0; i < prefScales.length; i++) {
            float d = Math.abs(prefScales[i] - initScale);
            if (d < minDistance) {
                minDistance = d;
                minDistanceIndex = i;
            }
        }
        pref.setProgress(minDistanceIndex);

        if (key != BACK_REGION_SEEKBAR_KEY) {
            pref.setOnPreferenceChangeListener((p, v) -> {
                final int width = (int) (mDefaultBackGestureInset * prefScales[(int) v]);
                mIndicatorView.setIndicatorWidth(width, key == LEFT_EDGE_SEEKBAR_KEY);
                if (key == LEFT_EDGE_SEEKBAR_KEY) {
                    mCurrentLeftWidth = width;
                } else {
                    mCurrentRightWidth = width;
                }
                return true;
            });

            pref.setOnPreferenceChangeStopListener((p, v) -> {
                mIndicatorView.setIndicatorWidth(0, key == LEFT_EDGE_SEEKBAR_KEY);
                final float scale = prefScales[(int) v];
                Settings.Secure.putFloat(getContext().getContentResolver(), settingsKey, scale);
                return true;
            });
        } else {
            pref.setOnPreferenceChangeListener((p, v) -> {
                final int region = (int) prefScales[(int) v];
                mIndicatorView.setIndicatorHeightScale(region);
                // dont use updateViewLayout else it will animate
                mWindowManager.removeView(mIndicatorView);
                mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                        getActivity().getWindow().getAttributes()));
                // peek the indicators
                mIndicatorView.setIndicatorWidth(mCurrentRightWidth, false);
                mIndicatorView.setIndicatorWidth(mCurrentLeftWidth, true);
                return true;
            });

            pref.setOnPreferenceChangeStopListener((p, v) -> {
                final int region = (int) prefScales[(int) v];
                mIndicatorView.setIndicatorWidth(0, false);
                mIndicatorView.setIndicatorWidth(0, true);
                Settings.Secure.putInt(getContext().getContentResolver(), settingsKey, region);
                return true;
            });
        }
    }

    private static float[] getFloatArray(TypedArray array) {
        int length = array.length();
        float[] floatArray = new float[length];
        for (int i = 0; i < length; i++) {
            floatArray[i] = array.getFloat(i, 1.0f);
        }
        array.recycle();
        return floatArray;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.gesture_navigation_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return SystemNavigationPreferenceController.isGestureAvailable(context);
                }
            };

}
