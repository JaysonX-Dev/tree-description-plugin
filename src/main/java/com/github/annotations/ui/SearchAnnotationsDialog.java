package com.github.annotations.ui;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.services.MappingLibraryService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
// 使用固定颜色，不再依赖主题感知
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 备注搜索对话框
 * 提供全局搜索项目备注的功能
 */
public class SearchAnnotationsDialog extends DialogWrapper {
    
    private final Project project;
    private final AnnotationService annotationService;
    private final MappingLibraryService mappingLibraryService;
    
    private JBTextField searchField;
    private JBList<SearchResult> resultsList;
    private DefaultListModel<SearchResult> listModel;
    private JLabel statusLabel;
    
    public SearchAnnotationsDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        this.annotationService = AnnotationService.getInstance(project);
        this.mappingLibraryService = MappingLibraryService.getInstance();
        
        setTitle(I18nUtils.getText(project, "搜索项目树备注", "Search Project Tree Annotations"));
        setSize(1000, 700);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // 搜索输入框
        JPanel searchPanel = createSearchPanel();
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        
        // 结果列表
        JPanel resultsPanel = createResultsPanel();
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        
        // 状态栏
        statusLabel = new JLabel(I18nUtils.getText(project, "请输入搜索关键词", "Please enter search keywords"));
        statusLabel.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel label = new JLabel(I18nUtils.getText(project, "搜索关键词:", "Search Keywords:"));
        label.setBorder(JBUI.Borders.empty(0, 0, 5, 5));
        
        searchField = new JBTextField();
        searchField.setToolTipText(I18nUtils.getText(project, "输入关键词搜索项目树备注内容", "Enter keywords to search project tree annotations"));
        
        // 添加实时搜索监听器
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { performSearch(); }
            
            @Override
            public void removeUpdate(DocumentEvent e) { performSearch(); }
            
