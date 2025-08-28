package com.github.annotations.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * VFS刷新服务
 * 负责强制刷新IDEA的虚拟文件系统状态
 */
public class VFSRefreshService {
    
    private final Project project;
    
    public VFSRefreshService(Project project) {
        this.project = project;
    }
    
    /**
     * 强制刷新指定的JSON文件
     */
    public void refreshJsonFile(@NotNull String jsonPath) {
        try {
            // 强制刷新VFS中的文件状态
            VirtualFile jsonFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(jsonPath);
            if (jsonFile != null) {
                jsonFile.refresh(false, false); // 强制刷新
            }
        } catch (Exception e) {
            // 记录错误但不中断流程
            com.intellij.openapi.diagnostic.Logger.getInstance(VFSRefreshService.class)
                .warn("刷新VFS失败: " + e.getMessage());
        }
    }
    
    /**
     * 刷新整个.td-maps目录
     */
    public void refreshMappingsDirectory() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                String mappingsPath = basePath + "/.td-maps";
                VirtualFile mappingsDir = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(mappingsPath);
                if (mappingsDir != null) {
                    mappingsDir.refresh(false, false);
                }
            }
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(VFSRefreshService.class)
                .warn("刷新.td-maps目录失败: " + e.getMessage());
        }
    }
}
