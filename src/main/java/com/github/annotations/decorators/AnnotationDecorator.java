package com.github.annotations.decorators;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.services.MappingLibraryService;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
// 使用固定颜色，不再依赖主题感知
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

    /**
     * 项目树节点装饰器
     * 负责在项目树中显示中文备注
     * 
     * 性能优化：
     * - 快速路径检查避免不必要的计算
     * - 实时检查映射库开关状态
     */
public class AnnotationDecorator implements ProjectViewNodeDecorator {
    
    // 使用固定的颜色
    private static SimpleTextAttributes getAnnotationAttributes() {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, new Color(187, 187, 187)); // 浅灰色 #BBBBBB
    }
    
    // 根据颜色配置获取文本属性
    private static SimpleTextAttributes getAnnotationAttributesWithColor(String colorHex) {
        if (colorHex != null && !colorHex.trim().isEmpty()) {
            try {
                Color color = Color.decode(colorHex);
                return new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, color);
            } catch (NumberFormatException e) {
                // 如果颜色解析失败，使用默认颜色
                return getAnnotationAttributes();
            }
        }
        return getAnnotationAttributes();
    }
    
    // 不再使用缓存，确保开关状态实时更新
    
    @Override
    public void decorate(ProjectViewNode<?> node, PresentationData data) {
        Project project = node.getProject();
        if (project == null) {
            return;
        }
        
        // 检查项目树备注开关状态
        AnnotationService annotationService = AnnotationService.getInstance(project);
        if (!annotationService.isProjectTreeAnnotationsEnabled()) {
            return; // 如果开关关闭，不显示任何备注
        }
        
        VirtualFile file = getVirtualFile(node);
        if (file == null) {
            return;
        }
        
        String annotation = getAnnotationForFile(project, file);
        if (annotation != null && !annotation.trim().isEmpty()) {
            // 在原有文本后添加中文备注
            String originalText = data.getPresentableText();
            if (originalText != null) {
                data.clearText();
                data.addText(originalText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                
                // 获取对应的颜色配置（传入备注内容避免循环调用）
                String textColor = getTextColorForFile(project, file, annotation);
                SimpleTextAttributes annotationAttributes = getAnnotationAttributesWithColor(textColor);
                
                data.addText("  " + annotation, annotationAttributes);
            }
        }
    }
    
    /**
     * 获取节点对应的虚拟文件
     */
    @Nullable
    private VirtualFile getVirtualFile(@NotNull ProjectViewNode<?> node) {
        Object value = node.getValue();
        
        if (value instanceof PsiDirectory) {
            return ((PsiDirectory) value).getVirtualFile();
        } else if (value instanceof PsiFile) {
            return ((PsiFile) value).getVirtualFile();
        } else if (value instanceof VirtualFile) {
            return (VirtualFile) value;
        }
        
        return node.getVirtualFile();
    }
    

    @Nullable
    private String getAnnotationForFile(@NotNull Project project, @NotNull VirtualFile file) {
        String name = file.getName();
        if (name.isEmpty() || name.startsWith(".") && name.length() < 3) {
            return null;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        // 优先从实时服务获取数据（如果可用）
        String realTimeAnnotation = getRealTimeAnnotation(project, file);
        if (realTimeAnnotation != null && !realTimeAnnotation.isEmpty()) {
            return realTimeAnnotation.trim();
        }
        
        // 1. 用户文件映射
        String userFileAnnotation = annotationService.getAnnotation(file);
        if (userFileAnnotation != null && !userFileAnnotation.isEmpty()) {
            return userFileAnnotation.trim();
        }
        
        // 2. 用户包映射
        String userPackageAnnotation = annotationService.getPackageAnnotation(file);
        if (userPackageAnnotation != null && !userPackageAnnotation.isEmpty()) {
            return userPackageAnnotation.trim();
        }
        
        // 3. 用户文件匹配映射（仅对文件生效）
        if (!file.isDirectory()) {
            // 获取完整的相对路径用于混合匹配
            String relativePath = getRelativePathForMatching(project, file);
            String userFileMatchAnnotation = getUserFilePatternAnnotation(annotationService, name, relativePath);
            if (userFileMatchAnnotation != null && !userFileMatchAnnotation.isEmpty()) {
                return userFileMatchAnnotation.trim();
            }
        }
        
        // 4. 用户包匹配映射（仅对目录/包生效）
        if (file.isDirectory()) {
            // 获取完整的相对路径用于包匹配
            String relativePath = getRelativePathForMatching(project, file);
            String userPackageMatchAnnotation = getUserPackagePatternAnnotation(annotationService, relativePath != null ? relativePath : name);
            if (userPackageMatchAnnotation != null && !userPackageMatchAnnotation.isEmpty()) {
                return userPackageMatchAnnotation.trim();
            }
        }
        
        // 5. 检查内置映射库（如果启用）
        MappingLibraryService mappingService = getMappingService();
        if (mappingService.isBuiltinMappingsEnabled()) {
            // 5.1 内置文件映射
            String builtinFileMapping = mappingService.searchFileMapping(name);
            if (builtinFileMapping != null) {
                return builtinFileMapping;
            }
            
            // 5.2 内置包映射
            String builtinPackageMapping = mappingService.searchPackageMapping(name);
            if (builtinPackageMapping != null) {
                return builtinPackageMapping;
            }
            
            // 5.3 内置文件匹配映射（仅对文件生效）
            if (!file.isDirectory()) {
                String builtinFileMatchMapping = mappingService.searchFileMatchMapping(name);
                if (builtinFileMatchMapping != null) {
                    return builtinFileMatchMapping;
                }
            }
            
            // 5.4 内置包匹配映射（仅对目录/包生效）
            if (file.isDirectory()) {
                // 获取完整的相对路径用于包匹配
                String relativePath = getRelativePathForMatching(project, file);
                String builtinPackageMatchMapping = mappingService.searchPatternMapping(relativePath != null ? relativePath : name);
                if (builtinPackageMatchMapping != null) {
                    return builtinPackageMatchMapping;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取用户自定义的文件匹配模式备注
     */
    @Nullable
    private String getUserFilePatternAnnotation(@NotNull AnnotationService annotationService, @NotNull String fileName, @Nullable String relativePath) {
        return annotationService.getFileMatchAnnotation(fileName, relativePath);
    }
    
    /**
     * 获取用户自定义的包匹配模式备注
     */
    @Nullable
    private String getUserPackagePatternAnnotation(@NotNull AnnotationService annotationService, @NotNull String packageName) {
        return annotationService.getPackageMatchAnnotation(packageName);
    }
    
    /**
     * 获取文件对应的文本颜色
     * 根据实际显示的备注类型获取对应的颜色
     */
    private String getTextColorForFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull String annotation) {
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        // 根据备注来源确定颜色
        String name = file.getName();
        
        // 1. 检查用户文件映射颜色
        String userFileAnnotation = annotationService.getAnnotation(file);
        if (userFileAnnotation != null && userFileAnnotation.trim().equals(annotation.trim())) {
            String relativePath = getRelativePathForMatching(project, file);
            if (relativePath != null) {
                String fileColor = annotationService.getFileTextColor(relativePath);
                if (fileColor != null) {
                    return fileColor;
                }
            }
        }
        
        // 2. 检查用户包映射颜色
        String userPackageAnnotation = annotationService.getPackageAnnotation(file);
        if (userPackageAnnotation != null && userPackageAnnotation.trim().equals(annotation.trim())) {
            String relativePath = getRelativePathForMatching(project, file);
            if (relativePath != null) {
                String packageColor = annotationService.getPackageTextColor(relativePath);
                if (packageColor != null) {
                    return packageColor;
                }
            }
        }
        
        // 3. 检查用户文件匹配映射颜色
        if (!file.isDirectory()) {
            String relativePath = getRelativePathForMatching(project, file);
            String userFileMatchAnnotation = getUserFilePatternAnnotation(annotationService, name, relativePath);
            if (userFileMatchAnnotation != null && userFileMatchAnnotation.trim().equals(annotation.trim())) {
                String fileColor = annotationService.getFileTextColor(name);
                if (fileColor != null) {
                    return fileColor;
                }
            }
        }
        
        // 4. 检查用户包匹配映射颜色
        if (file.isDirectory()) {
            String relativePath = getRelativePathForMatching(project, file);
            if (relativePath != null) {
                String userPackageMatchAnnotation = getUserPackagePatternAnnotation(annotationService, relativePath);
                if (userPackageMatchAnnotation != null && userPackageMatchAnnotation.trim().equals(annotation.trim())) {
                    String packageColor = annotationService.getPackageTextColor(relativePath);
                    if (packageColor != null) {
                        return packageColor;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 从实时服务获取备注数据
     */
    @Nullable
    private String getRealTimeAnnotation(@NotNull Project project, @NotNull VirtualFile file) {
        try {
            // 通过反射获取实时服务实例（避免循环依赖）
            Class<?> realTimeServiceClass = Class.forName("com.github.annotations.services.RealTimeAnnotationService");
            Object realTimeService = project.getService(realTimeServiceClass);
            
            if (realTimeService != null) {
                // 获取文件路径
                String relativePath = getRelativePathForMatching(project, file);
                if (relativePath != null) {
                    // 尝试获取文件备注
                    if (!file.isDirectory()) {
                        try {
                            java.lang.reflect.Method getFileAnnotationMethod = 
                                realTimeServiceClass.getMethod("getFileAnnotation", String.class);
                            String annotation = (String) getFileAnnotationMethod.invoke(realTimeService, relativePath);
                            if (annotation != null && !annotation.isEmpty()) {
                                return annotation;
                            }
                        } catch (Exception e) {
                            // 忽略反射错误，降级到普通服务
                        }
                    } else {
                        // 目录/包备注
                        try {
                            java.lang.reflect.Method getPackageAnnotationMethod = 
                                realTimeServiceClass.getMethod("getPackageAnnotation", String.class);
                            String annotation = (String) getPackageAnnotationMethod.invoke(realTimeService, relativePath);
                            if (annotation != null && !annotation.isEmpty()) {
                                return annotation;
                            }
                        } catch (Exception e) {
                            // 忽略反射错误，降级到普通服务
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 如果实时服务不可用，忽略错误，降级到普通服务
        }
        
        return null;
    }
    
    /**
     * 获取映射库服务实例（使用缓存提高性能）
     * 注意：每次调用都获取最新实例，确保开关状态实时更新
     */
    private static MappingLibraryService getMappingService() {
        // 每次都获取最新实例，确保开关状态实时更新
        return MappingLibraryService.getInstance();
    }
    
    /**
     * 获取文件相对于项目根目录的路径，用于模式匹配
     * @param project 项目实例
     * @param file 虚拟文件
     * @return 相对路径，使用 / 作为分隔符（与配置文件格式一致）
     */
    @Nullable
    private String getRelativePathForMatching(@NotNull Project project, @NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            // 确保有子路径才截取
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
}

