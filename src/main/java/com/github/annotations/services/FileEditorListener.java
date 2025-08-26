package com.github.annotations.services;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

/**
 * 文件编辑器监听器
 * 监听文件的打开和关闭事件，自动注册/移除Document监听器
 */
public class FileEditorListener {
    
    private static final String MAPPINGS_DIR_NAME = "mappings";
    
    private final Project project;
    private final RealTimeAnnotationService realTimeAnnotationService;
    private final MessageBusConnection messageBusConnection;
    
    public FileEditorListener(Project project, RealTimeAnnotationService realTimeAnnotationService) {
        this.project = project;
        this.realTimeAnnotationService = realTimeAnnotationService;
        this.messageBusConnection = project.getMessageBus().connect();
        
        // 注册文件编辑器监听器
        registerFileEditorListener();
    }
    
    /**
     * 注册文件编辑器监听器
     */
    private void registerFileEditorListener() {
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                // 当文件打开时，检查是否是我们的JSON文件
                if (isOurJsonFile(file)) {
                    realTimeAnnotationService.registerDocumentListener(file);
                }
            }
            
            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                // 当文件关闭时，移除监听器
                if (isOurJsonFile(file)) {
                    realTimeAnnotationService.unregisterDocumentListener(file);
                }
            }
        });
    }
    
    /**
     * 检查是否是mappings目录下的JSON文件
     */
    private boolean isOurJsonFile(VirtualFile file) {
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
     * 释放资源
     */
    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
    }
}
