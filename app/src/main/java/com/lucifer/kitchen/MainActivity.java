package com.lucifer.kitchen;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.lucifer.kitchen.adapters.MainPagerAdapter;
import com.lucifer.kitchen.utils.BinaryManager;
import com.lucifer.kitchen.utils.PermissionHelper;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private String[] tabTitles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request storage permissions
        PermissionHelper.checkAndRequestPermissions(this, 100);

        // Extract tools from assets on first run / update
        BinaryManager bm = new BinaryManager(this);
        if (!bm.isSetupComplete()) {
            new Thread(() -> {
                try {
                    bm.extractTools(MainActivity.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                runOnUiThread(() -> {
                    // Tools ready
                });
            }).start();
        }

        tabTitles = new String[]{
            getString(R.string.tab_home),
            getString(R.string.tab_unpack),
            getString(R.string.tab_repack),
            getString(R.string.tab_patch),
            getString(R.string.tab_boot),
            getString(R.string.tab_apk),
            getString(R.string.tab_auto),
            getString(R.string.tab_translate),
            getString(R.string.tab_plugins),
            getString(R.string.tab_tools)
        };

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);

        new TabLayoutMediator(tabLayout, viewPager,
            (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_terminal) {
            startActivity(new Intent(this, TerminalActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_desc)
                .setPositiveButton(R.string.ok, null)
                .show();
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (!PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
                new AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage("Storage access is needed for ROM operations.")
                    .setPositiveButton(R.string.ok, (d, w) ->
                        PermissionHelper.checkAndRequestPermissions(this, 100))
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        }
    }
}
