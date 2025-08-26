package com.github.annotations.services;

import com.github.annotations.model.LocalMappingFile;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 监听JSON编辑器的变化，实现实时备注更新
 */
public class JsonEditorListener {
    private static final Logger LOG = Logger.getInstance(JsonEditorListener.class);
    private static final String MAPPINGS_DIR_NAME = "mappings";
    
    private final Project project;
    private final AnnotationService annotationService;
    private final ProjectViewRefreshService refreshService;
    private final MessageBusConnection messageBusConnection;
    private final Map<VirtualFile, DocumentListener> documentListeners;
    private final Timer debounceTimer;
    private final Gson gson;
    
    public JsonEditorListener(Project project, AnnotationService annotationService) {
        this.project = project;
        this.annotationService = annotationService;
        this.refreshService = new ProjectViewRefreshService(project);
        this.messageBusConnection = project.getMessageBus().connect();
        this.documentListeners = new HashMap<>();
        this.gson = new Gson();
        
        // 防抖定时器，200ms后执行更新
        this.debounceTimer = new Timer(200, e -> {
            performUpdate();
        });
        this.debounceTimer.setRepeats(false);
        
        // 检查当前已打开的JSON文件
        checkAndRegisterOpenFiles();
        
        LOG.info("JsonEditorListener初始化成功");
    }
    
    /**
     * 检查并注册当前已打开的JSON文件
     */
    private void checkAndRegisterOpenFiles() {
        FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                VirtualFile file = editor.getFile();
                if (isOurJsonFile(file)) {
                    registerDocumentListener(file);
                }
            }
        }
    }
    
    /**
     * 公共方法：注册新打开的文件（供外部调用）
     */
    public void registerFileIfNeeded(VirtualFile file) {
        if (isOurJsonFile(file) && !documentListeners.containsKey(file)) {
            registerDocumentListener(file);
        }
    }
    
    /**
     * 公共方法：移除已关闭的文件（供外部调用）
     */
    public void unregisterFileIfNeeded(VirtualFile file) {
        if (isOurJsonFile(file) && documentListeners.containsKey(file)) {
            removeDocumentListener(file);
        }
    }
    
    /**
     * 为指定的JSON文件注册文档监听器
     */
    private void registerDocumentListener(VirtualFile file) {
        if (documentListeners.containsKey(file)) {
            return; // 已经注册过了
        }
        
        FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                TextEditor textEditor = (TextEditor) editor;
                Editor editorComponent = textEditor.getEditor();
                Document document = editorComponent.getDocument();
                
                DocumentListener listener = new DocumentListener() {
                    @Override
                    public void documentChanged(@NotNull DocumentEvent event) {
                        scheduleUpdate();
                    }
                };
                
                document.addDocumentListener(listener);
                documentListeners.put(file, listener);
                
                LOG.info("为文件 " + file.getName() + " 注册了文档监听器");
                break;
            }
        }
    }
    
    /**
     * 移除指定文件的文档监听器
     */
    private void removeDocumentListener(VirtualFile file) {
        DocumentListener listener = documentListeners.remove(file);
        if (listener != null) {
            FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
            for (FileEditor editor : editors) {
                if (editor instanceof TextEditor) {
                    TextEditor textEditor = (TextEditor) editor;
                    Document document = textEditor.getEditor().getDocument();
                    document.removeDocumentListener(listener);
                    break;
                }
            }
            LOG.info("为文件 " + file.getName() + " 移除了文档监听器");
        }
    }
    
    /**
     * 检查是否是mappings目录下的JSON文件
     */
    private boolean isOurJsonFile(VirtualFile file) {
        if (file == null || !file.getName().endsWith(".json")) {
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
     * 安排更新（防抖机制）
     */
    private void scheduleUpdate() {
        debounceTimer.restart();
    }
    
    /**
     * 执行实际的更新操作
     */
    private void performUpdate() {
        try {
            // 获取当前打开的JSON文件
            VirtualFile jsonFile = findOpenJsonFile();
            if (jsonFile == null) {
                return;
            }
            
            // 获取文件内容
            String jsonContent = getFileContent(jsonFile);
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                return;
            }
            
            // 解析JSON内容
            LocalMappingFile mapping = parseJsonContent(jsonContent);
            if (mapping != null) {
                // 更新内存中的备注数据
                updateAnnotationsFromMapping(mapping);
                
                // 刷新项目树显示
                refreshService.refreshProjectView();
                
                LOG.info("实时更新备注完成");
            }
        } catch (Exception e) {
            LOG.warn("实时更新备注时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 查找当前打开的JSON文件
     */
    private VirtualFile findOpenJsonFile() {
        FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                VirtualFile file = editor.getFile();
                if (isOurJsonFile(file)) {
                    return file;
                }
            }
        }
        return null;
    }
    
    /**
     * 获取文件内容
     */
    private String getFileContent(VirtualFile file) {
        try {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                return document.getText();
            }
        } catch (Exception e) {
            LOG.warn("获取文件内容失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 解析JSON内容
     */
    private LocalMappingFile parseJsonContent(String jsonContent) {
        try {
            return gson.fromJson(jsonContent, LocalMappingFile.class);
        } catch (JsonSyntaxException e) {
            // JSON格式错误时不更新，保持原有备注
            // JSON格式错误，跳过实时更新
            return null;
        }
    }
    
    /**
     * 从映射文件更新备注数据
     */
    private void updateAnnotationsFromMapping(LocalMappingFile mapping) {
        if (mapping == null || mapping.getMappings() == null) {
            return;
        }
        
        LocalMappingFile.Mappings mappings = mapping.getMappings();
        
        // 更新文件备注
        if (mappings.getFiles() != null) {
            annotationService.setAnnotations(mappings.getFiles());
        }
        
        // 更新包备注
        if (mappings.getPackages() != null) {
            annotationService.setPackageAnnotations(mappings.getPackages());
        }
        
        // 更新文件文本颜色和包文本颜色需要逐个设置
        if (mappings.getFilesTextColor() != null) {
            for (Map.Entry<String, String> entry : mappings.getFilesTextColor().entrySet()) {
                annotationService.setFileTextColor(entry.getKey(), entry.getValue());
            }
        }
        
        if (mappings.getPackagesTextColor() != null) {
            for (Map.Entry<String, String> entry : mappings.getPackagesTextColor().entrySet()) {
                annotationService.setPackageTextColor(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * 释放资源
     */
    public void dispose() {
        // 移除所有文档监听器
        for (Map.Entry<VirtualFile, DocumentListener> entry : documentListeners.entrySet()) {
            VirtualFile file = entry.getKey();
            DocumentListener listener = entry.getValue();
            removeDocumentListener(file);
        }
        documentListeners.clear();
        
        // 停止定时器
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
        
        // 断开消息总线连接
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        
        LOG.info("JsonEditorListener已释放资源");
    }
}
