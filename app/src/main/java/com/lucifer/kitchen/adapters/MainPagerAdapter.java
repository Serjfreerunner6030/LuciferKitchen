package com.lucifer.kitchen.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.lucifer.kitchen.fragments.*;

public class MainPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 10;

    public MainPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new HomeFragment();
            case 1: return ActionListFragment.newInstance("unpack");
            case 2: return ActionListFragment.newInstance("repack");
            case 3: return ActionListFragment.newInstance("patch");
            case 4: return ActionListFragment.newInstance("boot");
            case 5: return new ApkFragment();
            case 6: return new AutoRepackFragment();
            case 7: return new TranslateFragment();
            case 8: return new PluginsFragment();
            case 9: return ActionListFragment.newInstance("tools");
            default: return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}
