package com.github.annotations.services;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 项目树刷新服务
 * 负责强制刷新项目视图和节点装饰
 */
public class ProjectViewRefreshService {
    
    private final Project project;
    
    public ProjectViewRefreshService(Project project) {
        this.project = project;
    }
    
    /**
     * 强制刷新项目视图
     */
    public void refreshProjectView() {
        try {
            ProjectView projectView = ProjectView.getInstance(project);
            if (projectView != null) {
                // 标记需要刷新
                projectView.refresh();
                
                // 强制立即刷新
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        // 使用正确的API刷新项目视图
                        projectView.refresh();
                    } catch (Exception e) {
                        com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                            .warn("强制刷新项目树失败: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                .warn("刷新项目视图失败: " + e.getMessage());
        }
    }
    
    /**
     * 强制重新装饰所有节点
     */
    public void forceNodeRedecoration() {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    ProjectView projectView = ProjectView.getInstance(project);
                    if (projectView != null) {
                        // 强制刷新项目视图以重新装饰节点
                        projectView.refresh();
                    }
                } catch (Exception e) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                        .warn("强制重新装饰节点失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                .warn("强制重新装饰节点失败: " + e.getMessage());
        }
    }
}
