package com.lucifer.kitchen.utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;
import android.util.Xml;
import java.io.*;
import java.util.*;

/**
 * Scans unpacked ROM partitions for translatable Android string resources
 * and creates translated values-ru directories.
 */
public class RomTranslator {

    public interface TranslationCallback {
        void onProgress(int current, int total, String fileName);
        void onLog(String message);
        void onComplete(int translated);
    }

    // Basic translation dictionary for common Android/MIUI/HyperOS strings
    private static final Map<String, String> DICT = new HashMap<>();
    static {
        // System
        DICT.put("Settings", "Настройки");
        DICT.put("OK", "ОК");
        DICT.put("Cancel", "Отмена");
        DICT.put("Yes", "Да");
        DICT.put("No", "Нет");
        DICT.put("Done", "Готово");
        DICT.put("Back", "Назад");
        DICT.put("Next", "Далее");
        DICT.put("Save", "Сохранить");
        DICT.put("Delete", "Удалить");
        DICT.put("Edit", "Редактировать");
        DICT.put("Share", "Поделиться");
        DICT.put("Copy", "Копировать");
        DICT.put("Paste", "Вставить");
        DICT.put("Cut", "Вырезать");
        DICT.put("Search", "Поиск");
        DICT.put("Close", "Закрыть");
        DICT.put("Open", "Открыть");
        DICT.put("Send", "Отправить");
        DICT.put("Retry", "Повторить");
        DICT.put("Error", "Ошибка");
        DICT.put("Warning", "Предупреждение");
        DICT.put("Info", "Информация");
        DICT.put("Loading", "Загрузка");
        DICT.put("Please wait", "Пожалуйста, подождите");
        DICT.put("Downloading", "Скачивание");
        DICT.put("Installing", "Установка");
        DICT.put("Uninstalling", "Удаление");
        DICT.put("Update", "Обновление");
        DICT.put("Restart", "Перезагрузка");
        DICT.put("Reboot", "Перезагрузка");
        DICT.put("Shut down", "Выключить");
        DICT.put("Power off", "Выключить");
        DICT.put("Turn on", "Включить");
        DICT.put("Turn off", "Выключить");
        DICT.put("Enable", "Включить");
        DICT.put("Disable", "Отключить");
        DICT.put("Enabled", "Включено");
        DICT.put("Disabled", "Отключено");
        DICT.put("On", "Вкл");
        DICT.put("Off", "Выкл");
        // Settings
        DICT.put("Wi-Fi", "Wi-Fi");
        DICT.put("Bluetooth", "Bluetooth");
        DICT.put("Display", "Экран");
        DICT.put("Sound", "Звук");
        DICT.put("Battery", "Батарея");
        DICT.put("Storage", "Хранилище");
        DICT.put("Security", "Безопасность");
        DICT.put("Privacy", "Конфиденциальность");
        DICT.put("Location", "Местоположение");
        DICT.put("Accounts", "Аккаунты");
        DICT.put("System", "Система");
        DICT.put("About phone", "О телефоне");
        DICT.put("Notifications", "Уведомления");
        DICT.put("Apps", "Приложения");
        DICT.put("Developer options", "Для разработчиков");
        DICT.put("Accessibility", "Специальные возможности");
        DICT.put("Date & time", "Дата и время");
        DICT.put("Language", "Язык");
        DICT.put("Wallpaper", "Обои");
        DICT.put("Home screen", "Рабочий стол");
        DICT.put("Lock screen", "Экран блокировки");
        DICT.put("Network", "Сеть");
        DICT.put("Airplane mode", "Режим полёта");
        DICT.put("Mobile data", "Мобильные данные");
        DICT.put("Hotspot", "Точка доступа");
        DICT.put("SIM card", "SIM-карта");
        DICT.put("Brightness", "Яркость");
        DICT.put("Volume", "Громкость");
        DICT.put("Ringtone", "Рингтон");
        DICT.put("Vibration", "Вибрация");
        DICT.put("Do not disturb", "Не беспокоить");
        DICT.put("Camera", "Камера");
        DICT.put("Gallery", "Галерея");
        DICT.put("Calculator", "Калькулятор");
        DICT.put("Clock", "Часы");
        DICT.put("Calendar", "Календарь");
        DICT.put("Contacts", "Контакты");
        DICT.put("Messages", "Сообщения");
        DICT.put("Phone", "Телефон");
        DICT.put("File manager", "Файловый менеджер");
        DICT.put("Downloads", "Загрузки");
        DICT.put("Music", "Музыка");
        DICT.put("Video", "Видео");
        DICT.put("Photos", "Фото");
        DICT.put("Recorder", "Диктофон");
        DICT.put("Notes", "Заметки");
        DICT.put("Weather", "Погода");
        DICT.put("Compass", "Компас");
        // MIUI/HyperOS specific
        DICT.put("Themes", "Темы");
        DICT.put("Mi Account", "Mi Аккаунт");
        DICT.put("Cloud", "Облако");
        DICT.put("Cleaner", "Очистка");
        DICT.put("Scanner", "Сканер");
        DICT.put("Game Turbo", "Игровой режим");
        DICT.put("Second space", "Второе пространство");
        DICT.put("Dual apps", "Клонирование приложений");
        DICT.put("Screen recorder", "Запись экрана");
        DICT.put("Screenshot", "Скриншот");
        DICT.put("Fingerprint", "Отпечаток пальца");
        DICT.put("Face unlock", "Разблокировка лицом");
        DICT.put("Password", "Пароль");
        DICT.put("Pattern", "Графический ключ");
        DICT.put("PIN", "PIN-код");
    }

