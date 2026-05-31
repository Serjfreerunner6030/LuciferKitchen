package com.lucifer.kitchen.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.lucifer.kitchen.R;
import com.lucifer.kitchen.utils.PluginManager;
import com.lucifer.kitchen.utils.PluginManager.*;
import com.lucifer.kitchen.utils.ProjectManager;
import com.lucifer.kitchen.utils.ShellExecutor;
import java.io.*;
import java.util.*;

/**
 * DNA-compatible plugin UI.
 *
 * Layout:
 *   [Import Plugin (.zip2 / .mpk)]
 *   [Remove Plugin] (multi-select, like DNA)
 *   ---
 *   Plugin List:
 *     For each plugin with index.xml:
 *       Parses <group>/<action>/<param> into native cards
 *       Each action renders as a card with title, desc, param inputs, and Run button
 *     For plugins without index.xml but with .sh:
 *       Simple card with Run button
 */
public class PluginsFragment extends Fragment {

    private PluginManager pluginManager;
    private ProjectManager projectManager;
    private LinearLayout pluginsContainer;
    private TextView tvNoPlugins;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importPluginFromUri(uri);
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plugins, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pluginManager = new PluginManager(requireContext());
        projectManager = new ProjectManager(requireContext());

        pluginsContainer = view.findViewById(R.id.pluginsContainer);
        tvNoPlugins = view.findViewById(R.id.tvNoPlugins);

        MaterialButton btnImport = view.findViewById(R.id.btnImportPlugin);
        btnImport.setOnClickListener(v -> openFilePicker());

