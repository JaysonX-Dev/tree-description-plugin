package com.github.annotations.services;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 项目启动后初始化活动
 * 替代StartupManager.runAfterOpened内部API的使用
 */
public class ProjectInitActivity implements StartupActivity {
    
    private static final Logger LOG = Logger.getInstance(ProjectInitActivity.class);
    
    @Override
    public void runActivity(@NotNull Project project) {
        // 项目打开后的初始化逻辑
        // 这里可以调用AnnotationService中的语言设置初始化方法
        try {
            // 获取AnnotationService实例并执行初始化
            AnnotationService annotationService = project.getService(AnnotationService.class);
            if (annotationService != null) {
                annotationService.initializeLanguageSettings();
            }
        } catch (Exception e) {
            // 记录错误日志
            LOG.warn("Failed to initialize project settings", e);
        }
    }
}