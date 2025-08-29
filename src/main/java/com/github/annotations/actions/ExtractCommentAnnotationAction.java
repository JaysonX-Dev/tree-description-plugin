package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.ui.ExtractCommentDialog;
import com.github.annotations.utils.I18nUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提取注释为备注Action
 * 从Java文件的Javadoc注释中提取第一行作为备注
 */
public class ExtractCommentAnnotationAction extends AnAction {
    
    // 预编译的正则表达式，用于匹配Javadoc注释的第一行
    private static final Pattern JAVADOC_PATTERN = Pattern.compile(
        "/\\*\\*\\s*\\n\\s*\\*\\s*(.+?)\\s*(?:\\n|\\*/)", 
        Pattern.DOTALL | Pattern.MULTILINE
    );
    
    // 通知组
    private static final NotificationGroup NOTIFICATION_GROUP = 
        NotificationGroupManager.getInstance().getNotificationGroup("Extract.Comment.Notification");
    
    public ExtractCommentAnnotationAction() {
        super();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        // 始终显示菜单项，不进行任何可见性控制
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
        
        // 更新Action的显示文本
        String text = I18nUtils.getText(project, "提取注释为备注", "Extract Comment as Remark");
        e.getPresentation().setText(text);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        
        if (project == null || files == null || files.length == 0) {
            return;
        }
        
        // 在后台线程中提取注释内容
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<VirtualFile> javaFiles = collectJavaFiles(files);
            Map<VirtualFile, String> extractedComments = new HashMap<>();
            
            // 提取所有文件的注释
            for (VirtualFile file : javaFiles) {
                String comment = extractCommentFromFile(project, file);
                if (comment != null && !comment.trim().isEmpty()) {
                    extractedComments.put(file, comment.trim());
                }
            }
            
            // 在EDT线程中显示对话框
            ApplicationManager.getApplication().invokeLater(() -> {
                if (extractedComments.isEmpty()) {
                    showNotification(project, 0, javaFiles.size());
                    return;
                }
                
                // 显示提取注释对话框
                List<String> commentList = new ArrayList<>(extractedComments.values());
                ExtractCommentDialog dialog = new ExtractCommentDialog(project, commentList, extractedComments.size());
                
                if (dialog.showAndGet()) {
                    String selectedColor = dialog.getSelectedColor();
                    
                    // 保存所有提取的注释
                    AnnotationService service = AnnotationService.getInstance(project);
                    if (service != null) {
                        int successCount = 0;
                        for (Map.Entry<VirtualFile, String> entry : extractedComments.entrySet()) {
                            VirtualFile file = entry.getKey();
                            String comment = entry.getValue();
                            String relativePath = getRelativePath(project, file);
                            
                            if (relativePath != null) {
                                service.setAnnotationAndRefresh(relativePath, comment, selectedColor);
                                successCount++;
                            }
                        }
                        
                        // 显示成功通知
                        showNotification(project, successCount, javaFiles.size());
                    }
                }
            });
        });
    }
    
    /**
     * 检查文件数组中是否包含Java文件
     */
    private boolean hasJavaFiles(VirtualFile[] files) {
        for (VirtualFile file : files) {
            if (isJavaFileOrDirectory(file)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 收集所有Java文件（包括目录中的Java文件）
     */
    private List<VirtualFile> collectJavaFiles(VirtualFile[] files) {
        List<VirtualFile> javaFiles = new ArrayList<>();
        for (VirtualFile file : files) {
            collectJavaFilesRecursively(file, javaFiles);
        }
        return javaFiles;
    }
    
    /**
     * 递归收集Java文件
     */
    private void collectJavaFilesRecursively(VirtualFile file, List<VirtualFile> javaFiles) {
        if (file.isDirectory()) {
            VirtualFile[] children = file.getChildren();
            if (children != null) {
                for (VirtualFile child : children) {
                    collectJavaFilesRecursively(child, javaFiles);
                }
            }
        } else if ("java".equalsIgnoreCase(file.getExtension())) {
            javaFiles.add(file);
        }
    }
    
    /**
     * 检查是否为Java文件或包含Java文件的目录
     */
    private boolean isJavaFileOrDirectory(VirtualFile file) {
        if (file.isDirectory()) {
            return true; // 目录可能包含Java文件
        }
        return "java".equalsIgnoreCase(file.getExtension());
    }
    
    /**
     * 从单个文件中提取注释
     */
    private String extractCommentFromFile(Project project, VirtualFile file) {
        try {
            // 获取文件编码
            Charset charset = file.getCharset();
            
            // 读取文件前100行
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset))) {
                
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 100) {
                    content.append(line).append("\n");
                    lineCount++;
                }
            }
            
            // 提取Javadoc注释的第一行
            return extractFirstJavadocLine(content.toString());
        } catch (IOException e) {
            // 忽略IO异常，继续处理其他文件
        }
        return null;
    }
    
    /**
     * 提取Javadoc注释的第一行
     */
    private String extractFirstJavadocLine(String content) {
        Matcher matcher = JAVADOC_PATTERN.matcher(content);
        if (matcher.find()) {
            String firstLine = matcher.group(1);
            if (firstLine != null) {
                // 清理注释内容，移除多余的空格和特殊字符
                return firstLine.replaceAll("\s+", " ").trim();
            }
        }
        return null;
    }
    
    /**
     * 显示处理结果通知
     */
    private void showNotification(Project project, int successCount, int totalCount) {
        String message;
        NotificationType type;
        
        if (successCount == 0) {
            message = I18nUtils.getText(project, 
                "未找到可提取的注释", 
                "No extractable comments found");
            type = NotificationType.WARNING;
        } else if (successCount == totalCount) {
            message = I18nUtils.getText(project, 
                String.format("成功提取 %d 个文件的注释为备注", successCount),
                String.format("Successfully extracted comments from %d files as remarks", successCount));
            type = NotificationType.INFORMATION;
        } else {
            message = I18nUtils.getText(project, 
                String.format("成功提取 %d/%d 个文件的注释为备注", successCount, totalCount),
                String.format("Successfully extracted comments from %d/%d files as remarks", successCount, totalCount));
            type = NotificationType.INFORMATION;
        }
        
        Notification notification = NOTIFICATION_GROUP.createNotification(
            I18nUtils.getText(project, "提取注释为备注", "Extract Comment as Remark"),
            message,
            type
        );
        notification.notify(project);
    }
    
    /**
     * 获取相对于项目根目录的路径
     */
    @Nullable
    private String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            if (filePath.length() > projectPath.length()) {
                String relativePath = filePath.substring(projectPath.length());
                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            } else if (filePath.equals(projectPath)) {
                return "";
            }
        }
        
        return null;
    }
}