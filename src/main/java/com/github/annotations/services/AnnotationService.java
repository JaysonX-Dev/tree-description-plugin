package com.github.annotations.services;

import com.github.annotations.model.LocalMappingFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

/**
 * 项目级别的备注管理服务
 * 负责存储和管理当前项目的所有中文备注
 * 基于项目根目录的 .td-maps/ 目录下的 JSON 文件
 * 支持多个映射文件，主文件为 local-description.json
 */
public class AnnotationService {
    
    private static final Logger LOG = Logger.getInstance(AnnotationService.class);
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        .create();
    private static final String MAPPINGS_DIR_NAME = ".td-maps";
    private static final String LOCAL_DESCRIPTION_FILE = "local-description.json";
    
    private final Project project;
    private Map<String, String> annotations = new LinkedHashMap<>(); // 文件备注
    private Map<String, String> packageAnnotations = new LinkedHashMap<>(); // 包备注
    private Map<String, String> fileMatchAnnotations = new LinkedHashMap<>(); // 文件匹配模式备注
    private Map<String, String> packageMatchAnnotations = new LinkedHashMap<>(); // 包匹配模式备注
    
    // 新增：备注字体颜色配置
    private Map<String, String> packagesTextColor = new LinkedHashMap<>(); // 包备注字体颜色
    private Map<String, String> filesTextColor = new LinkedHashMap<>(); // 文件备注字体颜色
    
    private boolean builtinMappingsEnabled = true; // 内置映射库开关状态
    private boolean projectTreeAnnotationsEnabled = true; // 项目树备注显示开关状态
    private String language = "en"; // 语言设置，默认为英文
    private MessageBusConnection messageBusConnection;
    
    // 新增：存储所有映射文件的内容
    private Map<String, LocalMappingFile> allMappingFiles = new HashMap<>();
    
    // 新增：JSON文件监听器
    private JsonFileWatcher jsonFileWatcher;
    
    // 新增：实时备注服务（基于DocumentListener）
    private RealTimeAnnotationService realTimeAnnotationService;
    
    // 新增：文件编辑器监听器
    private FileEditorListener fileEditorListener;
    
    public AnnotationService(Project project) {
        this.project = project;
        setupFileWatcher();
        // 启动时从 .td-maps 目录加载所有映射文件
        loadFromMappingsDirectory();
        // 延迟初始化JSON文件监听器，避免循环依赖
        // setupJsonFileWatcher(); // 移除立即初始化
        
        // 初始化实时备注服务，启用双向监听
        setupRealTimeAnnotationService();
        
        // 注册项目启动监听器（新API方式）
        registerProjectListener();
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        if (jsonFileWatcher != null) {
            jsonFileWatcher.dispose();
        }
        if (realTimeAnnotationService != null) {
            realTimeAnnotationService.dispose();
        }
        if (fileEditorListener != null) {
            fileEditorListener.dispose();
        }
    }
    
    /**
     * 初始化JSON文件监听器（延迟初始化）
     */
    private void setupJsonFileWatcher() {
        try {
            if (jsonFileWatcher == null) {
                jsonFileWatcher = new JsonFileWatcher(project, this); // 传入this引用
                LOG.info("JSON文件监听器初始化成功");
            }
        } catch (Exception e) {
            LOG.warn("初始化JSON文件监听器失败: " + e.getMessage());
        }
    }
    
    /**
     * 初始化实时备注服务
     */
    private void setupRealTimeAnnotationService() {
        try {
            if (realTimeAnnotationService == null) {
                realTimeAnnotationService = new RealTimeAnnotationService(project);
                LOG.info("实时备注服务初始化成功");
                
                // 初始化文件编辑器监听器
                setupFileEditorListener();
            }
        } catch (Exception e) {
            LOG.warn("初始化实时备注服务失败: " + e.getMessage());
        }
    }
    
    /**
     * 初始化文件编辑器监听器
     */
    private void setupFileEditorListener() {
        try {
            if (fileEditorListener == null) {
                fileEditorListener = new FileEditorListener(project, realTimeAnnotationService);
                LOG.info("文件编辑器监听器初始化成功");
            }
        } catch (Exception e) {
            LOG.warn("初始化文件编辑器监听器失败: " + e.getMessage());
        }
    }
    
    /**
     * 确保JSON文件监听器已初始化
     */
    private void ensureJsonFileWatcher() {
        if (jsonFileWatcher == null) {
            setupJsonFileWatcher();
        }
    }
    
    /**
     * 确保实时备注服务已设置
     */
    private void ensureRealTimeAnnotationService() {
        if (realTimeAnnotationService == null) {
            setupRealTimeAnnotationService();
        }
    }
    
    /**
     * 获取项目的AnnotationService实例
     */
    public static AnnotationService getInstance(Project project) {
        // 使用项目级别的服务管理
        return project.getService(AnnotationService.class);
    }
    
    /**
     * 项目启动时初始化服务
     */
    public static AnnotationService initializeService(Project project) {
        // 确保服务被创建和初始化
        return project.getService(AnnotationService.class);
    }
    
