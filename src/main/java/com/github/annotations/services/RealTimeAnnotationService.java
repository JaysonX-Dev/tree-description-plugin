package com.github.annotations.services;

import com.github.annotations.model.LocalMappingFile;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于DocumentListener的实时备注服务
 * 实现无需保存的实时更新功能
 */
public class RealTimeAnnotationService {
    private static final Logger LOG = Logger.getInstance(RealTimeAnnotationService.class);
    private static final String MAPPINGS_DIR_NAME = ".td-maps";
    
    private final Project project;
    private final ProjectViewRefreshService treeRefreshService;
    
    // 内存中的实时缓存 - 这是关键！
    private final Map<String, String> liveFileAnnotations = new HashMap<>();
    private final Map<String, String> livePackageAnnotations = new HashMap<>();
    private final Map<String, String> liveFileTextColors = new HashMap<>();
    private final Map<String, String> livePackageTextColors = new HashMap<>();
    
    // 后台保存相关
    private ScheduledFuture<?> saveTask;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isSavingInProgress = false;
    
    // Document监听器映射
    private final Map<VirtualFile, DocumentListener> documentListeners = new HashMap<>();
    
    // VFS监听器相关
    private MessageBusConnection messageBusConnection;
    
    public RealTimeAnnotationService(Project project) {
        this.project = project;
        this.treeRefreshService = new ProjectViewRefreshService(project);
        
        // 检查当前已打开的JSON文件
        checkAndRegisterOpenFiles();
        
        // 设置VFS文件监听器，监听外部文件变化
        setupVfsFileWatcher();
        
        LOG.info("RealTimeAnnotationService初始化成功");
    }
    
