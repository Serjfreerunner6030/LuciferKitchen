package com.lucifer.kitchen.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShellExecutor {

    public static String execute(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            reader.close();
            errReader.close();
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage()).append("\n");
        }
        return output.toString();
    }

    public static String executeAsRoot(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            reader.close();
            errReader.close();
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage()).append("\n");
        }
        return output.toString();
    }

    public static boolean hasRoot() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }
}
