package com.github.annotations.utils;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.project.Project;

/**
 * 国际化工具类
 * 提供获取多语言文本的方法
 */
public class I18nUtils {
    
    /**
     * 获取多语言文本
     * @param project 项目实例
     * @param chinese 中文文本
     * @param english 英文文本
     * @return 根据当前语言返回对应的文本
     */
    public static String getText(Project project, String chinese, String english) {
        if (project == null) {
            return chinese; // 默认返回中文
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            return chinese; // 默认返回中文
        }
        
        String currentLanguage = service.getLanguage();
        return "en".equals(currentLanguage) ? english : chinese;
    }
    
    /**
     * 获取多语言文本（无项目上下文）
     * @param language 语言代码
     * @param chinese 中文文本
     * @param english 英文文本
     * @return 根据语言返回对应的文本
     */
    public static String getText(String language, String chinese, String english) {
        return "en".equals(language) ? english : chinese;
    }
}