    /**
     * 添加或更新文件备注
     */
    public void setAnnotation(@NotNull String filePath, @NotNull String annotation) {
        annotations.put(filePath, annotation);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 添加或更新文件备注（带颜色）
     */
    public void setAnnotation(@NotNull String filePath, @NotNull String annotation, @Nullable String textColor) {
        annotations.put(filePath, annotation);
        
        // 设置颜色（只有非默认颜色才保存）
        if (textColor != null && !textColor.equals("#BBBBBB") && !textColor.trim().isEmpty()) {
            filesTextColor.put(filePath, textColor);
        } else {
            // 如果是默认颜色或空值，移除颜色配置
            filesTextColor.remove(filePath);
        }
        
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 添加或更新文件备注（带颜色）并立即刷新
     */
    public void setAnnotationAndRefresh(@NotNull String filePath, @NotNull String annotation, @Nullable String textColor) {
        setAnnotation(filePath, annotation, textColor);
        
        // 立即刷新VFS和项目视图
        refreshAfterSave();
    }
    
    /**
     * 添加或更新包/目录备注
     */
    public void setPackageAnnotation(@NotNull String packagePath, @NotNull String annotation) {
        packageAnnotations.put(packagePath, annotation);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 添加或更新包/目录备注（带颜色）
     */
    public void setPackageAnnotation(@NotNull String packagePath, @NotNull String annotation, @Nullable String textColor) {
        packageAnnotations.put(packagePath, annotation);
        
        // 设置颜色（只有非默认颜色才保存）
        if (textColor != null && !textColor.equals("#BBBBBB") && !textColor.trim().isEmpty()) {
            packagesTextColor.put(packagePath, textColor);
        } else {
            // 如果是默认颜色或空值，移除颜色配置
            packagesTextColor.remove(packagePath);
        }
        
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 添加或更新包/目录备注（带颜色）并立即刷新
     */
    public void setPackageAnnotationAndRefresh(@NotNull String packagePath, @NotNull String annotation, @Nullable String textColor) {
        setPackageAnnotation(packagePath, annotation, textColor);
        
        // 立即刷新VFS和项目视图
        refreshAfterSave();
    }
    
    /**
     * 保存到项目根目录的 .td-maps/local-description.json 文件
     */
    private void saveToFile() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                // 创建 .td-maps 目录
        Path mappingsDir = Paths.get(basePath, MAPPINGS_DIR_NAME);
                Files.createDirectories(mappingsDir);
                
                // 主映射文件路径
                Path localDescriptionPath = mappingsDir.resolve(LOCAL_DESCRIPTION_FILE);
                
                // 创建或更新主映射文件
                LocalMappingFile localMapping = createLocalMappingFile();
                
                // 序列化为 JSON
                String jsonContent = gson.toJson(localMapping);
                
                // 写入文件
                Files.writeString(localDescriptionPath, jsonContent);
                LOG.info("备注已保存到: " + localDescriptionPath);
            }
        } catch (IOException e) {
            LOG.error("保存备注文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取JSON格式的备注内容（用于导出）
     */
    public String getJsonContentForExport() {
        LocalMappingFile localMapping = createLocalMappingFile();
        return gson.toJson(localMapping);
    }
    
    /**
     * 创建本地映射文件对象
     */
    private LocalMappingFile createLocalMappingFile() {
        LocalMappingFile localMapping = new LocalMappingFile();
        
        // 设置元数据
        localMapping.setName("project local mappings");
        localMapping.setVersion("1.0.0");
        localMapping.setDescription("Current project path and file mapping configuration");
        localMapping.setAuthor("Local");
        localMapping.setBuiltinMappingsEnabled(builtinMappingsEnabled);
        localMapping.setLanguage(language);
        
        // 设置映射内容 - 按照期望的顺序
        LocalMappingFile.Mappings mappings = localMapping.getMappings();
        mappings.setFiles(new LinkedHashMap<>(annotations));
        mappings.setPackages(new LinkedHashMap<>(packageAnnotations));
        mappings.setFileMatch(new LinkedHashMap<>(fileMatchAnnotations));
        mappings.setPackageMatch(new LinkedHashMap<>(packageMatchAnnotations));
        mappings.setFilesTextColor(new LinkedHashMap<>(filesTextColor));
        mappings.setPackagesTextColor(new LinkedHashMap<>(packagesTextColor));
        
        return localMapping;
    }
    

    
    /**
     * 获取备注
     */
    @Nullable
    public String getAnnotation(@NotNull String filePath) {
        return annotations.get(filePath);
    }
    
    /**
     * 获取文件备注（VirtualFile版本）
     */
    @Nullable
    public String getAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        return relativePath != null ? getAnnotation(relativePath) : null;
    }
    
    /**
     * 获取包备注（VirtualFile版本）
     */
    @Nullable
    public String getPackageAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath != null) {
            // packages 映射使用原始文件系统路径（包含/），不进行标准化
            return getPackageAnnotation(relativePath);
        }
        return null;
    }
    
    /**
     * 获取包备注（字符串路径版本）
     */
    @Nullable
    public String getPackageAnnotation(@NotNull String packagePath) {
        // 直接查找，不进行路径标准化
        return packageAnnotations.get(packagePath);
    }
    
    /**
     * 根据文件名模式匹配文件匹配模式备注
     * 支持精确的匹配策略：
     * 1. 正则表达式匹配（如 ".*Controller\\.java$"）
     * 2. 完整单词匹配（如 "service" 只匹配独立的单词，不匹配 "consumer.service"）
     * 3. 完全匹配（完全相同的文件名）
     */
    @Nullable
    public String getFileMatchAnnotation(@NotNull String fileName) {
        return getFileMatchAnnotation(fileName, null);
    }
    
    /**
     * 根据文件名和路径模式匹配文件匹配模式备注（支持混合匹配）
     * @param fileName 文件名
     * @param relativePath 文件的相对路径（可为null）
     * @return 匹配的备注
     */
    @Nullable
    public String getFileMatchAnnotation(@NotNull String fileName, @Nullable String relativePath) {
        if (fileMatchAnnotations.isEmpty()) {
            return null;
        }
        
        // 优先进行完全匹配
        String exactMatch = fileMatchAnnotations.get(fileName);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // 按优先级进行模式匹配
        return fileMatchAnnotations.entrySet().stream()
            .filter(entry -> {
                String pattern = entry.getKey();
                String value = entry.getValue();
                
                // 跳过空值
                if (value == null || value.trim().isEmpty()) {
                    return false;
                }
                
                // 使用精确匹配逻辑
                return matchesFilePattern(fileName, pattern, relativePath);
            })
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 根据包名模式匹配包匹配模式备注
     * 支持精确的匹配策略：
     * 1. 正则表达式匹配（如 ".*\\.service\\..*"）
     * 2. 完整路径段匹配（如 "service" 只匹配完整的路径段，不匹配 "consumer.service"）
     * 3. 完全匹配（完全相同的包路径）
     */
    @Nullable
    public String getPackageMatchAnnotation(@NotNull String packageName) {
        if (packageMatchAnnotations.isEmpty()) {
            return null;
        }
        
        // 优先进行完全匹配
        String exactMatch = packageMatchAnnotations.get(packageName);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // 按优先级进行模式匹配
        return packageMatchAnnotations.entrySet().stream()
            .filter(entry -> {
                String pattern = entry.getKey();
                String value = entry.getValue();
                
                // 跳过空值
                if (value == null || value.trim().isEmpty()) {
                    return false;
                }
                
                // 使用精确匹配逻辑
                return matchesPackagePattern(packageName, pattern);
            })
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 删除文件备注
     */
    public void removeAnnotation(@NotNull String filePath) {
        annotations.remove(filePath);
        // 同时清理颜色配置
        filesTextColor.remove(filePath);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 删除包/目录备注
     */
    public void removePackageAnnotation(@NotNull String packagePath) {
        packageAnnotations.remove(packagePath);
        // 同时清理颜色配置
        packagesTextColor.remove(packagePath);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 删除备注（VirtualFile版本）
     */
    public void removeAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath != null) {
            removeAnnotation(relativePath);
        }
    }
    
    /**
     * 检查是否有文件备注
     */
    public boolean hasAnnotation(@NotNull String filePath) {
        return annotations.containsKey(filePath);
    }
    
    /**
     * 检查是否有包/目录备注
     */
    public boolean hasPackageAnnotation(@NotNull String packagePath) {
        return packageAnnotations.containsKey(packagePath);
    }
    
    /**
     * 检查是否有文件备注（VirtualFile版本）
     */
    public boolean hasAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        return relativePath != null && hasAnnotation(relativePath);
    }
    
    /**
     * 检查是否有包/目录备注（VirtualFile版本）
     */
    public boolean hasPackageAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        return relativePath != null && hasPackageAnnotation(relativePath);
    }
    
    /**
     * 获取所有文件备注
     */
    @NotNull
    public Map<String, String> getAllAnnotations() {
        return new HashMap<>(annotations);
    }
    
    /**
     * 获取所有包备注
     */
    @NotNull
    public Map<String, String> getAllPackageAnnotations() {
        return new HashMap<>(packageAnnotations);
    }
    
    /**
     * 获取所有文件匹配模式备注
     */
    @NotNull
    public Map<String, String> getAllFileMatchAnnotations() {
        return new HashMap<>(fileMatchAnnotations);
    }
    
    /**
     * 获取所有包匹配模式备注
     */
    @NotNull
    public Map<String, String> getAllPackageMatchAnnotations() {
        return new HashMap<>(packageMatchAnnotations);
    }
    
    /**
     * 获取包备注字体颜色
     */
    public String getPackageTextColor(@NotNull String packagePath) {
        return packagesTextColor.get(packagePath);
    }
    
    /**
     * 获取文件备注字体颜色
     */
    public String getFileTextColor(@NotNull String filePath) {
        return filesTextColor.get(filePath);
    }
    
    /**
     * 设置包备注字体颜色
     */
    public void setPackageTextColor(@NotNull String packagePath, @NotNull String color) {
        packagesTextColor.put(packagePath, color);
        saveToFile();
    }
    
    /**
     * 设置文件备注字体颜色
     */
    public void setFileTextColor(@NotNull String filePath, @NotNull String color) {
        filesTextColor.put(filePath, color);
        saveToFile();
    }
    
    /**
     * 获取所有包备注字体颜色
     */
    public Map<String, String> getAllPackageTextColors() {
        return new HashMap<>(packagesTextColor);
    }
    
    /**
     * 获取所有文件备注字体颜色
     */
    public Map<String, String> getAllFileTextColors() {
        return new HashMap<>(filesTextColor);
    }
    
    /**
     * 获取内置映射库开关状态
     */
    public boolean isBuiltinMappingsEnabled() {
        return builtinMappingsEnabled;
    }
    
    /**
     * 设置内置映射库开关状态
     */
    public void setBuiltinMappingsEnabled(boolean enabled) {
        if (this.builtinMappingsEnabled != enabled) {
            this.builtinMappingsEnabled = enabled;
            saveToFile(); // 立即保存到文件
        }
    }
    
    /**
     * 批量设置文件备注
     */
    public void setAnnotations(@NotNull Map<String, String> newAnnotations) {
        annotations.putAll(newAnnotations);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 批量设置包备注
     */
    public void setPackageAnnotations(@NotNull Map<String, String> newAnnotations) {
        packageAnnotations.putAll(newAnnotations);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 添加或更新单个文件匹配模式备注
     */
    public void setFileMatchAnnotation(@NotNull String pattern, @NotNull String annotation) {
        fileMatchAnnotations.put(pattern, annotation);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 添加或更新单个包匹配模式备注
     */
    public void setPackageMatchAnnotation(@NotNull String pattern, @NotNull String annotation) {
        packageMatchAnnotations.put(pattern, annotation);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 批量设置文件匹配模式备注
     */
    public void setFileMatchAnnotations(@NotNull Map<String, String> newAnnotations) {
        fileMatchAnnotations.putAll(newAnnotations);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 批量设置包匹配模式备注
     */
    public void setPackageMatchAnnotations(@NotNull Map<String, String> newAnnotations) {
        packageMatchAnnotations.putAll(newAnnotations);
        saveToFile(); // 立即保存到文件
    }
    
    /**
     * 清空所有备注
     */
    public void clearAllAnnotations() {
        annotations.clear();
        packageAnnotations.clear();
        fileMatchAnnotations.clear();
        packageMatchAnnotations.clear();
        packagesTextColor.clear();
        filesTextColor.clear();
        
        // 清空.td-maps目录下除local-description.json外的其他文件
        clearMappingsDirectory();
        
        saveToFile(); // 立即保存到文件
        
        // 强制刷新UI和编辑器内容
        forceRefreshUIAndEditors();
    }
    
    /**
     * 获取文件相对于项目根目录的路径
     */
    @Nullable
    private String getRelativePath(@NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            // 确保有子路径才截取，避免StringIndexOutOfBoundsException
            if (filePath.length() > projectPath.length()) {
                String relativePath = filePath.substring(projectPath.length());

                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            } else if (filePath.equals(projectPath)) {
                // 如果是项目根目录本身
                return "";
            }
        }
        
        return null;
    }
    

    
    /**
     * 将备注导出为与映射库兼容的格式
     */
    public String exportToMappingFormat() {
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("name", "用户备注");
        exportData.put("version", "1.0.0");
        exportData.put("description", "用户自定义备注");
        
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("files", annotations);
        if (!packageAnnotations.isEmpty()) {
            mappings.put("packages", packageAnnotations);
        }
        if (!fileMatchAnnotations.isEmpty()) {
            mappings.put("fileMatch", fileMatchAnnotations);
        }
        if (!packageMatchAnnotations.isEmpty()) {
            mappings.put("packageMatch", packageMatchAnnotations);
        }
        exportData.put("mappings", mappings);
        
        return gson.toJson(exportData);
    }
    
    /**
     * 从映射库格式导入备注
     */
    @SuppressWarnings("unchecked")
    public boolean importFromMappingFormat(String jsonContent) {
        try {
            LocalMappingFile mappingFile = gson.fromJson(jsonContent, LocalMappingFile.class);
            if (mappingFile != null && mappingFile.getMappings() != null) {
                LocalMappingFile.Mappings mappings = mappingFile.getMappings();
                boolean hasImported = false;
                
                // 导入文件备注
                Map<String, String> files = mappings.getFiles();
                if (files != null && !files.isEmpty()) {
                    annotations.putAll(files);
                    hasImported = true;
                }
                
                // 导入包备注
                Map<String, String> packages = mappings.getPackages();
                if (packages != null && !packages.isEmpty()) {
                    packageAnnotations.putAll(packages);
                    hasImported = true;
                }
                
                // 导入文件匹配模式备注
                Map<String, String> fileMatchPatterns = mappings.getFileMatch();
                if (fileMatchPatterns != null && !fileMatchPatterns.isEmpty()) {
                    fileMatchAnnotations.putAll(fileMatchPatterns);
                    hasImported = true;
                }
                
                // 导入包匹配模式备注
                Map<String, String> packageMatchPatterns = mappings.getPackageMatch();
                if (packageMatchPatterns != null && !packageMatchPatterns.isEmpty()) {
                    packageMatchAnnotations.putAll(packageMatchPatterns);
                    hasImported = true;
                }
                
                // 导入内置映射库开关状态
                if (mappingFile.getBuiltinMappingsEnabled() != null) {
                    this.builtinMappingsEnabled = mappingFile.getBuiltinMappingsEnabled();
                    hasImported = true;
                }
                
                if (hasImported) {
                    saveToFile(); // 立即保存到文件
                    return true;
                }
            }
        } catch (JsonSyntaxException e) {
            LOG.error("导入备注失败: " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * 从 .td-maps 目录加载所有映射文件
     */
    private void loadFromMappingsDirectory() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path mappingsDir = Paths.get(basePath, MAPPINGS_DIR_NAME);
                
                // 如果 .td-maps 目录不存在，则创建它
                if (!Files.exists(mappingsDir)) {
                    Files.createDirectories(mappingsDir);
                    LOG.info("已创建 .td-maps 目录: " + mappingsDir);
                    return;
                }
                
                // 扫描 .td-maps 目录下的所有 JSON 文件
                scanAndLoadMappingFiles(mappingsDir);
                
                // 合并所有数据
                mergeMappingData();
                
                LOG.info("已从 .td-maps 目录加载映射数据");
                
                // 加载完成后初始化JSON文件监听器
                ensureJsonFileWatcher();
                
                // 加载完成后初始化实时备注服务
                ensureRealTimeAnnotationService();
            }
        } catch (IOException e) {
            LOG.error("加载映射目录失败: " + e.getMessage(), e);
            
            // 尝试从旧的 annotations.xml 迁移
            tryMigrateFromXml();
        }
    }
    
    /**
     * 扫描并加载 .td-maps 目录下的所有 JSON 文件
     */
    private void scanAndLoadMappingFiles(Path mappingsDir) throws IOException {
        allMappingFiles.clear();
        
        try (Stream<Path> files = Files.walk(mappingsDir, 1)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                 .forEach(this::loadSingleMappingFile);
        }
    }
    
    /**
     * 加载单个映射文件
     */
    private void loadSingleMappingFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            LocalMappingFile mappingFile = gson.fromJson(content, LocalMappingFile.class);
            
            if (mappingFile != null) {
                String fileName = filePath.getFileName().toString();
                allMappingFiles.put(fileName, mappingFile);
                LOG.info("已加载映射文件: " + fileName);
            }
        } catch (IOException | JsonSyntaxException e) {
            LOG.error("加载映射文件失败: " + filePath + ", 错误: " + e.getMessage(), e);
        }
    }
    
    /**
     * 合并所有映射数据到主数据结构中
     */
    private void mergeMappingData() {
        // 清空现有数据
        annotations.clear();
        packageAnnotations.clear();
        fileMatchAnnotations.clear();
        packageMatchAnnotations.clear();
        packagesTextColor.clear();
        filesTextColor.clear();
        
        // 先加载主文件
        LocalMappingFile localDescription = allMappingFiles.get(LOCAL_DESCRIPTION_FILE);
        if (localDescription != null) {
            mergeSingleMappingFile(localDescription);
            
            // 从主文件获取全局设置
            if (localDescription.getBuiltinMappingsEnabled() != null) {
                this.builtinMappingsEnabled = localDescription.getBuiltinMappingsEnabled();
            }
        }
        
        // 再加载其他文件
        for (Map.Entry<String, LocalMappingFile> entry : allMappingFiles.entrySet()) {
            if (!LOCAL_DESCRIPTION_FILE.equals(entry.getKey())) {
                mergeSingleMappingFile(entry.getValue());
            }
        }
    }
    
    /**
     * 合并单个映射文件的数据
     */
    private void mergeSingleMappingFile(LocalMappingFile mappingFile) {
        if (mappingFile != null && mappingFile.getMappings() != null) {
            LocalMappingFile.Mappings mappings = mappingFile.getMappings();
            
            // 合并文件映射
            if (mappings.getFiles() != null) {
                annotations.putAll(mappings.getFiles());
            }
            
            // 合并包映射
            if (mappings.getPackages() != null) {
                packageAnnotations.putAll(mappings.getPackages());
            }
            
            // 合并文件匹配映射
            if (mappings.getFileMatch() != null) {
                fileMatchAnnotations.putAll(mappings.getFileMatch());
            }
            
            // 合并包匹配映射
            if (mappings.getPackageMatch() != null) {
                packageMatchAnnotations.putAll(mappings.getPackageMatch());
            }
            
            // 合并包备注字体颜色
            if (mappings.getPackagesTextColor() != null) {
                packagesTextColor.putAll(mappings.getPackagesTextColor());
            }
            
            // 合并文件备注字体颜色
            if (mappings.getFilesTextColor() != null) {
                filesTextColor.putAll(mappings.getFilesTextColor());
            }
            
            // 合并语言设置
            if (mappingFile.getLanguage() != null) {
                language = mappingFile.getLanguage();
            }
        }
    }
    
    /**
     * 尝试从旧的 annotations.xml 迁移数据
     */
    private void tryMigrateFromXml() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path oldXmlPath = Paths.get(basePath, "annotations.xml");
                if (Files.exists(oldXmlPath)) {
                    LOG.info("检测到旧的 annotations.xml 文件，尝试迁移...");
                    
                    String xmlContent = Files.readString(oldXmlPath);
                    
                    // 解析 XML 并迁移数据
                    migrateFromXmlContent(xmlContent);
                    
                    // 保存为新格式
                    saveToFile();
                    
                    LOG.info("数据迁移完成，建议删除旧的 annotations.xml 文件");
                }
            }
        } catch (IOException e) {
            LOG.error("迁移旧数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 XML 内容迁移数据
     */
    private void migrateFromXmlContent(String xmlContent) {
        try {
            // 清空现有数据
            annotations.clear();
            packageAnnotations.clear();
            fileMatchAnnotations.clear();
            packageMatchAnnotations.clear();
            
            // 解析各个部分
            parseXmlSectionForMigration(xmlContent, "files", annotations);
            parseXmlSectionForMigration(xmlContent, "packages", packageAnnotations);
            parseXmlSectionForMigration(xmlContent, "fileMatch", fileMatchAnnotations);
            parseXmlSectionForMigration(xmlContent, "packageMatch", packageMatchAnnotations);
            
            // 解析内置映射库开关状态
            parseBuiltinMappingsEnabledFromXml(xmlContent);
            
        } catch (Exception e) {
            LOG.error("迁移 XML 内容失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 XML 解析特定部分（迁移用）
     */
    private void parseXmlSectionForMigration(String xmlContent, String sectionName, Map<String, String> targetMap) {
        try {
            String startTag = "<entry key=\"" + sectionName + "\">";
            String endTag = "</entry>";
            
            int startIndex = xmlContent.indexOf(startTag);
            if (startIndex != -1) {
                int endIndex = xmlContent.indexOf(endTag, startIndex);
                if (endIndex != -1) {
                    String sectionContent = xmlContent.substring(startIndex, endIndex + endTag.length());
                    
                    // 解析entry标签
                    String[] lines = sectionContent.split("\n");
                    for (String line : lines) {
                        if (line.contains("<entry key=") && line.contains("value=")) {
                            // 提取key和value
                            int keyStart = line.indexOf("key=\"") + 5;
                            int keyEnd = line.indexOf("\"", keyStart);
                            int valueStart = line.indexOf("value=\"") + 7;
                            int valueEnd = line.indexOf("\"", valueStart);
                            
                            if (keyStart > 4 && keyEnd > keyStart && valueStart > 6 && valueEnd > valueStart) {
                                String key = line.substring(keyStart, keyEnd);
                                String value = line.substring(valueStart, valueEnd);
                                
                                // 反转义XML特殊字符
                                key = unescapeXml(key);
                                value = unescapeXml(value);
                                
                                targetMap.put(key, value);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("解析 XML 部分 " + sectionName + " 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 XML 解析内置映射库开关状态（迁移用）
     */
    private void parseBuiltinMappingsEnabledFromXml(String xmlContent) {
        try {
            String searchPattern = "<entry key=\"builtinMappingsEnabled\" value=\"";
            int startIndex = xmlContent.indexOf(searchPattern);
            if (startIndex != -1) {
                int valueStart = startIndex + searchPattern.length();
                int valueEnd = xmlContent.indexOf("\"", valueStart);
                if (valueEnd > valueStart) {
                    String value = xmlContent.substring(valueStart, valueEnd);
                    this.builtinMappingsEnabled = Boolean.parseBoolean(value);
                }
            }
        } catch (Exception e) {
            LOG.error("解析内置映射库开关状态失败: " + e.getMessage(), e);
            // 保持默认值 true
            this.builtinMappingsEnabled = true;
        }
    }
    
    /**
     * 检查文件名是否匹配给定的模式
     * @param fileName 文件名
     * @param pattern 匹配模式
     * @return 是否匹配
     */
    private boolean matchesFilePattern(@NotNull String fileName, @NotNull String pattern) {
        return matchesFilePattern(fileName, pattern, null);
    }
    
    /**
     * 检查文件名是否匹配给定的模式（支持混合匹配）
     * @param fileName 文件名
     * @param pattern 匹配模式
     * @param relativePath 文件的相对路径（可为null）
     * @return 是否匹配
     */
    private boolean matchesFilePattern(@NotNull String fileName, @NotNull String pattern, @Nullable String relativePath) {
        try {
            // 1. 尝试正则表达式匹配
            if (pattern.contains(".*") || pattern.contains("\\") || pattern.contains("$") || pattern.contains("^")) {
                return fileName.matches(pattern);
            }
            
            // 2. 检查是否为混合匹配模式（包含路径分隔符）
            if (pattern.contains("/")) {
                return matchesMixedPattern(fileName, pattern, relativePath);
            }
            
            // 3. 完全匹配 - 文件名必须完全相等
            return fileName.equalsIgnoreCase(pattern);
            
        } catch (Exception e) {
            // 如果出错，降级为简单包含匹配
            return fileName.toLowerCase().contains(pattern.toLowerCase());
        }
    }
    
    /**
     * 检查文件是否匹配混合模式（包路径+文件名）
     * @param fileName 当前文件名
     * @param pattern 混合模式，如 "com/common/pom.xml"
     * @param relativePath 文件的相对路径（可为null）
     * @return 是否匹配
     */
    private boolean matchesMixedPattern(@NotNull String fileName, @NotNull String pattern, @Nullable String relativePath) {
        // 从pattern中提取文件名和包路径
        int lastSlashIndex = pattern.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return false; // 不应该发生，因为调用前已检查包含'/'
        }
        
        String expectedFileName = pattern.substring(lastSlashIndex + 1);
        String expectedPackagePath = pattern.substring(0, lastSlashIndex);
        
        // 首先检查文件名是否匹配
        if (!fileName.equalsIgnoreCase(expectedFileName)) {
            return false;
        }
        
        // 如果没有相对路径信息，只能基于文件名匹配
        if (relativePath == null || relativePath.isEmpty()) {
            return true;
        }
        
        // 检查文件是否在期望的包路径中（后缀匹配）
        // 从relativePath中提取目录路径
        String fileDir = "";
        int fileLastSlashIndex = relativePath.lastIndexOf('/');
        if (fileLastSlashIndex != -1) {
            fileDir = relativePath.substring(0, fileLastSlashIndex);
        }
        
        // 检查目录路径是否以期望的包路径结尾
        return fileDir.endsWith(expectedPackagePath);
    }
    
    /**
     * 检查包路径是否匹配给定的模式
     * @param packagePath 包路径（可能使用 / 或 . 作为分隔符）
     * @param pattern 匹配模式
     * @return 是否匹配
     */
    private boolean matchesPackagePattern(@NotNull String packagePath, @NotNull String pattern) {
        try {
            // 1. 尝试正则表达式匹配
            if (pattern.contains(".*") || pattern.contains("\\") || pattern.contains("$") || pattern.contains("^")) {
                // 将包路径标准化（支持 / 和 . 分隔符）
                String normalizedPath = packagePath.replace("/", ".");
                return normalizedPath.matches(pattern);
            }
            
            // 2. 完整路径段匹配 - 确保pattern作为完整的路径段出现
            String normalizedPath = packagePath.replace("/", ".").toLowerCase();
            String lowerPattern = pattern.toLowerCase();
            
            // 将路径分割为段
            String[] pathSegments = normalizedPath.split("\\.");
            String[] patternSegments = lowerPattern.split("\\.");
            
            // 检查是否有连续的路径段匹配pattern的所有段
            if (patternSegments.length == 1) {
                // 单段匹配：只匹配路径的最后一段（目录名）
                if (pathSegments.length > 0) {
                    String lastSegment = pathSegments[pathSegments.length - 1];
                    return lastSegment.equals(lowerPattern);
                }
                return false;
            } else {
                // 多段匹配：后缀精准匹配（支持前缀路径，但后缀必须完全匹配）
                if (patternSegments.length > pathSegments.length) {
                    return false; // 模式段数不能超过路径段数
                }
                
                // 检查后缀是否完全匹配
                int pathStart = pathSegments.length - patternSegments.length;
                for (int i = 0; i < patternSegments.length; i++) {
                    if (!pathSegments[pathStart + i].equals(patternSegments[i])) {
                        return false;
                    }
                }
                return true;
            }
            
        } catch (Exception e) {
            // 如果出错，降级为简单匹配
            String normalizedPath = packagePath.replace("/", ".").toLowerCase();
            return normalizedPath.contains(pattern.toLowerCase());
        }
    }
    

    
    /**
     * 反转义XML特殊字符（仅用于迁移）
     */
    private String unescapeXml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&apos;", "'");
    }
    
    /**
     * 设置文件监听器，监听 .td-maps 目录下 JSON 文件的变化
     * 实现实时双向绑定：修改JSON文件后自动刷新项目视图
     */
    private void setupFileWatcher() {
        // 不再需要重复的VFS监听器，RealTimeAnnotationService中已有实时监听器
        // 保留方法体为空，避免双重监听问题
        LOG.info("VFS文件监听器已禁用，使用RealTimeAnnotationService的实时监听器");
    }
    
    /**
     * 重新从 .td-maps 目录加载所有映射文件
     */
    private void reloadFromMappingsDirectory() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path mappingsDir = Paths.get(basePath, MAPPINGS_DIR_NAME);
                if (Files.exists(mappingsDir)) {
                    // 扫描并加载所有 JSON 文件
                    scanAndLoadMappingFiles(mappingsDir);
                    
                    // 合并数据
                    mergeMappingData();
                    
                    LOG.info("已从 .td-maps 目录重新加载映射数据");
                }
            }
        } catch (IOException e) {
            LOG.error("重新加载映射目录失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 重新加载备注数据（公共方法）
     */
    public void reloadAnnotations() {
        reloadFromMappingsDirectory();
        
        // 强制刷新UI，确保备注显示更新
        forceRefreshUI();
    }
    
    /**
     * 清空.td-maps目录下除local-description.json外的其他文件
     */
    private void clearMappingsDirectory() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path mappingsDir = Paths.get(basePath, MAPPINGS_DIR_NAME);
                if (Files.exists(mappingsDir) && Files.isDirectory(mappingsDir)) {
                    try (Stream<Path> files = Files.list(mappingsDir)) {
                        files.filter(path -> !path.getFileName().toString().equals(LOCAL_DESCRIPTION_FILE))
                             .forEach(path -> {
                                 try {
                                     Files.deleteIfExists(path);
                                     LOG.info("已删除文件: " + path.toString());
                                 } catch (IOException e) {
                                     LOG.warn("删除文件失败: " + path.toString() + ", 错误: " + e.getMessage());
                                 }
                             });
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("清空.td-maps目录失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 强制刷新UI和编辑器内容
     */
    private void forceRefreshUIAndEditors() {
        try {
            // 异步执行刷新操作，避免阻塞UI
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // 刷新VFS
                    VFSRefreshService vfsService = new VFSRefreshService(project);
                    String basePath = project.getBasePath();
                    if (basePath != null) {
                        String jsonPath = basePath + "/.td-maps/local-description.json";
                        vfsService.refreshJsonFile(jsonPath);
                        vfsService.refreshMappingsDirectory();
                    }
                    
                    // 强制刷新项目视图
                    ProjectViewRefreshService refreshService = new ProjectViewRefreshService(project);
                    refreshService.refreshProjectView();
                    refreshService.forceNodeRedecoration();
                    
                    // 强制重新装饰所有节点
                    com.intellij.ide.projectView.ProjectView projectView = com.intellij.ide.projectView.ProjectView.getInstance(project);
                    if (projectView != null) {
                        projectView.refresh();
                    }
                    
                    // 强制刷新当前打开的编辑器
                    refreshOpenEditors();
                    
                    LOG.info("强制刷新UI和编辑器完成");
                } catch (Exception e) {
                    LOG.warn("强制刷新UI和编辑器失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("安排强制刷新UI和编辑器失败: " + e.getMessage());
        }
    }
    
    /**
     * 刷新当前打开的编辑器
     */
    private void refreshOpenEditors() {
        try {
            com.intellij.openapi.fileEditor.FileEditorManager editorManager = 
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
            
            // 获取所有打开的文件
            VirtualFile[] openFiles = editorManager.getOpenFiles();
            
            for (VirtualFile file : openFiles) {
                // 检查是否是JSON文件
                if (file.getName().endsWith(".json")) {
                    // 强制刷新文件内容
                    file.refresh(false, false);
                    
                    // 如果是local-description.json，触发编辑器重新加载
                    if (file.getName().equals(LOCAL_DESCRIPTION_FILE)) {
                        com.intellij.openapi.fileEditor.FileEditor[] editors = editorManager.getEditors(file);
                        for (com.intellij.openapi.fileEditor.FileEditor editor : editors) {
                            if (editor instanceof com.intellij.openapi.fileEditor.TextEditor) {
                                com.intellij.openapi.fileEditor.TextEditor textEditor = 
                                    (com.intellij.openapi.fileEditor.TextEditor) editor;
                                com.intellij.openapi.editor.Document document = textEditor.getEditor().getDocument();
                                
                                // 触发文档重新加载
                                com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> {
                                    try {
                                        String content = new String(file.contentsToByteArray(), file.getCharset());
                                        document.setText(content);
                                    } catch (IOException e) {
                                        LOG.warn("刷新编辑器内容失败: " + e.getMessage());
                                    }
                                });
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("刷新打开的编辑器失败: " + e.getMessage());
        }
    }
    
    /**
     * 强制刷新UI，确保备注显示更新
     */
    private void forceRefreshUI() {
        try {
            // 异步执行刷新操作，避免阻塞UI
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // 刷新VFS
                    VFSRefreshService vfsService = new VFSRefreshService(project);
                    String basePath = project.getBasePath();
                    if (basePath != null) {
                        String jsonPath = basePath + "/.td-maps/local-description.json";
                        vfsService.refreshJsonFile(jsonPath);
                        vfsService.refreshMappingsDirectory();
                    }
                    
                    // 强制刷新项目视图
                    ProjectViewRefreshService refreshService = new ProjectViewRefreshService(project);
                    refreshService.refreshProjectView();
                    refreshService.forceNodeRedecoration();
                    
                    // 强制重新装饰所有节点
                    com.intellij.ide.projectView.ProjectView projectView = com.intellij.ide.projectView.ProjectView.getInstance(project);
                    if (projectView != null) {
                        projectView.refresh();
                    }
                } catch (Exception e) {
                    LOG.warn("UI强制刷新失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("安排UI强制刷新失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存后立即刷新VFS和项目视图
     */
    private void refreshAfterSave() {
        try {
            // 异步执行刷新操作，避免阻塞UI
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // 刷新VFS
                    VFSRefreshService vfsService = new VFSRefreshService(project);
                    String basePath = project.getBasePath();
                    if (basePath != null) {
                        String jsonPath = basePath + "/.td-maps/local-description.json";
                        vfsService.refreshJsonFile(jsonPath);
                        vfsService.refreshMappingsDirectory();
                    }
                    
                    // 刷新项目视图
                    ProjectViewRefreshService refreshService = new ProjectViewRefreshService(project);
                    refreshService.refreshProjectView();
                    refreshService.forceNodeRedecoration();
                    
                    LOG.info("保存后刷新完成");
                } catch (Exception e) {
                    LOG.warn("保存后刷新失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("安排保存后刷新失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取项目树备注显示开关状态
     */
    public boolean isProjectTreeAnnotationsEnabled() {
        return projectTreeAnnotationsEnabled;
    }
    
    /**
     * 设置项目树备注显示开关状态
     */
    public void setProjectTreeAnnotationsEnabled(boolean enabled) {
        this.projectTreeAnnotationsEnabled = enabled;
        // 刷新项目视图以应用更改
        ProjectView.getInstance(project).refresh();
    }
    
    /**
     * 获取语言设置
     */
    public String getLanguage() {
        return language;
    }
    
    /**
     * 设置语言
     */
    public void setLanguage(String language) {
        this.language = language;
        // 同步到 LanguageManager
        com.github.annotations.utils.LanguageManager.setCurrentLanguage(
            com.github.annotations.utils.LanguageManager.Language.fromCode(language)
        );
        saveToFile(); // 保存到文件
    }
    
    /**
     * 初始化语言设置（由PostStartupActivity调用）
     */
    public void initializeLanguageSettings() {
        try {
            // 项目打开后初始化语言设置
            com.github.annotations.utils.LanguageManager.setCurrentLanguage(
                com.github.annotations.utils.LanguageManager.Language.fromCode(this.getLanguage())
            );
        } catch (Exception e) {
            LOG.warn("初始化语言设置失败: " + e.getMessage());
        }
    }
    
    /**
     * 注册项目启动监听器（使用StartupActivity替代已过时的ProjectManagerListener）
     */
    private void registerProjectListener() {
        // 此方法已由PostStartupActivity替代，不再需要内部API调用
        // 语言设置初始化现在由ProjectInitActivity处理
    }


}

