package com.github.annotations.services;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 应用级别的映射库管理服务
 * 负责管理预置的框架映射库
 */
@Service
public final class MappingLibraryService {
    
    private static final Logger LOG = Logger.getInstance(MappingLibraryService.class);
    private static final Gson gson = new Gson();
    
    private final Map<String, MappingLibrary> libraries = new HashMap<>();
    private boolean initialized = false;
    
    /**
     * 获取应用级别的服务实例
     */
    public static MappingLibraryService getInstance() {
        MappingLibraryService service = ApplicationManager.getApplication().getService(MappingLibraryService.class);
        // 确保在第一次获取服务时就初始化
        if (!service.initialized) {
            service.initializeDefaultLibraries();
        }
        return service;
    }
    
    /**
     * 检查内置映射库是否启用
     * 从项目级别的 AnnotationService 获取状态
     */
    public boolean isBuiltinMappingsEnabled() {
        // 尝试从当前项目获取状态，如果没有项目则返回默认值 true
        try {
            com.intellij.openapi.project.Project[] openProjects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                com.github.annotations.services.AnnotationService annotationService =
                    com.github.annotations.services.AnnotationService.getInstance(openProjects[0]);
                return annotationService.isBuiltinMappingsEnabled();
            }
        } catch (Exception e) {
            // 如果获取失败，返回默认值
        }
        return true; // 默认启用
    }
    
    /**
     * 设置内置映射库开关状态
     * 通过项目级别的 AnnotationService 设置状态
     */
    public void setBuiltinMappingsEnabled(boolean enabled) {
        try {
            com.intellij.openapi.project.Project[] openProjects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                com.github.annotations.services.AnnotationService annotationService =
                    com.github.annotations.services.AnnotationService.getInstance(openProjects[0]);
                annotationService.setBuiltinMappingsEnabled(enabled);
                
                // 根据新状态更新内置映射库
                if (!enabled) {
                    clearBuiltinLibraries();
                } else if (initialized) {
                    reloadBuiltinLibraries();
                }
            }
        } catch (Exception e) {
            // 如果设置失败，记录错误
            LOG.error("设置内置映射库开关状态失败", e);
        }
    }
    
    /**
     * 确保服务已初始化（性能优化：减少检查开销）
     */
    private void ensureInitialized() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
    }
    
    /**
     * 初始化预置映射库
     */
    public void initializeDefaultLibraries() {
        if (initialized) {
            return;
        }
        
        if (!isBuiltinMappingsEnabled()) {
            initialized = true;
            return;
        }
        
        try {
            loadBuiltinLibrary("spring-boot", "/.td-maps/spring-boot-common.json");
            loadBuiltinLibrary("apache", "/.td-maps/apache-commons.json");
            loadBuiltinLibrary("mybatis", "/.td-maps/mybatis-common.json");
            initialized = true;
        } catch (Exception e) {
            LOG.error("初始化预置映射库失败", e);
        }
    }
    
    /**
     * 清空内置映射库
     */
    private void clearBuiltinLibraries() {
        libraries.remove("spring-boot");
        libraries.remove("apache");
        libraries.remove("mybatis");
    }
    
    /**
     * 重新加载内置映射库
     */
    private void reloadBuiltinLibraries() {
        if (!isBuiltinMappingsEnabled()) {
            return;
        }
        
        try {
            loadBuiltinLibrary("spring-boot", "/.td-maps/spring-boot-common.json");
            loadBuiltinLibrary("apache", "/.td-maps/apache-commons.json");
            loadBuiltinLibrary("mybatis", "/.td-maps/mybatis-common.json");
        } catch (Exception e) {
            LOG.error("重新加载内置映射库失败", e);
        }
    }
    
    /**
     * 加载内置映射库
     */
    private void loadBuiltinLibrary(@NotNull String name, @NotNull String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return;
            }
            
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            MappingLibrary library = gson.fromJson(reader, MappingLibrary.class);
            
            if (library != null) {
                libraries.put(name, library);
            }
            
        } catch (IOException | JsonSyntaxException e) {
            LOG.error("加载映射库失败: " + resourcePath, e);
        }
    }
    
    /**
     * 获取所有可用的映射库
     */
    @NotNull
    public Collection<MappingLibrary> getAllLibraries() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        return new ArrayList<>(libraries.values());
    }
    
    /**
     * 根据名称获取映射库
     */
    @Nullable
    public MappingLibrary getLibrary(@NotNull String name) {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        return libraries.get(name);
    }
    
    /**
     * 搜索包名映射
     * 性能优化：确保初始化 + 快速失败
     */
    @Nullable
    public String searchPackageMapping(@NotNull String packageName) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.packages != null) {
                String mapping = library.mappings.packages.get(packageName);
                if (mapping != null) {
                    return mapping;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 搜索文件名映射
     * 性能优化：确保初始化 + 快速失败
     */
    @Nullable
    public String searchFileMapping(@NotNull String fileName) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.files != null) {
                String mapping = library.mappings.files.get(fileName);
                if (mapping != null) {
                    return mapping;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 搜索通用模式映射
     * 性能优化：减少字符串操作 + 快速失败
     */
    @Nullable
    public String searchPatternMapping(@NotNull String pattern) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.packageMatch != null) {
                for (Map.Entry<String, String> entry : library.mappings.packageMatch.entrySet()) {
                    // 使用精确匹配逻辑，与用户映射保持一致
                    if (matchesPackagePattern(pattern, entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 搜索文件匹配模式映射
     * 性能优化：减少字符串操作 + 快速失败
     */
    @Nullable
    public String searchFileMatchMapping(@NotNull String fileName) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.fileMatch != null) {
                for (Map.Entry<String, String> entry : library.mappings.fileMatch.entrySet()) {
                    // 使用精确匹配逻辑，与用户映射保持一致
                    if (matchesFilePattern(fileName, entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 智能搜索映射
     * 按优先级搜索：精确包名 -> 精确文件名 -> 模式匹配
     */
    @Nullable
    public String smartSearch(@NotNull String name) {
        // 1. 精确包名匹配
        String packageMapping = searchPackageMapping(name);
        if (packageMapping != null) {
            return packageMapping;
        }
        
        // 2. 精确文件名匹配
        String fileMapping = searchFileMapping(name);
        if (fileMapping != null) {
            return fileMapping;
        }
        
        // 3. 模式匹配
        return searchPatternMapping(name);
    }
    
    /**
     * 获取所有映射的合并结果 - 用于搜索功能
     * 返回 文件路径 -> 中文说明 的映射
     * 注意：不包含 packageMatch，因为它们只是包名匹配模式，不是实际文件
     */
    @NotNull
    public Map<String, String> getAllMappings() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        
        Map<String, String> allMappings = new HashMap<>();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null) {
                // 添加包映射
                if (library.mappings.packages != null) {
                    allMappings.putAll(library.mappings.packages);
                }
                
                // 添加文件映射
                if (library.mappings.files != null) {
                    allMappings.putAll(library.mappings.files);
                }
                
                // 不添加 packageMatch，因为它们只是包名匹配模式
                // 例如：controller -> "控制器层" 只用于包名显示，不用于搜索
            }
        }
        
        return allMappings;
    }
    
    /**
     * 获取所有包匹配模式映射 - 仅用于包名显示
     * 返回 模式 -> 中文说明 的映射
     */
    @NotNull
    public Map<String, String> getAllPackageMatchPatterns() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        
        Map<String, String> allPatterns = new HashMap<>();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.packageMatch != null) {
                allPatterns.putAll(library.mappings.packageMatch);
            }
        }
        
        return allPatterns;
    }
    
    /**
     * 获取所有文件匹配模式映射 - 仅用于文件名显示
     * 返回 模式 -> 中文说明 的映射
     */
    @NotNull
    public Map<String, String> getAllFileMatchPatterns() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        
        Map<String, String> allPatterns = new HashMap<>();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.fileMatch != null) {
                allPatterns.putAll(library.mappings.fileMatch);
            }
        }
        
        return allPatterns;
    }
    
    /**
     * 从JSON字符串加载自定义映射库
     */
    public boolean loadCustomLibrary(@NotNull String name, @NotNull String jsonContent) {
        try {
            // 尝试解析为映射库格式
            MappingLibrary library = gson.fromJson(jsonContent, MappingLibrary.class);
            if (library != null) {
                // 检查映射库内容是否有效
                boolean hasContent = false;
                
                if (library.mappings != null) {
                    int packageCount = (library.mappings.packages != null) ? library.mappings.packages.size() : 0;
                    int fileCount = (library.mappings.files != null) ? library.mappings.files.size() : 0;
                    int patternCount = (library.mappings.packageMatch != null) ? library.mappings.packageMatch.size() : 0;
                    
                    hasContent = (packageCount + fileCount + patternCount) > 0;
                    
                    LOG.info("导入映射库: " + (library.name != null ? library.name : name));
                    LOG.info("- 包映射: " + packageCount + " 条");
                    LOG.info("- 文件映射: " + fileCount + " 条");
                    LOG.info("- 通用模式: " + patternCount + " 条");
                }
                
                if (!hasContent) {
                    LOG.warn("警告: 导入的映射库没有有效内容");
                    return false;
                }
                
                // 如果没有设置名称，使用提供的名称
                if (library.name == null || library.name.isEmpty()) {
                    library.name = name;
                }
                
                // 保存到映射库集合
                libraries.put(name, library);
                LOG.info("成功加载自定义映射库: " + library.name);
                return true;
            } else {
                LOG.warn("错误: 解析的映射库对象为null");
            }
        } catch (JsonSyntaxException e) {
            LOG.error("解析自定义映射库失败", e);
        }
        return false;
    }
    
    /**
     * 映射库数据结构
     */
    public static class MappingLibrary {
        public String name;
        public String version;
        public String description;
        public String author;
        public Mappings mappings;
        public List<String> tags;
        public String lastUpdated;
        
        public static class Mappings {
            public Map<String, String> packages;      // 包名映射：org.springframework.boot -> "Spring Boot框架"
            public Map<String, String> files;         // 文件名映射：pom.xml -> "Maven项目配置文件"
            public Map<String, String> packageMatch; // 包名模式映射：controller -> "控制器层"（仅用于包名显示，不用于搜索）
            public Map<String, String> fileMatch;    // 文件名模式映射：Service -> "业务逻辑层"（仅用于文件名显示，不用于搜索）
        }
    }
    
    /**
     * 检查文件名是否匹配给定的模式
     * @param fileName 文件名
     * @param pattern 匹配模式
     * @return 是否匹配
     */
    private boolean matchesFilePattern(@NotNull String fileName, @NotNull String pattern) {
        try {
            // 1. 尝试正则表达式匹配
            if (pattern.contains(".*") || pattern.contains("\\") || pattern.contains("$") || pattern.contains("^")) {
                return fileName.matches(pattern);
            }
            
            // 2. 完全匹配 - 文件名必须完全相等
            return fileName.equalsIgnoreCase(pattern);
            
        } catch (Exception e) {
            // 如果出错，降级为简单包含匹配
            return fileName.toLowerCase().contains(pattern.toLowerCase());
        }
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

}

