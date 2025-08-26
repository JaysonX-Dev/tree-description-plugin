package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveAnnotationAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        if (isMultipleSelection(e)) {
            handleMultipleFiles(e, project);
        } else {
            handleSingleFile(e, project);
        }
    }

    private void handleMultipleFiles(@NotNull AnActionEvent e, @NotNull Project project) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        int annotatedCount = 0;
        int fileCount = 0;
        int directoryCount = 0;
        
        for (VirtualFile file : files) {
            if (file.isDirectory()) {
                if (annotationService.hasPackageAnnotation(file)) {
                    annotatedCount++;
                    directoryCount++;
                }
            } else {
                if (annotationService.hasAnnotation(file)) {
                    annotatedCount++;
                    fileCount++;
                }
            }
        }
        
        if (annotatedCount == 0) {
            Messages.showInfoMessage(project, 
                I18nUtils.getText(project, "选中的文件/目录没有备注", "Selected files/directories have no annotations"), 
                I18nUtils.getText(project, "删除备注", "Remove Annotation"));
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append(I18nUtils.getText(project, "确定要删除选中的 ", "Are you sure you want to delete the selected ")).append(annotatedCount).append(I18nUtils.getText(project, " 个", " "));
        
        if (fileCount > 0 && directoryCount > 0) {
            message.append(I18nUtils.getText(project, "文件/目录", "files/directories"));
        } else if (fileCount > 0) {
            message.append(I18nUtils.getText(project, "文件", "files"));
        } else {
            message.append(I18nUtils.getText(project, "目录", "directories"));
        }
        
        message.append(I18nUtils.getText(project, "的备注吗？", " annotations?"));
        
        int result = Messages.showYesNoDialog(
            project,
            message.toString(),
            I18nUtils.getText(project, "确认批量删除", "Confirm Batch Delete"),
            Messages.getQuestionIcon()
        );
        
        if (result == Messages.YES) {
            int deletedCount = 0;
            
            for (VirtualFile file : files) {
                String relativePath = getRelativePath(project, file);
                if (relativePath != null) {
                    if (file.isDirectory()) {
                        if (annotationService.hasPackageAnnotation(relativePath)) {
                            annotationService.removePackageAnnotation(relativePath);
                            deletedCount++;
                        }
                    } else {
                        if (annotationService.hasAnnotation(relativePath)) {
                            annotationService.removeAnnotation(relativePath);
                            deletedCount++;
                        }
                    }
                } else {
                    if (file.isDirectory()) {
                        if (annotationService.hasPackageAnnotation(file.getPath())) {
                            annotationService.removePackageAnnotation(file.getPath());
                            deletedCount++;
                        }
                    } else {
                        if (annotationService.hasAnnotation(file.getPath())) {
                            annotationService.removeAnnotation(file.getPath());
                            deletedCount++;
                        }
                    }
                }
            }
            
            // 使用AnnotationService的刷新机制，确保JSON文件和UI同步更新
            try {
                java.lang.reflect.Method refreshMethod = annotationService.getClass().getDeclaredMethod("refreshAfterSave");
                refreshMethod.setAccessible(true);
                refreshMethod.invoke(annotationService);
            } catch (Exception ex) {
                // 如果反射调用失败，回退到基本的项目视图刷新
                ProjectView.getInstance(project).refresh();
            }
        }
    }

    private void handleSingleFile(@NotNull AnActionEvent e, @NotNull Project project) {
        VirtualFile file = getSelectedFile(e);
        if (file == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        String existingAnnotation;
        
        if (file.isDirectory()) {
            existingAnnotation = annotationService.getPackageAnnotation(file);
        } else {
            existingAnnotation = annotationService.getAnnotation(file);
        }
        
        if (existingAnnotation == null) {
            String message = file.isDirectory() ? 
                I18nUtils.getText(project, "该目录没有备注", "This directory has no annotation") : 
                I18nUtils.getText(project, "该文件没有备注", "This file has no annotation");
            Messages.showInfoMessage(project, message, I18nUtils.getText(project, "删除备注", "Remove Annotation"));
            return;
        }
        
        int result = Messages.showYesNoDialog(
            project,
            I18nUtils.getText(project, 
                "确定要删除 \"" + file.getName() + "\" 的备注吗？\n\n当前备注：" + existingAnnotation,
                "Are you sure you want to delete the annotation for \"" + file.getName() + "\"?\n\nCurrent annotation: " + existingAnnotation),
            I18nUtils.getText(project, "确认删除备注", "Confirm Delete Annotation"),
            Messages.getQuestionIcon()
        );
        
        if (result == Messages.YES) {
            String relativePath = getRelativePath(project, file);
            if (relativePath != null) {
                if (file.isDirectory()) {
                    annotationService.removePackageAnnotation(relativePath);
                } else {
                    annotationService.removeAnnotation(relativePath);
                }
            } else {
                if (file.isDirectory()) {
                    annotationService.removePackageAnnotation(file.getPath());
                } else {
                    annotationService.removeAnnotation(file.getPath());
                }
            }
            
            // 使用AnnotationService的刷新机制，确保JSON文件和UI同步更新
            try {
                java.lang.reflect.Method refreshMethod = annotationService.getClass().getDeclaredMethod("refreshAfterSave");
                refreshMethod.setAccessible(true);
                refreshMethod.invoke(annotationService);
            } catch (Exception ex) {
                // 如果反射调用失败，回退到基本的项目视图刷新
                ProjectView.getInstance(project).refresh();
            }
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        // 检查是否为多选
        if (isMultipleSelection(e)) {
            // 多选时，检查是否至少有一个文件有备注
            VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            if (files == null || files.length == 0) {
                e.getPresentation().setEnabledAndVisible(false);
                return;
            }
            
            AnnotationService annotationService = AnnotationService.getInstance(project);
            boolean hasAnyAnnotation = false;
            
            for (VirtualFile file : files) {
                if (file.isDirectory()) {
                    if (annotationService.hasPackageAnnotation(file)) {
                        hasAnyAnnotation = true;
                        break;
                    }
                } else {
                    if (annotationService.hasAnnotation(file)) {
                        hasAnyAnnotation = true;
                        break;
                    }
                }
            }
            
            // 始终显示菜单项，但根据是否有备注来启用/禁用
            e.getPresentation().setVisible(true);
            e.getPresentation().setEnabled(hasAnyAnnotation);
            
            if (hasAnyAnnotation) {
                // 根据当前语言动态显示菜单文本
                String currentLanguage = annotationService.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Remove Annotation");
                    e.getPresentation().setDescription("Remove user annotations from selected files");
                } else {
                    e.getPresentation().setText("删除备注");
                    e.getPresentation().setDescription("删除选中文件的用户备注");
                }
            } else {
                // 根据当前语言动态显示菜单文本
                String currentLanguage = annotationService.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Remove Annotation");
                    e.getPresentation().setDescription("Selected files have no user annotations to remove");
                } else {
                    e.getPresentation().setText("删除备注");
                    e.getPresentation().setDescription("选中的文件没有用户备注可删除");
                }
            }
            return;
        }
        
        // 单选时，检查文件是否有备注
        VirtualFile file = getSelectedFile(e);
        if (file == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        boolean hasAnnotation;
        
        if (file.isDirectory()) {
            hasAnnotation = annotationService.hasPackageAnnotation(file);
        } else {
            hasAnnotation = annotationService.hasAnnotation(file);
        }
        
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(hasAnnotation);
        
        if (hasAnnotation) {
            // 根据当前语言动态显示菜单文本
            String currentLanguage = annotationService.getLanguage();
            if ("en".equals(currentLanguage)) {
                e.getPresentation().setText("Remove Annotation");
                e.getPresentation().setDescription("Remove user annotation from this file");
            } else {
                e.getPresentation().setText("删除备注");
                e.getPresentation().setDescription("删除该文件的用户备注");
            }
        } else {
            // 根据当前语言动态显示菜单文本
            String currentLanguage = annotationService.getLanguage();
            if ("en".equals(currentLanguage)) {
                e.getPresentation().setText("Remove Annotation");
                e.getPresentation().setDescription("This file has no user annotation to remove");
            } else {
                e.getPresentation().setText("删除备注");
                e.getPresentation().setDescription("该文件没有用户备注可删除");
            }
        }
    }
    
    private boolean isMultipleSelection(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        return files != null && files.length > 1;
    }
    
    @Nullable
    private VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length > 0) {
            return files[0];
        }
        
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiFile) {
            return ((PsiFile) psiElement).getVirtualFile();
        } else if (psiElement instanceof PsiDirectory) {
            return ((PsiDirectory) psiElement).getVirtualFile();
        }
        
        // 方法3：从导航目标获取
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return file;
        }
        
        return null;
    }
    
    @Nullable
    private String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
        VirtualFile projectRoot = project.getBaseDir();
        if (projectRoot == null) {
            return null;
        }
        
        String projectPath = projectRoot.getPath();
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
