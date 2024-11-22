package com.classic.sonorstream;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TogglePagerAdapter extends FragmentStateAdapter {

    public TogglePagerAdapter(FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @Override
    public int getItemCount() {
        return 2; // Number of fragments
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return (position == 0) ? new StreamActivity() : new ReceiveActivity();
    }
}
