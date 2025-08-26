package com.github.annotations.utils;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 语言管理工具类
 * 负责管理插件的语言设置
 */
public class LanguageManager {
    
    private static final Logger LOG = Logger.getInstance(LanguageManager.class);
    
    // 支持的语言
    public enum Language {
        ENGLISH("en", "English", Locale.ENGLISH),
        CHINESE("zh", "中文", Locale.CHINESE);
        
        private final String code;
        private final String displayName;
        private final Locale locale;
        
        Language(String code, String displayName, Locale locale) {
            this.code = code;
            this.displayName = displayName;
            this.locale = locale;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public Locale getLocale() {
            return locale;
        }
        
        public static Language fromCode(String code) {
            for (Language lang : values()) {
                if (lang.code.equals(code)) {
                    return lang;
                }
            }
            return ENGLISH; // 默认英文
        }
    }
    
    private static Language currentLanguage = Language.ENGLISH; // 默认英文
    
    /**
     * 获取当前语言
     */
    @NotNull
    public static Language getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * 设置当前语言
     */
    public static void setCurrentLanguage(@NotNull Language language) {
        currentLanguage = language;
        LOG.info("Language changed to: " + language.getDisplayName());
    }
    
    /**
     * 获取当前语言代码
     */
    @NotNull
    public static String getCurrentLanguageCode() {
        return currentLanguage.getCode();
    }
    
    /**
     * 检查是否为中文
     */
    public static boolean isChinese() {
        return currentLanguage == Language.CHINESE;
    }
    
    /**
     * 检查是否为英文
     */
    public static boolean isEnglish() {
        return currentLanguage == Language.ENGLISH;
    }
    
    /**
     * 根据当前语言获取文本
     * 这是一个简单的实现，后续可以扩展为完整的国际化系统
     */
    @NotNull
    public static String getText(String chineseText, String englishText) {
        return isChinese() ? chineseText : englishText;
    }
    
    /**
     * 根据当前语言获取文本（重载版本，只传入中文文本，英文自动生成）
     */
    @NotNull
    public static String getText(String chineseText) {
        // 简单的中英文映射，后续可以完善
        if (isChinese()) {
            return chineseText;
        } else {
            // 这里可以添加简单的中英文转换逻辑
    
            return chineseText;
        }
    }
}