    private final String projectPath;

    public RomTranslator(String projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * Scan for translatable string resource XML files in the project
     */
    public List<File> scanTranslatableFiles() {
        List<File> files = new ArrayList<>();
        File root = new File(projectPath);
        scanDir(root, files);
        return files;
    }

    private void scanDir(File dir, List<File> files) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                // Look for values/ directories with strings.xml or arrays.xml
                if (child.getName().equals("values") || child.getName().startsWith("values-en")) {
                    File strings = new File(child, "strings.xml");
                    File arrays = new File(child, "arrays.xml");
                    if (strings.exists()) files.add(strings);
                    if (arrays.exists()) files.add(arrays);
                } else {
                    scanDir(child, files);
                }
            }
        }
    }

    /**
     * Translate a strings.xml file and write to values-ru/strings.xml alongside it
     */
    public int translateFile(File sourceFile, TranslationCallback callback) {
        int count = 0;
        try {
            // Parse source XML
            Map<String, String> entries = parseStringsXml(sourceFile);
            if (entries.isEmpty()) return 0;

            // Create values-ru directory
            File parentDir = sourceFile.getParentFile();
            String parentName = parentDir.getName();
            File ruDir;
            if (parentName.equals("values") || parentName.startsWith("values-en")) {
                ruDir = new File(parentDir.getParentFile(), "values-ru");
            } else {
                ruDir = new File(parentDir.getParentFile(), "values-ru");
            }
            ruDir.mkdirs();

            // Translate entries
            Map<String, String> translated = new HashMap<>();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String value = entry.getValue();
                String translatedValue = translateString(value);
                if (!translatedValue.equals(value)) {
                    translated.put(entry.getKey(), translatedValue);
                    count++;
                }
            }

            if (!translated.isEmpty()) {
                // Write translated XML
                File outFile = new File(ruDir, sourceFile.getName());
                writeStringsXml(outFile, translated);
                if (callback != null) {
                    callback.onLog("Translated " + count + " strings -> " + outFile.getPath());
                }
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onLog("Error: " + sourceFile.getName() + " - " + e.getMessage());
            }
        }
        return count;
    }

    private Map<String, String> parseStringsXml(File file) {
        Map<String, String> entries = new LinkedHashMap<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new FileReader(file));

            int eventType = parser.getEventType();
            String currentName = null;
            boolean inString = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("string".equals(tag)) {
                        currentName = parser.getAttributeValue(null, "name");
                        String translatable = parser.getAttributeValue(null, "translatable");
                        inString = currentName != null && !"false".equals(translatable);
                    }
                } else if (eventType == XmlPullParser.TEXT && inString && currentName != null) {
                    String text = parser.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        entries.put(currentName, text);
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("string".equals(parser.getName())) {
                        inString = false;
                        currentName = null;
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            // Skip unparseable files
        }
        return entries;
    }

    private void writeStringsXml(File file, Map<String, String> entries) throws Exception {
        XmlSerializer serializer = Xml.newSerializer();
        FileWriter writer = new FileWriter(file);
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", true);
        serializer.text("\n");
        serializer.startTag(null, "resources");
        serializer.text("\n");

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            serializer.text("    ");
            serializer.startTag(null, "string");
            serializer.attribute(null, "name", entry.getKey());
            serializer.text(entry.getValue());
            serializer.endTag(null, "string");
            serializer.text("\n");
        }

        serializer.endTag(null, "resources");
        serializer.endDocument();
        writer.close();
    }

    /**
     * Dictionary-based translation with word/phrase matching
     */
    public String translateString(String input) {
        if (input == null || input.trim().isEmpty()) return input;

        // Try exact match first
        String exact = DICT.get(input.trim());
        if (exact != null) return exact;

        // Try word-by-word replacement for longer strings
        String result = input;
        // Sort by length descending to match longer phrases first
        List<Map.Entry<String, String>> sorted = new ArrayList<>(DICT.entrySet());
        sorted.sort((a, b) -> b.getKey().length() - a.getKey().length());

        for (Map.Entry<String, String> entry : sorted) {
            String key = entry.getKey();
            if (result.contains(key)) {
                result = result.replace(key, entry.getValue());
            }
        }

        return result;
    }

    /**
     * Run full translation on all scanned files
     */
    public void translateAll(TranslationCallback callback) {
        List<File> files = scanTranslatableFiles();
        if (callback != null) {
            callback.onLog("Found " + files.size() + " translatable files");
        }
        int totalTranslated = 0;
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (callback != null) {
                callback.onProgress(i + 1, files.size(), f.getName());
            }
            totalTranslated += translateFile(f, callback);
        }
        if (callback != null) {
            callback.onComplete(totalTranslated);
        }
    }
}
