package com.github.annotations.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * JSON文件监听器
 * 监听IDEA内部对mappings目录下所有JSON文件的修改，实时更新备注显示
 */
public class JsonFileWatcher implements BulkFileListener {
    
    private static final String MAPPINGS_DIR_NAME = "mappings";
    
    private final Project project;
    private final AnnotationService annotationService;
    private final ProjectViewRefreshService refreshService;
    private final MessageBusConnection messageBusConnection;
    
    public JsonFileWatcher(Project project, AnnotationService annotationService) {
        this.project = project;
        this.annotationService = annotationService; // 直接使用传入的引用，避免循环依赖
        this.refreshService = new ProjectViewRefreshService(project);
        
        // 注册到消息总线
        this.messageBusConnection = project.getMessageBus().connect();
        this.messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
        

        
        // 记录初始化成功
        com.intellij.openapi.diagnostic.Logger.getInstance(JsonFileWatcher.class)
            .info("JsonFileWatcher初始化成功，已注册到VFS系统");
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        

    }
    
    /**
     * 检查是否是mappings目录下的JSON文件
     */
    private boolean isOurJsonFile(@NotNull VirtualFile file) {
        if (file == null || !file.exists() || !file.getName().endsWith(".json")) {
            return false;
        }
        
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
            return false;
        }
        
        String mappingsPath = projectBasePath + "/" + MAPPINGS_DIR_NAME;
        return file.getPath().startsWith(mappingsPath);
    }
    
    /**
     * 处理文件变化事件（包括创建、修改、删除）
     */
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (isOurJsonFile(file)) {
                // 记录文件变化类型
                com.intellij.openapi.diagnostic.Logger.getInstance(JsonFileWatcher.class)
                    .info("BulkFileListener检测到文件变化: " + event.getClass().getSimpleName() + " - " + file.getPath());
                
                // 延迟处理，避免频繁刷新
                com.intellij.openapi.application.ApplicationManager.getApplication()
                    .invokeLater(() -> handleJsonFileChanged(file));
            }
        }
    }
    
    /**
     * 处理JSON文件变化
     */
    private void handleJsonFileChanged(@NotNull VirtualFile file) {
        try {
            // 重新加载备注数据
            annotationService.reloadAnnotations();
            
            // 刷新项目视图
            refreshService.refreshProjectView();
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(JsonFileWatcher.class)
                .warn("处理JSON文件变化失败: " + e.getMessage());
        }
    }
    

}
