package com.zygisk_enc.RecorderX;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocaleManager {
    public static final String PREF_SELECTED_LANG = "selected_language";

    public static class LanguageItem {
        public final String code;
        public final String englishName;
        public final String nativeName;

        public LanguageItem(String code, String englishName, String nativeName) {
            this.code = code;
            this.englishName = englishName;
            this.nativeName = nativeName;
        }

        public String getDisplayName() {
            if ("sys".equals(code)) return englishName;
            return englishName + " (" + nativeName + ")";
        }
    }

    public static List<LanguageItem> getSupportedLanguages() {
        List<LanguageItem> list = new ArrayList<>();
        list.add(new LanguageItem("sys", "System Default", "Default"));
        list.add(new LanguageItem("en", "English", "English"));
        list.add(new LanguageItem("es", "Spanish", "Español"));
        list.add(new LanguageItem("hi", "Hindi", "हिंदी"));
        list.add(new LanguageItem("zh-rCN", "Chinese (Simplified)", "简体中文"));
        list.add(new LanguageItem("zh-rTW", "Chinese (Traditional)", "繁體中文"));
        list.add(new LanguageItem("ar", "Arabic", "العربية"));
        list.add(new LanguageItem("pt-rBR", "Portuguese (Brazil)", "Português"));
        list.add(new LanguageItem("ru", "Russian", "Русский"));
        list.add(new LanguageItem("ja", "Japanese", "日本語"));
        list.add(new LanguageItem("ko", "Korean", "한국어"));
        list.add(new LanguageItem("fr", "French", "Français"));
        list.add(new LanguageItem("de", "German", "Deutsch"));
        list.add(new LanguageItem("id", "Indonesian", "Bahasa Indonesia"));
        list.add(new LanguageItem("tr", "Turkish", "Türkçe"));
        list.add(new LanguageItem("vi", "Vietnamese", "Tiếng Việt"));
        list.add(new LanguageItem("it", "Italian", "Italiano"));
        list.add(new LanguageItem("pl", "Polish", "Polski"));
        list.add(new LanguageItem("uk", "Ukrainian", "Українська"));
        list.add(new LanguageItem("th", "Thai", "ไทย"));
        list.add(new LanguageItem("bn", "Bengali", "বাংলা"));
        list.add(new LanguageItem("ta", "Tamil", "தமிழ்"));
        list.add(new LanguageItem("te", "Telugu", "తెలుగు"));
        list.add(new LanguageItem("mr", "Marathi", "मराठी"));
        list.add(new LanguageItem("gu", "Gujarati", "ગુજરાતી"));
        list.add(new LanguageItem("kn", "Kannada", "ಕನ್ನಡ"));
        list.add(new LanguageItem("ml", "Malayalam", "മലയാളം"));
        list.add(new LanguageItem("pa", "Punjabi", "ਪੰਜਾਬੀ"));
        list.add(new LanguageItem("ur", "Urdu", "اردو"));
        list.add(new LanguageItem("ms", "Malay", "Bahasa Melayu"));
        list.add(new LanguageItem("tl", "Filipino / Tagalog", "Tagalog"));
        list.add(new LanguageItem("fa", "Persian", "فارسی"));
        list.add(new LanguageItem("he", "Hebrew", "עברית"));
        list.add(new LanguageItem("sw", "Swahili", "Kiswahili"));
        list.add(new LanguageItem("nl", "Dutch", "Nederlands"));
        list.add(new LanguageItem("ro", "Romanian", "Română"));
        list.add(new LanguageItem("hu", "Hungarian", "Magyar"));
        list.add(new LanguageItem("cs", "Czech", "Čeština"));
        list.add(new LanguageItem("el", "Greek", "Ελληνικά"));
        list.add(new LanguageItem("sv", "Swedish", "Svenska"));
        list.add(new LanguageItem("da", "Danish", "Dansk"));
        list.add(new LanguageItem("fi", "Finnish", "Suomi"));
        list.add(new LanguageItem("nb", "Norwegian", "Norsk"));
        list.add(new LanguageItem("sr", "Serbian", "Српски"));
        list.add(new LanguageItem("hr", "Croatian", "Hrvatski"));
        list.add(new LanguageItem("bg", "Bulgarian", "Български"));
        list.add(new LanguageItem("sk", "Slovak", "Slovenčina"));
        list.add(new LanguageItem("sl", "Slovenian", "Slovenščina"));
        list.add(new LanguageItem("lt", "Lithuanian", "Lietuvių"));
        list.add(new LanguageItem("lv", "Latvian", "Latviešu"));
        list.add(new LanguageItem("et", "Estonian", "Eesti"));
        list.add(new LanguageItem("ca", "Catalan", "Català"));
        list.add(new LanguageItem("gl", "Galician", "Galego"));
        list.add(new LanguageItem("eu", "Basque", "Euskara"));
        list.add(new LanguageItem("sq", "Albanian", "Shqip"));
        list.add(new LanguageItem("kk", "Kazakh", "Қазақ тілі"));
        list.add(new LanguageItem("uz", "Uzbek", "Oʻzbekcha"));
        list.add(new LanguageItem("az", "Azerbaijani", "Azərbaycanca"));
        list.add(new LanguageItem("ka", "Georgian", "ქართული"));
        list.add(new LanguageItem("hy", "Armenian", "Հայերեն"));
        list.add(new LanguageItem("my", "Burmese", "မြန်မာဘာသာ"));
        list.add(new LanguageItem("km", "Khmer", "ភាសាខ្មែរ"));
        list.add(new LanguageItem("lo", "Lao", "ພາສາລາວ"));
        list.add(new LanguageItem("si", "Sinhala", "සිංහල"));
        list.add(new LanguageItem("ne", "Nepali", "नेपाली"));
        list.add(new LanguageItem("mn", "Mongolian", "Монгол"));
        list.add(new LanguageItem("bo", "Tibetan", "བོད་སྐད"));
        list.add(new LanguageItem("jv", "Javanese", "Basa Jawa"));
        list.add(new LanguageItem("su", "Sundanese", "Basa Sunda"));
        list.add(new LanguageItem("ku", "Kurdish", "Kurdî"));
        list.add(new LanguageItem("tk", "Turkmen", "Türkmençe"));
        list.add(new LanguageItem("ky", "Kyrgyz", "Кыргызча"));
        list.add(new LanguageItem("tg", "Tajik", "Тоҷикӣ"));
        list.add(new LanguageItem("af", "Afrikaans", "Afrikaans"));
        list.add(new LanguageItem("am", "Amharic", "አማርኛ"));
        list.add(new LanguageItem("ha", "Hausa", "Hausa"));
        list.add(new LanguageItem("yo", "Yoruba", "Édè Yorùbá"));
        list.add(new LanguageItem("ig", "Igbo", "Asụsụ Igbo"));
        list.add(new LanguageItem("zu", "Zulu", "isiZulu"));
        list.add(new LanguageItem("so", "Somali", "Soomaali"));
        return list;
    }

    public static Context updateResources(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE);
        String langCode = prefs.getString(PREF_SELECTED_LANG, "sys");
        if ("sys".equals(langCode)) {
            return context;
        }
        Locale locale = getLocaleFromCode(langCode);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            config.setLocales(new LocaleList(locale));
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }

    public static Locale getLocaleFromCode(String code) {
        if (code.contains("-r")) {
            String[] parts = code.split("-r");
            return new Locale(parts[0], parts[1]);
        }
        return new Locale(code);
    }
}