            @Override
            public void changedUpdate(DocumentEvent e) { performSearch(); }
        });
        
        panel.add(label, BorderLayout.WEST);
        panel.add(searchField, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel resultsLabel = new JLabel(I18nUtils.getText(project, "搜索结果:", "Search Results:"));
        resultsLabel.setBorder(JBUI.Borders.empty(10, 0, 5, 0));
        
        listModel = new DefaultListModel<>();
        resultsList = new JBList<>(listModel);
        resultsList.setCellRenderer(new SearchResultCellRenderer());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // 双击跳转到文件
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedFile();
                }
            }
        });
        
        // 回车键跳转到文件
        resultsList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateToSelectedFile();
                }
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(resultsList);
        scrollPane.setPreferredSize(new Dimension(800, 400));
        
        panel.add(resultsLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void performSearch() {
        String keyword = searchField.getText().trim();
        listModel.clear();
        
        if (keyword.isEmpty()) {
            statusLabel.setText("请输入搜索关键词");
            return;
        }
        
        List<SearchResult> results = new ArrayList<>();
        Set<String> userAnnotatedPaths = new HashSet<>(); // 记录已有用户备注的文件路径
        
        // 1. 搜索用户自定义备注（files）
        Map<String, String> userAnnotations = annotationService.getAllAnnotations();
        for (Map.Entry<String, String> entry : userAnnotations.entrySet()) {
            String filePath = entry.getKey();
            String annotation = entry.getValue();
            
            // 检查备注内容是否包含关键词（忽略大小写）
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                                                SearchResult result = new SearchResult(filePath, annotation, keyword, "用户文件映射");
                results.add(result);
                userAnnotatedPaths.add(filePath); // 记录此路径已有用户备注
            }
        }
        
        // 2. 搜索用户自定义包备注（packages）
        Map<String, String> userPackageAnnotations = annotationService.getAllPackageAnnotations();
        for (Map.Entry<String, String> entry : userPackageAnnotations.entrySet()) {
            String packagePath = entry.getKey();
            String annotation = entry.getValue();
            
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                SearchResult result = new SearchResult(packagePath, annotation, keyword, "用户包映射");
                results.add(result);
                userAnnotatedPaths.add(packagePath);
            }
        }
        
        // 3. 搜索用户自定义文件匹配模式匹配的文件
        Map<String, String> userFileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
        for (Map.Entry<String, String> entry : userFileMatchAnnotations.entrySet()) {
            String pattern = entry.getKey();
            String annotation = entry.getValue();
            
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                // 找到所有匹配该pattern的文件
                List<String> matchedFiles = findFilesMatchingPattern(pattern);
                for (String filePath : matchedFiles) {
                    if (!userAnnotatedPaths.contains(filePath)) {
                        SearchResult result = new SearchResult(filePath, annotation, keyword, "用户文件匹配映射");
                        results.add(result);
                        userAnnotatedPaths.add(filePath);
                    }
                }
            }
        }
        
        // 4. 搜索用户自定义包匹配模式匹配的包
        Map<String, String> userPackageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
        for (Map.Entry<String, String> entry : userPackageMatchAnnotations.entrySet()) {
            String pattern = entry.getKey();
            String annotation = entry.getValue();
            
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                // 找到所有匹配该pattern的包
                List<String> matchedPackages = findPackagesMatchingPattern(pattern);
                for (String packagePath : matchedPackages) {
                    if (!userAnnotatedPaths.contains(packagePath)) {
                        SearchResult result = new SearchResult(packagePath, annotation, keyword, "用户包匹配映射");
                        results.add(result);
                        userAnnotatedPaths.add(packagePath);
                    }
                }
            }
        }
        
        // 5. 搜索内置映射库的files和packages（只搜索实际文件和包，不搜索模式匹配）
        // 只有在启用内置映射库时才搜索
        if (mappingLibraryService.isBuiltinMappingsEnabled()) {
            Map<String, String> builtinMappings = mappingLibraryService.getAllMappings();
            for (Map.Entry<String, String> entry : builtinMappings.entrySet()) {
                String keyName = entry.getKey();
                String annotation = entry.getValue();
                
                // 检查备注内容是否包含关键词（忽略大小写）
                if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                    // 判断是文件名还是包名
                    if (isFileName(keyName)) {
                        // 对于文件名，搜索项目中所有匹配的完整路径
                        List<String> allPaths = findAllFilesInProject(keyName);
                        if (!allPaths.isEmpty()) {
                            for (String fullPath : allPaths) {
                                // 只有当该文件路径没有用户备注时，才添加内置映射结果
                                if (!userAnnotatedPaths.contains(fullPath)) {
                                    // 再次验证文件确实存在
                                    if (fileExistsInProject(fullPath)) {
                                        SearchResult result = new SearchResult(fullPath, annotation, keyword, "内置文件映射");
                                        results.add(result);
                                    }
                                }
                            }
                        } else {
                            // 如果没找到匹配的文件，但该映射确实存在，则添加一个通用的结果
                            if (fileExistsInProject(keyName)) {
                                if (!userAnnotatedPaths.contains(keyName)) {
                                    SearchResult result = new SearchResult(keyName, annotation, keyword, "内置文件映射");
                                    results.add(result);
                                }
                            }
                        }
                    } else {
                        // 对于包名，验证包路径是否真实存在于项目中
                        if (!userAnnotatedPaths.contains(keyName) && packageExistsInProject(keyName)) {
                            SearchResult result = new SearchResult(keyName, annotation, keyword, "内置包映射");
                            results.add(result);
                            userAnnotatedPaths.add(keyName);
                        }
                    }
                }
            }
        }
        
        // 6. 搜索内置映射库的fileMatch模式匹配的文件
        // 只有在启用内置映射库时才搜索
        if (mappingLibraryService.isBuiltinMappingsEnabled()) {
            Map<String, String> builtinFileMatchPatterns = mappingLibraryService.getAllFileMatchPatterns();
            for (Map.Entry<String, String> entry : builtinFileMatchPatterns.entrySet()) {
                String pattern = entry.getKey();
                String annotation = entry.getValue();
                
                if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                    // 找到所有匹配该pattern的文件
                    List<String> matchedFiles = findFilesMatchingPattern(pattern);
                    for (String filePath : matchedFiles) {
                        if (!userAnnotatedPaths.contains(filePath)) {
                            SearchResult result = new SearchResult(filePath, annotation, keyword, "内置文件匹配映射");
                            results.add(result);
                            userAnnotatedPaths.add(filePath);
                        }
                    }
                }
            }
        }
        
        // 7. 搜索内置映射库的packageMatch模式匹配的包
        // 只有在启用内置映射库时才搜索
        if (mappingLibraryService.isBuiltinMappingsEnabled()) {
            Map<String, String> builtinPackageMatchPatterns = mappingLibraryService.getAllPackageMatchPatterns();
            for (Map.Entry<String, String> entry : builtinPackageMatchPatterns.entrySet()) {
                String pattern = entry.getKey();
                String annotation = entry.getValue();
                
                if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                    // 找到所有匹配该pattern的包
                    List<String> matchedPackages = findPackagesMatchingPattern(pattern);
                    for (String packagePath : matchedPackages) {
                        if (!userAnnotatedPaths.contains(packagePath)) {
                            SearchResult result = new SearchResult(packagePath, annotation, keyword, "内置包匹配映射");
                            results.add(result);
                            userAnnotatedPaths.add(packagePath);
                        }
                    }
                }
            }
        }
        
        // 按类型和文件路径排序 - 用户映射优先
        results.sort((a, b) -> {
            // 先按类型排序：用户文件映射 > 用户包映射 > 用户文件匹配映射 > 用户包匹配映射 > 内置文件映射 > 内置包映射 > 内置文件匹配映射 > 内置包匹配映射
            String[] priorityOrder = {"用户文件映射", "用户包映射", "用户文件匹配映射", "用户包匹配映射", "内置文件映射", "内置包映射", "内置文件匹配映射", "内置包匹配映射"};
            int aPriority = getPriority(a.source, priorityOrder);
            int bPriority = getPriority(b.source, priorityOrder);
            
            if (aPriority != bPriority) {
                return Integer.compare(aPriority, bPriority);
            }
            
            // 同类型的按文件路径排序
            return a.filePath.compareToIgnoreCase(b.filePath);
        });
        
        // 添加到列表
        for (SearchResult result : results) {
            listModel.addElement(result);
        }
        
        // 更新状态
        if (results.isEmpty()) {
            statusLabel.setText(I18nUtils.getText(project, 
                "未找到包含 \"" + keyword + "\" 的备注",
                "No annotations found containing \"" + keyword + "\""));
        } else {
            long userCount = results.stream().filter(r -> r.source.startsWith("用户")).count();
            statusLabel.setText(I18nUtils.getText(project,
                "找到 " + results.size() + " 个匹配的备注",
                "Found " + results.size() + " matching annotations"));
        }
    }
    
    /**
     * 获取优先级顺序
     */
    private int getPriority(String source, String[] priorityOrder) {
        for (int i = 0; i < priorityOrder.length; i++) {
            if (source.equals(priorityOrder[i])) {
                return i;
            }
        }
        return priorityOrder.length; // 未知类型排在最后
    }
    
    /**
     * 查找匹配指定pattern的所有文件
     */
    private List<String> findFilesMatchingPattern(String pattern) {
        List<String> matchedFiles = new ArrayList<>();
        
        try {
            String projectPath = project.getBasePath();
            if (projectPath == null) return matchedFiles;
            
            VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
            if (projectRoot == null) return matchedFiles;
            
            // 递归搜索项目中的所有文件
            findFilesMatchingPatternRecursive(projectRoot, pattern, matchedFiles);
        } catch (Exception e) {
            // 忽略异常，返回空列表
        }
        
        return matchedFiles;
    }
    
    /**
     * 递归搜索匹配pattern的文件
     */
    private void findFilesMatchingPatternRecursive(VirtualFile directory, String pattern, List<String> matchedFiles) {
        if (directory == null || !directory.isDirectory()) return;
        
        try {
            for (VirtualFile child : directory.getChildren()) {
                if (child.isDirectory()) {
                    // 递归搜索子目录
                    findFilesMatchingPatternRecursive(child, pattern, matchedFiles);
                } else {
                    // 检查文件名是否匹配pattern - 使用完全匹配而不是包含匹配
                    String fileName = child.getName();
                    if (fileName.equalsIgnoreCase(pattern)) {
                        // 获取相对于项目根目录的路径
                        String projectPath = project.getBasePath();
                        if (projectPath != null) {
                            VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
                            if (projectRoot != null) {
                                String relativePath = getRelativePath(projectRoot, child);
                                if (relativePath != null) {
                                    matchedFiles.add(relativePath);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略异常，继续搜索
        }
    }
    
    /**
     * 查找匹配指定pattern的所有包
     */
    private List<String> findPackagesMatchingPattern(String pattern) {
        List<String> matchedPackages = new ArrayList<>();
        
        try {
            String projectPath = project.getBasePath();
            if (projectPath == null) return matchedPackages;
            
            VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
            if (projectRoot == null) return matchedPackages;
            
            // 递归搜索项目中的所有目录
            findPackagesMatchingPatternRecursive(projectRoot, pattern, matchedPackages);
        } catch (Exception e) {
            // 忽略异常，返回空列表
        }
        
        return matchedPackages;
    }
    
    /**
     * 递归搜索匹配pattern的包
     */
    private void findPackagesMatchingPatternRecursive(VirtualFile directory, String pattern, List<String> matchedPackages) {
        if (directory == null || !directory.isDirectory()) return;
        
        try {
            for (VirtualFile child : directory.getChildren()) {
                if (child.isDirectory()) {
                    // 检查目录名是否匹配pattern - 使用完全匹配而不是包含匹配
                    String dirName = child.getName();
                    if (dirName.equalsIgnoreCase(pattern)) {
                        // 获取相对于项目根目录的路径
                        String projectPath = project.getBasePath();
                        if (projectPath != null) {
                            VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
                            if (projectRoot != null) {
                                String relativePath = getRelativePath(projectRoot, child);
                                if (relativePath != null) {
                                    matchedPackages.add(relativePath);
                                }
                            }
                        }
                    }
                    
                    // 递归搜索子目录
                    findPackagesMatchingPatternRecursive(child, pattern, matchedPackages);
                }
            }
        } catch (Exception e) {
            // 忽略异常，继续搜索
        }
    }
    
    /**
     * 获取文件相对于项目根目录的路径
     */
    private String getRelativePath(VirtualFile baseDir, VirtualFile file) {
        try {
            String basePath = baseDir.getPath();
            String filePath = file.getPath();
            
            if (filePath.startsWith(basePath)) {
                String relativePath = filePath.substring(basePath.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                return relativePath;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }
    
    /**
     * 判断是否为文件名（而不是包名）
     */
    private boolean isFileName(String name) {
        // 包含文件扩展名的肯定是文件
        if (name.contains(".") && (name.endsWith(".xml") || name.endsWith(".yml") || name.endsWith(".yaml") || 
            name.endsWith(".properties") || name.endsWith(".json") || name.endsWith(".gradle") || 
            name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".gitignore") || 
            name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".js") || name.endsWith(".ts"))) {
            return true;
        }
        
        // 特殊文件名（无扩展名）
        if (name.equals(".gitignore") || name.equals("Dockerfile") || name.equals("Jenkinsfile") || 
            name.equals("Makefile") || name.equals("README")) {
            return true;
        }
        
        // 包含多个点的通常是包名（如 org.springframework.boot）
        if (name.contains(".") && name.split("\\.").length > 2) {
            return false;
        }
        

        return false;
    }
    
    /**
     * 检查文件是否存在于当前项目中
     */
    private boolean fileExistsInProject(String relativePath) {
        try {
            String fullPath = project.getBasePath() + "/" + relativePath;
            VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + fullPath);
            return file != null && file.exists();
        } catch (Exception ex) {
            return false;
        }
    }
    
    /**
     * 检查包路径是否存在于项目中
     * 将包名转换为文件夹路径进行检查，例如：org.springframework.boot -> org/springframework/boot
     * 性能优化：减少文件系统调用
     */
    private boolean packageExistsInProject(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        
        try {
            // 将包名转换为路径格式
            String packagePath = packageName.replace(".", "/");
            String projectBasePath = project.getBasePath();
            if (projectBasePath == null) {
                return false;
            }
            
            // 检查常见的源码目录下是否存在该包路径（按概率排序）
            String[] basePaths = {
                "src/main/java/" + packagePath,  // 最常见
                "src/" + packagePath,            // 简化结构
                "src/test/java/" + packagePath,
                packagePath                      // 根目录
            };
            
            VirtualFileManager vfm = VirtualFileManager.getInstance();
            for (String basePath : basePaths) {
                VirtualFile file = vfm.findFileByUrl("file://" + projectBasePath + "/" + basePath);
                if (file != null && file.exists() && file.isDirectory()) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
    
    /**
     * 在项目中递归搜索文件
     */
    private boolean searchFileInProject(VirtualFile directory, String fileName) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        
        try {
            for (VirtualFile child : directory.getChildren()) {
                if (child.getName().equals(fileName)) {
                    return true;
                }
                if (child.isDirectory() && searchFileInProject(child, fileName)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            // 忽略异常，继续搜索
        }
        
        return false;
    }
    
    /**
     * 在项目中搜索文件并返回完整的相对路径
     */
    private String findFileInProject(String fileName) {
        try {
            // 首先检查根目录
            String rootPath = fileName;
            if (fileExistsInProject(rootPath)) {
                return rootPath;
            }
            
            // 递归搜索整个项目，返回第一个匹配的相对路径
            String projectPath = project.getBasePath();
            if (projectPath == null) return null;
            VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
            if (projectRoot == null) return null;
            return searchFilePathInProject(projectRoot, fileName, "");
        } catch (Exception ex) {
            return null;
        }
    }
    
    /**
     * 在项目中搜索所有匹配的文件并返回完整的相对路径列表
     */
    private List<String> findAllFilesInProject(String fileName) {
        Set<String> allPaths = new LinkedHashSet<>();
        try {
            String projectPath = project.getBasePath();
            if (projectPath == null) return new ArrayList<>();
            VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
            if (projectRoot == null) return new ArrayList<>();
            searchAllFilePathsInProject(projectRoot, fileName, "", allPaths);
        } catch (Exception ex) {
            // 静默处理异常
        }
        return new ArrayList<>(allPaths);
    }
    
    /**
     * 递归搜索文件并返回相对路径
     */
    private String searchFilePathInProject(VirtualFile directory, String fileName, String currentPath) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        
        try {
            for (VirtualFile child : directory.getChildren()) {
                String childPath = currentPath.isEmpty() ? child.getName() : currentPath + "/" + child.getName();
                
                if (child.getName().equals(fileName)) {
                    return childPath;
                }
                
                if (child.isDirectory()) {
                    String result = searchFilePathInProject(child, fileName, childPath);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } catch (Exception ex) {
            // 忽略异常，继续搜索
        }
        
        return null;
    }
    
    /**
     * 递归搜索所有匹配的文件并收集路径
     */
    private void searchAllFilePathsInProject(VirtualFile directory, String fileName, String currentPath, Set<String> results) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        
        try {
            for (VirtualFile child : directory.getChildren()) {
                String childPath = currentPath.isEmpty() ? child.getName() : currentPath + "/" + child.getName();
                
                if (child.getName().equals(fileName)) {
                    results.add(childPath);
                }
                
                if (child.isDirectory()) {
                    searchAllFilePathsInProject(child, fileName, childPath, results);
                }
            }
        } catch (Exception ex) {
            // 静默处理异常
        }
    }
    
    private void navigateToSelectedFile() {
        SearchResult selected = resultsList.getSelectedValue();
        if (selected == null) return;
        
        try {
            // 构建完整文件路径
            String fullPath = project.getBasePath() + "/" + selected.filePath;
            VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + fullPath);
            
            if (file != null && file.exists()) {
                // 在编辑器中打开文件
                FileEditorManager.getInstance(project).openFile(file, true);
                
                // 在Project View中选中文件
                ProjectView.getInstance(project).select(null, file, true);
                
                // 关闭对话框
                close(OK_EXIT_CODE);
            } else {
                statusLabel.setText("文件不存在: " + selected.filePath);
            }
        } catch (Exception ex) {
            statusLabel.setText("无法打开文件: " + ex.getMessage());
        }
    }
    
    @Override
    protected @NotNull Action[] createActions() {
        return new Action[]{
            new AbstractAction(I18nUtils.getText(project, "跳转到选中结果", "Navigate to Selected Result")) {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    navigateToSelectedFile();
                }
            },
            getCancelAction()
        };
    }
    
    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return searchField;
    }
    
    /**
     * 搜索结果数据类
     */
    public static class SearchResult {
        public final String filePath;
        public final String annotation;
        public final String keyword;
        public final String source; // "用户备注" 或 "内置映射"
        
        public SearchResult(String filePath, String annotation, String keyword, String source) {
            this.filePath = filePath;
            this.annotation = annotation;
            this.keyword = keyword;
            this.source = source;
        }
        
        @Override
        public String toString() {
            return filePath + " - " + annotation + " [" + source + "]";
        }
    }
    
    /**
     * 搜索结果渲染器 - 高亮关键词
     */
    private class SearchResultCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof SearchResult) {
                SearchResult result = (SearchResult) value;
                
                // 根据是否选中状态和当前主题决定文字颜色
                String textColor = isSelected ? "#FFFFFF" : getUnselectedTextColor(); // 选中时白色，未选中时根据主题调整
                String annotationColor = isSelected ? "#FFFFFF" : getUnselectedAnnotationColor(); // 选中时白色，未选中时根据主题调整
                
                // 创建HTML格式的文本，高亮关键词
                String highlightedText = highlightKeyword(result.annotation, result.keyword, isSelected);
                
                // 根据来源设置不同的颜色 - 使用固定颜色方案
                String sourceColor = "#6088F0"; // 与路径颜色一致
                
                // 选中状态下，标签颜色调整为白色以保持清晰；未选中状态下使用灰色
                String labelColor = isSelected ? "#FFFFFF" : "#787A7C";
                
                String displayText = "<html>" +
                    "<b style='color: " + textColor + ";'>" + result.filePath + "</b><br/>" +
                    "<span style='color: " + annotationColor + ";'>" + highlightedText + "</span>" +
                    "</html>";
                
                setText(displayText);
                setToolTipText(I18nUtils.getText(project, 
                    "双击跳转到: " + result.filePath + " (" + result.source + ")",
                    "Double-click to navigate to: " + result.filePath + " (" + result.source + ")"));
            }
            
            return this;
        }
        
        private String highlightKeyword(String text, String keyword, boolean isSelected) {
            if (keyword.isEmpty()) return text;
            
            // 根据选中状态决定高亮样式
            String highlightStyle;
            if (isSelected) {
                // 选中状态下：黄色背景 + 黑色粗体文字（在蓝色选中行上更清晰）
                highlightStyle = "background-color: #FFD700; color: #000000; font-weight: bold;";
            } else {
                // 未选中状态下：浅黄色背景 + 黑色粗体文字
                highlightStyle = "background-color: #FFFF99; color: #000000; font-weight: bold;";
            }
            
            String pattern = "(?i)" + java.util.regex.Pattern.quote(keyword);
            return text.replaceAll(pattern, "<span style='" + highlightStyle + "'>$0</span>");
        }
    }
    
    /**
     * 将Color对象转换为十六进制字符串
     */
    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
        /**
     * 获取未选中状态下的文件路径文字颜色（固定颜色方案）
     */
    private static String getUnselectedTextColor() {
        // 使用固定的蓝色，确保在各种背景下都清晰可见
        return "#6088F0"; // 蓝色
    }

    /**
     * 获取未选中状态下的备注文字颜色（固定颜色方案）
     */
    private static String getUnselectedAnnotationColor() {
        // 使用固定的灰色，确保备注文字清晰易读
        return "#787A7C"; // 灰色
    }
}