        addRemovePluginButton(view);
        refreshPluginList();
    }

    private void addRemovePluginButton(View root) {
        ViewGroup parent = (ViewGroup) pluginsContainer.getParent();
        MaterialButton btnRemove = new MaterialButton(requireContext());
        btnRemove.setText(R.string.plugins_delete);
        btnRemove.setBackgroundColor(0xFF424242);
        btnRemove.setCornerRadius(16);
        btnRemove.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 24;
        btnRemove.setLayoutParams(params);
        btnRemove.setOnClickListener(v -> showRemoveDialog());

        int idx = parent.indexOfChild(pluginsContainer);
        if (idx >= 0) parent.addView(btnRemove, idx);
        else parent.addView(btnRemove);
    }

    private void showRemoveDialog() {
        String[] names = pluginManager.getPluginNames();
        if (names.length == 0) {
            Toast.makeText(requireContext(), R.string.plugins_none, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean[] checked = new boolean[names.length];
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.plugins_select_delete)
            .setMultiChoiceItems(names, checked, (d, w, c) -> checked[w] = c)
            .setPositiveButton(R.string.ok, (d, w) -> {
                List<String> toDelete = new ArrayList<>();
                for (int i = 0; i < names.length; i++) {
                    if (checked[i]) toDelete.add(names[i]);
                }
                if (!toDelete.isEmpty()) {
                    pluginManager.deletePlugins(toDelete.toArray(new String[0]));
                    Toast.makeText(requireContext(), R.string.plugins_removed, Toast.LENGTH_SHORT).show();
                    refreshPluginList();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.plugins_select_mpk)));
    }

    private void importPluginFromUri(Uri uri) {
        AlertDialog progress = new AlertDialog.Builder(requireContext())
            .setMessage(R.string.running).setCancelable(false).show();

        new Thread(() -> {
            try {
                File tempFile = new File(requireContext().getCacheDir(), "temp_plugin.zip2");
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                fos.close();
                is.close();

                String pluginName = pluginManager.importPlugin(tempFile);
                tempFile.delete();

                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(requireContext(),
                        getString(R.string.plugins_imported) + ": " + pluginName,
                        Toast.LENGTH_SHORT).show();
                    refreshPluginList();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(requireContext(),
                        getString(R.string.plugins_invalid) + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ==================== Render Plugin List ====================

    private void refreshPluginList() {
        pluginsContainer.removeAllViews();
        List<PluginInfo> plugins = pluginManager.listPlugins();

        if (plugins.isEmpty()) {
            tvNoPlugins.setVisibility(View.VISIBLE);
            return;
        }
        tvNoPlugins.setVisibility(View.GONE);

        // Section header
        TextView header = new TextView(requireContext());
        header.setText(R.string.plugins_installed);
        header.setTextSize(14);
        header.setTextColor(0xFF909090);
        header.setPadding(0, 0, 0, 16);
        pluginsContainer.addView(header);

        for (PluginInfo plugin : plugins) {
            if (plugin.hasIndexXml && plugin.actions != null && !plugin.actions.isEmpty()) {
                // Render each action from index.xml as a separate card
                String lastGroup = null;
                for (PluginAction action : plugin.actions) {
                    // Group title header
                    if (action.groupTitle != null && !action.groupTitle.equals(lastGroup)) {
                        lastGroup = action.groupTitle;
                        addGroupHeader(plugin.name + " — " + action.groupTitle);
                    }
                    addActionCard(plugin, action);
                }
            } else if (plugin.entryScript != null) {
                // Simple plugin with just a script
                addSimplePluginCard(plugin);
            } else {
                // Plugin with no executable content — show info only
                addInfoCard(plugin);
            }
        }
    }

    private void addGroupHeader(String title) {
        TextView tv = new TextView(requireContext());
        tv.setText(title);
        tv.setTextSize(13);
        tv.setTextColor(0xFFD50000);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(8, 24, 0, 12);
        pluginsContainer.addView(tv);
    }

    /**
     * Render a parsed index.xml <action> as a native card with param inputs.
     */
    private void addActionCard(PluginInfo plugin, PluginAction action) {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        // Title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(action.title);
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 4);
        inner.addView(tvTitle);

        // Description
        if (action.description != null) {
            TextView tvDesc = new TextView(requireContext());
            tvDesc.setText(action.description);
            tvDesc.setTextSize(12);
            tvDesc.setTextColor(0xFFB0B0B0);
            tvDesc.setPadding(0, 0, 0, 16);
            inner.addView(tvDesc);
        }

        // Param inputs — collect references for reading values on Run
        Map<String, View> paramViews = new LinkedHashMap<>();

        if (action.params != null) {
            for (PluginParam param : action.params) {
                View paramView = createParamView(param);
                if (paramView != null) {
                    paramViews.put(param.name, paramView);
                    inner.addView(paramView);
                }
            }
        }

        // Run button
        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.plugins_run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 110);
        btnParams.topMargin = 16;
        btn.setLayoutParams(btnParams);

        btn.setOnClickListener(v -> {
            // Collect param values
            Map<String, String> values = collectParamValues(action.params, paramViews);
            // Check required
            if (action.params != null) {
                for (PluginParam p : action.params) {
                    if (p.required && values.containsKey(p.name)) {
                        String val = values.get(p.name);
                        if (val == null || val.trim().isEmpty()) {
                            Toast.makeText(requireContext(),
                                (p.title != null ? p.title : p.name) + " is required",
                                Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
            }
            runAction(plugin, action, values);
        });

        inner.addView(btn);
        card.addView(inner);
        pluginsContainer.addView(card);
    }

    /**
     * Create a param input view based on kr-script param type.
     */
    private View createParamView(PluginParam param) {
        String type = param.type != null ? param.type.toLowerCase() : "text";

        switch (type) {
            case "checkbox":
            case "switch": {
                CheckBox cb = new CheckBox(requireContext());
                cb.setText(param.label != null ? param.label : param.name);
                cb.setTextColor(0xFFB0B0B0);
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFD50000));
                cb.setTag(param.name);
                if ("true".equals(param.value) || "1".equals(param.value)) {
                    cb.setChecked(true);
                }
                return cb;
            }
            case "seekbar": {
                LinearLayout layout = new LinearLayout(requireContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(0, 8, 0, 8);

                TextView label = new TextView(requireContext());
                String labelText = param.label != null ? param.label : param.name;
                label.setText(labelText);
                label.setTextColor(0xFFB0B0B0);
                label.setTextSize(13);
                layout.addView(label);

                if (param.desc != null) {
                    TextView desc = new TextView(requireContext());
                    desc.setText(param.desc);
                    desc.setTextColor(0xFF666666);
                    desc.setTextSize(11);
                    layout.addView(desc);
                }

                SeekBar seekBar = new SeekBar(requireContext());
                seekBar.setMax(param.max - param.min);
                int defaultVal = 0;
                if (param.value != null) {
                    try { defaultVal = Integer.parseInt(param.value) - param.min; } catch (Exception e) {}
                }
                seekBar.setProgress(defaultVal);
                seekBar.setTag(param.name);
                seekBar.setTag(R.id.progressBar, param.min); // store min in tag

                TextView valText = new TextView(requireContext());
                valText.setText(String.valueOf(defaultVal + param.min));
                valText.setTextColor(0xFFFFFFFF);
                valText.setTextSize(12);

                final int minVal = param.min;
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                        valText.setText(String.valueOf(progress + minVal));
                    }
                    public void onStartTrackingTouch(SeekBar s) {}
                    public void onStopTrackingTouch(SeekBar s) {}
                });

                layout.addView(seekBar);
                layout.addView(valText);
                layout.setTag(param.name);
                return layout;
            }
            default: {
                // Text input (default, also for "file", "float")
                LinearLayout layout = new LinearLayout(requireContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(0, 8, 0, 8);

                if (param.title != null) {
                    TextView title = new TextView(requireContext());
                    title.setText(param.title);
                    title.setTextColor(0xFFB0B0B0);
                    title.setTextSize(13);
                    layout.addView(title);
                }
                if (param.desc != null) {
                    TextView desc = new TextView(requireContext());
                    desc.setText(param.desc);
                    desc.setTextColor(0xFF666666);
                    desc.setTextSize(11);
                    desc.setPadding(0, 0, 0, 4);
                    layout.addView(desc);
                }

                EditText et = new EditText(requireContext());
                et.setHint(param.placeholder != null ? param.placeholder :
                          (param.label != null ? param.label : param.name));
                et.setTextColor(0xFFFFFFFF);
                et.setHintTextColor(0xFF555555);
                et.setBackgroundColor(0xFF2A2A2A);
                et.setPadding(24, 16, 24, 16);
                et.setTextSize(13);
                et.setTag(param.name);

                if (param.value != null) et.setText(param.value);
                if ("float".equals(type)) {
                    et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                }

                layout.addView(et);
                layout.setTag(param.name);
                return layout;
            }
        }
    }

    /**
     * Collect values from param views.
     */
    private Map<String, String> collectParamValues(List<PluginParam> params, Map<String, View> paramViews) {
        Map<String, String> values = new HashMap<>();
        if (params == null) return values;

        for (PluginParam param : params) {
            View view = paramViews.get(param.name);
            if (view == null) continue;

            String type = param.type != null ? param.type.toLowerCase() : "text";

            switch (type) {
                case "checkbox":
                case "switch": {
                    CheckBox cb = (CheckBox) view;
                    values.put(param.name, cb.isChecked() ? "--" + param.name : "");
                    break;
                }
                case "seekbar": {
                    LinearLayout layout = (LinearLayout) view;
                    SeekBar seekBar = findSeekBar(layout);
                    if (seekBar != null) {
                        int minVal = param.min;
                        values.put(param.name, String.valueOf(seekBar.getProgress() + minVal));
                    }
                    break;
                }
                default: {
                    EditText et = findEditText(view);
                    if (et != null) {
                        values.put(param.name, et.getText().toString());
                    }
                    break;
                }
            }
        }
        return values;
    }

    private SeekBar findSeekBar(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof SeekBar) return (SeekBar) child;
        }
        return null;
    }

    private EditText findEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                EditText et = findEditText(vg.getChildAt(i));
                if (et != null) return et;
            }
        }
        return null;
    }

    /**
     * Run an action with collected param values.
     */
    private void runAction(PluginInfo plugin, PluginAction action, Map<String, String> values) {
        AlertDialog progress = new AlertDialog.Builder(requireContext())
            .setMessage(R.string.running).setCancelable(!action.interruptible).show();

        new Thread(() -> {
            String projectPath = projectManager.getCurrentProjectPath();
            String result = pluginManager.runAction(plugin, action, projectPath, values);
            handler.post(() -> {
                progress.dismiss();
                new AlertDialog.Builder(requireContext())
                    .setTitle(action.title)
                    .setMessage(result.length() > 3000 ? result.substring(0, 3000) + "..." : result)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            });
        }).start();
    }

    /**
     * Simple card for plugins with only a .sh entry script (no index.xml)
     */
    private void addSimplePluginCard(PluginInfo plugin) {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(requireContext());
        tvName.setText(plugin.name);
        tvName.setTextSize(16);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvName);

        TextView tvScript = new TextView(requireContext());
        tvScript.setText(plugin.entryScript);
        tvScript.setTextSize(11);
        tvScript.setTextColor(0xFF666666);
        tvScript.setPadding(0, 4, 0, 0);
        inner.addView(tvScript);

        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(R.string.plugins_run);
        btn.setBackgroundColor(0xFFD50000);
        btn.setCornerRadius(16);
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 110);
        btnP.topMargin = 16;
        btn.setLayoutParams(btnP);
        btn.setOnClickListener(v -> {
            AlertDialog pg = new AlertDialog.Builder(requireContext())
                .setMessage(R.string.running).setCancelable(false).show();
            new Thread(() -> {
                String result = pluginManager.runPlugin(plugin, projectManager.getCurrentProjectPath());
                handler.post(() -> {
                    pg.dismiss();
                    new AlertDialog.Builder(requireContext())
                        .setTitle(plugin.name)
                        .setMessage(result.length() > 3000 ? result.substring(0, 3000) + "..." : result)
                        .setPositiveButton(R.string.ok, null).show();
                });
            }).start();
        });
        inner.addView(btn);
        card.addView(inner);
        pluginsContainer.addView(card);
    }

    /**
     * Info-only card for plugins with no executable content.
     */
    private void addInfoCard(PluginInfo plugin) {
        MaterialCardView card = createCard();
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(requireContext());
        tvName.setText(plugin.name);
        tvName.setTextSize(16);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvName);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(plugin.description);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(0xFFB0B0B0);
        tvDesc.setPadding(0, 4, 0, 0);
        inner.addView(tvDesc);

        if (plugin.hasIndexXml) {
            MaterialButton btnXml = new MaterialButton(requireContext());
            btnXml.setText("index.xml");
            btnXml.setBackgroundColor(0xFF333333);
            btnXml.setCornerRadius(16);
            btnXml.setTextColor(0xFFB0B0B0);
            LinearLayout.LayoutParams xmlP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 100);
            xmlP.topMargin = 12;
            btnXml.setLayoutParams(xmlP);
            btnXml.setOnClickListener(v -> {
                String content = pluginManager.readIndexXml(plugin);
                if (content != null) {
                    new AlertDialog.Builder(requireContext())
                        .setTitle(plugin.name + " / index.xml")
                        .setMessage(content.length() > 3000 ? content.substring(0, 3000) + "..." : content)
                        .setPositiveButton(R.string.ok, null).show();
                }
            });
            inner.addView(btnXml);
        }

        card.addView(inner);
        pluginsContainer.addView(card);
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(0xFF1E1E1E);
        card.setRadius(32);
        card.setCardElevation(8);
        card.setContentPadding(40, 32, 40, 32);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = 20;
        card.setLayoutParams(p);
        return card;
    }
}
