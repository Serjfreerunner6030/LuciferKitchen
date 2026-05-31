package com.lucifer.kitchen.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;

public class ProjectManager {

    private static final String PREFS = "lucifer_prefs";
    private static final String KEY_PROJECT = "current_project";
    private static final String BASE_DIR = "/sdcard/LuciferKitchen";

    private final SharedPreferences prefs;

    public ProjectManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        new File(BASE_DIR).mkdirs();
    }

    public String getBaseDir() {
        return BASE_DIR;
    }

    public String getCurrentProject() {
        return prefs.getString(KEY_PROJECT, null);
    }

    public String getCurrentProjectPath() {
        String name = getCurrentProject();
        if (name == null) return null;
        return BASE_DIR + "/" + name;
    }

    public void setCurrentProject(String name) {
        prefs.edit().putString(KEY_PROJECT, name).apply();
    }

    public boolean createProject(String name) {
        File dir = new File(BASE_DIR, name);
        if (dir.exists()) return false;
        return dir.mkdirs();
    }

    public boolean deleteProject(String name) {
        File dir = new File(BASE_DIR, name);
        if (!dir.exists()) return false;
        deleteRecursive(dir);
        String current = getCurrentProject();
        if (name.equals(current)) {
            prefs.edit().remove(KEY_PROJECT).apply();
        }
        return true;
    }

    public String[] listProjects() {
        File base = new File(BASE_DIR);
        String[] list = base.list((dir, name) -> new File(dir, name).isDirectory());
        return list != null ? list : new String[0];
    }

    public String[] listFiles(String extension) {
        String path = getCurrentProjectPath();
        if (path == null) return new String[0];
        File dir = new File(path);
        if (!dir.exists()) return new String[0];
        String[] list = dir.list((d, name) -> name.endsWith("." + extension));
        return list != null ? list : new String[0];
    }

    public String[] listDirectories() {
        String path = getCurrentProjectPath();
        if (path == null) return new String[0];
        File dir = new File(path);
        if (!dir.exists()) return new String[0];
        String[] list = dir.list((d, name) -> new File(d, name).isDirectory());
        return list != null ? list : new String[0];
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