    /**
     * 设置VFS文件监听器，监听 .td-maps 目录下 JSON 文件的外部变化
     */
    private void setupVfsFileWatcher() {
        messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile eventFile = event.getFile();
                    String fileName = eventFile != null ? eventFile.getName() : "null";
                    LOG.info("检测到VFS事件: " + event.getClass().getSimpleName() + " - " + fileName);
                    if (event instanceof VFileContentChangeEvent) {
                        VirtualFile file = event.getFile();
                        if (file != null && file.getName().toLowerCase().endsWith(".json")) {
                            // 检查是否在 .td-maps 目录下
                            String basePath = project.getBasePath();
                            if (basePath != null) {
                                String mappingsDirPath = basePath + "/" + MAPPINGS_DIR_NAME + "/";
                                if (file.getPath().startsWith(mappingsDirPath)) {
                                    LOG.info("检测到.td-maps目录下的JSON文件变化: " + file.getName());
                                    // 延迟执行，避免频繁刷新
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        try {
                                            // 重新加载极文件内容到内存缓存
                                            reloadFromFile(file);
                                            // 刷新项目视图
                                            treeRefreshService.refreshProjectView();
                                            LOG.info("检测到外部文件变化，已自动刷新备注: " + file.getName());
                                        } catch (Exception e) {
                                            LOG.error("刷新备注失败: " + e.getMessage(), e);
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * 从文件重新加载内容到内存缓存
     */
    private void reloadFromFile(VirtualFile file) {
        try {
            String content = new String(file.contentsToByteArray());
            updateLiveCacheImmediately(content);
        } catch (IOException e) {
            LOG.error("重新加载文件失败: " + e.getMessage(), e);
        }
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
     * 为指定的JSON文件注册文档监听器
     */
    public void registerDocumentListener(VirtualFile file) {
        if (documentListeners.containsKey(file)) {
            return; // 已经注册过了
        }
        
        FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                TextEditor textEditor = (TextEditor) editor;
                Document document = textEditor.getEditor().getDocument();
                
                DocumentListener listener = new JsonDocumentListener();
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
    public void unregisterDocumentListener(VirtualFile file) {
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
     * 检查是否是.td-maps目录下的JSON文件
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
     * Document监听器 - 用户每输入一个字符都会触发
     */
    public class JsonDocumentListener implements DocumentListener {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            // 用户正在编辑时立即触发，无需等待保存！
            Document document = event.getDocument();
            
            if (isOurJsonFile(document)) {
                // 立即从编辑器获取最新内容
                String currentContent = document.getText();
                
                // 立即更新内存缓存
                updateLiveCacheImmediately(currentContent);
                
                // 立即刷新项目树UI
                refreshProjectTreeImmediately();
                
                // 后台异步保存（用户感知不到）
                scheduleBackgroundSave(document);
            }
        }
    }
    
    /**
     * 立即更新内存缓存 - 核心方法
     */
    private void updateLiveCacheImmediately(String jsonContent) {
        try {
            // 解析JSON内容
            LocalMappingFile mapping = parseJsonContent(jsonContent);
            
            if (mapping != null && mapping.getMappings() != null) {
                LocalMappingFile.Mappings mappings = mapping.getMappings();
                
                // 立即更新内存缓存
                liveFileAnnotations.clear();
                livePackageAnnotations.clear();
                liveFileTextColors.clear();
                livePackageTextColors.clear();
                
                if (mappings.getFiles() != null) {
                    liveFileAnnotations.putAll(mappings.getFiles());
                }
                if (mappings.getPackages() != null) {
                    livePackageAnnotations.putAll(mappings.getPackages());
                }
                if (mappings.getFilesTextColor() != null) {
                    liveFileTextColors.putAll(mappings.getFilesTextColor());
                }
                if (mappings.getPackagesTextColor() != null) {
                    livePackageTextColors.putAll(mappings.getPackagesTextColor());
                }
                
                LOG.info("内存缓存已更新，包含 " + liveFileAnnotations.size() + " 条文件备注，"
                        + livePackageAnnotations.size() + " 条包备注");
            }
        } catch (Exception e) {
            // JSON格式错误时保持原有缓存，不影响显示
            LOG.warn("JSON格式有误，保持当前显示: " + e.getMessage());
        }
    }
    
    /**
     * 立即刷新项目树UI
     */
    private void refreshProjectTreeImmediately() {
        // 在EDT线程中立即执行UI更新
        ApplicationManager.getApplication().invokeLater(() -> {
            treeRefreshService.refreshProjectView();
            LOG.info("项目树UI已刷新");
        });
    }
    
    /**
     * 获取文件备注 - 项目树调用此方法显示备注
     */
    public String getFileAnnotation(String filePath) {
        // 直接从内存缓存返回，超快速度！
        String annotation = liveFileAnnotations.get(normalizeFilePath(filePath));
        
        if (annotation != null) {

        }
        
        return annotation;
    }
    
    /**
     * 获取包备注 - 项目树调用此方法显示备注
     */
    public String getPackageAnnotation(String packagePath) {
        // 直接从内存缓存返回，超快速度！
        String annotation = livePackageAnnotations.get(normalizeFilePath(packagePath));
        
        if (annotation != null) {

        }
        
        return annotation;
    }
    
    /**
     * 获取文件文本颜色
     */
    public String getFileTextColor(String filePath) {
        return liveFileTextColors.get(normalizeFilePath(filePath));
    }
    
    /**
     * 获取包文本颜色
     */
    public String getPackageTextColor(String packagePath) {
        return livePackageTextColors.get(normalizeFilePath(packagePath));
    }
    
    /**
     * 后台异步保存 - 用户感知不到
     */
    private void scheduleBackgroundSave(Document document) {
        // 取消之前的保存任务（防抖机制）
        if (saveTask != null) {
            saveTask.cancel(false);
        }
        
        // 延迟保存到磁盘，避免频繁IO和冲突
        saveTask = scheduler.schedule(() -> {
            saveToFileInBackground(document);
        }, 50, TimeUnit.MILLISECONDS); // 从200ms改为50ms，实现更实时响应
    }
    
    /**
     * 后台保存到文件 - 使用安全的API
     */
    private void saveToFileInBackground(Document document) {
        // 检查是否正在保存，避免重复保存
        if (isSavingInProgress) {

            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    isSavingInProgress = true;
                    
                    // 使用Document API保存，避免VFS冲突
                    FileDocumentManager.getInstance().saveDocument(document);
                    LOG.info("文件已在后台保存");
                } catch (Exception e) {
                    LOG.warn("后台保存失败，但不影响显示: " + e.getMessage());
                } finally {
                    isSavingInProgress = false;
                }
            });
        });
    }
    
    /**
     * 解析JSON内容
     */
    private LocalMappingFile parseJsonContent(String jsonContent) {
        try {
            Gson gson = new Gson();
            return gson.fromJson(jsonContent, LocalMappingFile.class);
        } catch (JsonSyntaxException e) {
            LOG.warn("JSON解析失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查是否是我们的JSON文件
     */
    private boolean isOurJsonFile(Document document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        return file != null && isOurJsonFile(file);
    }
    
    /**
     * 标准化文件路径
     */
    private String normalizeFilePath(String path) {
        return path.replace('\\', '/');
    }
    
    /**
     * 释放资源
     */
    public void dispose() {
        // 移除VFS监听器
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        
        // 移除所有文档监听器
        for (Map.Entry<VirtualFile, DocumentListener> entry : documentListeners.entrySet()) {
            VirtualFile file = entry.getKey();
            DocumentListener listener = entry.getValue();
            unregisterDocumentListener(file);
        }
        documentListeners.clear();
        
        // 取消保存任务
        if (saveTask != null) {
            saveTask.cancel(false);
        }
        
        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdown();
        }
        
        LOG.info("RealTimeAnnotationService已释放资源");
    }
}
