package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.services.MappingLibraryService;
import com.github.annotations.ui.AddAnnotationDialog;
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class AddAnnotationAction extends AnAction {
    
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
        
        int fileCount = 0;
        int directoryCount = 0;
        for (VirtualFile file : files) {
            if (file.isDirectory()) {
                directoryCount++;
            } else {
                fileCount++;
            }
        }
        
        StringBuilder message = new StringBuilder();
        message.append("为选中的 ").append(files.length).append(" 个");
        
        if (fileCount > 0 && directoryCount > 0) {
            message.append("文件/目录");
        } else if (fileCount > 0) {
            message.append("文件");
        } else {
            message.append("目录");
        }
        
        message.append("添加或更新备注：");
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        String existingAnnotation = null;
        VirtualFile firstFile = files[0];
        if (firstFile.isDirectory()) {
            existingAnnotation = annotationService.getPackageAnnotation(firstFile);
        } else {
            existingAnnotation = annotationService.getAnnotation(firstFile);
        }
        
        String suggestedAnnotation = getSuggestedAnnotation(firstFile, existingAnnotation);
        String initialValue = existingAnnotation != null ? existingAnnotation : 
                             (suggestedAnnotation != null ? suggestedAnnotation : "");
        
        // 获取首个文件的现有颜色
        String existingColor = null;
        String firstRelativePath = getRelativePath(project, firstFile);
        if (firstRelativePath != null) {
            if (firstFile.isDirectory()) {
                existingColor = annotationService.getPackageTextColor(firstRelativePath);
            } else {
                existingColor = annotationService.getFileTextColor(firstRelativePath);
            }
        }
        
        AddAnnotationDialog dialog = new AddAnnotationDialog(project, I18nUtils.getText(project, "批量文件", "Multiple Files"), initialValue, existingColor);
        
        if (dialog.showAndGet()) {
            String annotation = dialog.getAnnotation();
            String selectedColor = dialog.getSelectedColor();
            
            if (annotation.isEmpty()) {
                return;
            }
            

            
            for (VirtualFile file : files) {
                String relativePath = getRelativePath(project, file);
                if (relativePath != null) {
                    if (file.isDirectory()) {
                        annotationService.setPackageAnnotation(relativePath, annotation, selectedColor);
                    } else {
                        annotationService.setAnnotation(relativePath, annotation, selectedColor);
                    }
                }
            }
            
            // 保存后立即刷新VFS和项目视图（修复多选文件不刷新问题）
            try {
                java.lang.reflect.Method refreshMethod = annotationService.getClass().getDeclaredMethod("refreshAfterSave");
                refreshMethod.setAccessible(true);
                refreshMethod.invoke(annotationService);
            } catch (Exception ex) {
                // 如果反射失败，使用备用刷新方法
                ProjectView.getInstance(project).refresh();
            }
        }
    }
    
    private void handleSingleFile(@NotNull AnActionEvent e, @NotNull Project project) {
        VirtualFile file = getSelectedFile(e);
        if (file == null) {
            Messages.showWarningDialog(project, 
                I18nUtils.getText(project, "请选择一个文件或目录", "Please select a file or directory"), 
                I18nUtils.getText(project, "添加备注", "Add Annotation"));
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        String existingAnnotation = null;
        if (file.isDirectory()) {
            existingAnnotation = annotationService.getPackageAnnotation(file);
        } else {
            existingAnnotation = annotationService.getAnnotation(file);
        }
        
        String suggestedAnnotation = getSuggestedAnnotation(file, existingAnnotation);
        String initialValue = existingAnnotation != null ? existingAnnotation : 
                             (suggestedAnnotation != null ? suggestedAnnotation : "");
        
        // 获取现有颜色
        String existingColor = null;
        String relativePath = getRelativePath(project, file);
        if (relativePath != null) {
            if (file.isDirectory()) {
                existingColor = annotationService.getPackageTextColor(relativePath);
            } else {
                existingColor = annotationService.getFileTextColor(relativePath);
            }
        }
        
        AddAnnotationDialog dialog = new AddAnnotationDialog(project, file.getName(), initialValue, existingColor);
        
        if (dialog.showAndGet()) {
            String annotation = dialog.getAnnotation();
            String selectedColor = dialog.getSelectedColor();
            
            if (annotation.isEmpty()) {
                return;
            }
            
            if (relativePath != null) {
                if (file.isDirectory()) {
                    annotationService.setPackageAnnotationAndRefresh(relativePath, annotation, selectedColor);
                } else {
                    annotationService.setAnnotationAndRefresh(relativePath, annotation, selectedColor);
                }
            } else {
                if (file.isDirectory()) {
                    annotationService.setPackageAnnotationAndRefresh(file.getPath(), annotation, selectedColor);
                } else {
                    annotationService.setAnnotationAndRefresh(file.getPath(), annotation, selectedColor);
                }
            }
            
            // 不再需要手动刷新，setAnnotationAndRefresh已经处理了
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        // 采用宽松显示策略，始终显示菜单项
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
        
        // 根据当前语言动态显示菜单文本
        AnnotationService service = AnnotationService.getInstance(project);
        if (service != null) {
            String currentLanguage = service.getLanguage();
            if ("en".equals(currentLanguage)) {
                e.getPresentation().setText("Add Annotation");
                e.getPresentation().setDescription("Add user annotation to selected files");
            } else {
                e.getPresentation().setText("添加备注");
                e.getPresentation().setDescription("为选中文件添加用户备注");
            }
        } else {
            e.getPresentation().setText("添加备注");
            e.getPresentation().setDescription("为选中文件添加用户备注");
        }
    }
    
 
    private boolean isMultipleSelection(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        return files != null && files.length > 1;
    }
    

    @Nullable
    private VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        // 1. 优先从选中的文件数组获取
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length > 0) {
            return files[0];
        }
        
        // 2. 从PSI元素获取
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiFile) {
            return ((PsiFile) psiElement).getVirtualFile();
        } else if (psiElement instanceof PsiDirectory) {
            return ((PsiDirectory) psiElement).getVirtualFile();
        }
        
        // 3. 从单个虚拟文件获取
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return file;
        }
        
        // 4. 新增：从当前编辑器获取文件（解决光标在文件内的问题）
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            VirtualFile editorFile = getFileFromEditor(editor);
            if (editorFile != null) {
                return editorFile;
            }
        }
        
        // 5. 新增：从当前活动的编辑器获取文件
        Project project = e.getProject();
        if (project != null) {
            VirtualFile activeFile = getActiveFileFromProject(project);
            if (activeFile != null) {
                return activeFile;
            }
        }
        
        return null;
    }

    @Nullable
    private String getSuggestedAnnotation(@NotNull VirtualFile file, @Nullable String existingAnnotation) {
        if (existingAnnotation != null) {
            return null; // 已有备注时不提供建议
        }
        
        MappingLibraryService mappingService = MappingLibraryService.getInstance();
        return mappingService.smartSearch(file.getName());
    }
    
    /**
     * 从编辑器获取文件
     */
    @Nullable
    private VirtualFile getFileFromEditor(@NotNull Editor editor) {
        try {
            // 通过Document获取文件
            com.intellij.openapi.fileEditor.FileDocumentManager fileDocumentManager = 
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
            return fileDocumentManager.getFile(editor.getDocument());
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从项目获取当前活动的文件
     */
    @Nullable
    private VirtualFile getActiveFileFromProject(@NotNull Project project) {
        try {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            FileEditor[] editors = fileEditorManager.getSelectedEditors();
            
            for (FileEditor editor : editors) {
                if (editor instanceof TextEditor) {
                    TextEditor textEditor = (TextEditor) editor;
                    VirtualFile file = textEditor.getFile();
                    if (file != null) {
                        return file;
                    }
                }
            }
            
            // 如果没有选中的编辑器，尝试获取当前打开的文件
            VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
            if (openFiles.length > 0) {
                return openFiles[0];
            }
        } catch (Exception e) {
            // 忽略错误，返回null
        }
        
        return null;
    }
    
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
