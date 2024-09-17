package com.classic.sonorstream;

import android.os.Bundle;

import androidx.annotation.Nullable;

public class PreferenceFragment extends androidx.preference.PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

    }
}
