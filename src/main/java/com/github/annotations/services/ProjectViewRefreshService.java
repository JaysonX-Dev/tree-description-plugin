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
                // 使用正确的API强制刷新整个项目视图
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        projectView.refresh();
                        // 避免使用 getCurrentProjectViewPane() 因为它在某些IDE版本中可能不可用
                        projectView.refresh(); // 再次刷新确保更新
                        
                        com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                            .info("项目视图已强制刷新");
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
