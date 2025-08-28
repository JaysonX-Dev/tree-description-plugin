package com.github.annotations.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * GitHub开源映射库服务
 * 负责从GitHub获取开源映射库信息
 */
public class GitHubMappingService {
    
    private static final Logger LOG = Logger.getInstance(GitHubMappingService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    // 多镜像源配置
    private static final String[] MIRROR_URLS = {
        "https://raw.githubusercontent.com",
        "https://hub.gitmirror.com/raw.githubusercontent.com",
        "https://cdn.statically.io"
    };
    
    private static final String REPO_OWNER = "JaysonX-Tech";
    private static final String REPO_NAME = "tree-description-repository";
    private static final String BRANCH = "main";
    
    private final Gson gson = new Gson();
    
    /**
     * 创建带代理的HTTP连接
     */
    private HttpURLConnection createProxyConnection(URL url) throws IOException {
        // 根据URL协议选择合适的代理设置
        String protocol = url.getProtocol().toLowerCase();
        String proxyHost = null;
        String proxyPort = null;
        
        if ("https".equals(protocol)) {
            proxyHost = System.getProperty("https.proxyHost");
            proxyPort = System.getProperty("https.proxyPort");
        } else if ("http".equals(protocol)) {
            proxyHost = System.getProperty("http.proxyHost");
            proxyPort = System.getProperty("http.proxyPort");
        }
        
        if (proxyHost != null && proxyPort != null) {
            try {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, 
                    new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
                LOG.info("使用" + protocol.toUpperCase() + "代理连接: " + proxyHost + ":" + proxyPort + " for " + url);
                return (HttpURLConnection) url.openConnection(proxy);
            } catch (NumberFormatException e) {
                LOG.warn("代理端口配置错误，使用直连: " + e.getMessage());
            }
        } else {
            LOG.info("未找到" + protocol.toUpperCase() + "代理配置，使用直连: " + url);
        }
        
        // 如果没有代理配置或配置错误，使用直连
        return (HttpURLConnection) url.openConnection();
    }
    
    /**
     * 获取仓库内容列表
     */
    @NotNull
    public List<GitHubMappingFile> getRepositoryContents() {
        List<GitHubMappingFile> files = new ArrayList<>();
        
        try {
            String apiUrl = String.format("%s/repos/%s/%s/contents", GITHUB_API_BASE, REPO_OWNER, REPO_NAME);
            LOG.info("正在请求GitHub API: " + apiUrl);
            
    
            testSimpleConnection(apiUrl);
            
            String response = makeGitHubApiRequest(apiUrl);
            
            if (response != null) {
                LOG.info("GitHub API响应长度: " + response.length());
                JsonArray contents = gson.fromJson(response, JsonArray.class);
                LOG.info("解析到 " + contents.size() + " 个项目");
                
                for (JsonElement element : contents) {
                    JsonObject item = element.getAsJsonObject();
                    String path = item.get("path").getAsString();
                    String type = item.get("type").getAsString();
                    LOG.info("处理项目: " + path + ", 类型: " + type);
                    
                    if ("file".equals(type) && path.endsWith(".json")) {
                        String displayName = getFileNameFromPath(path);
                        files.add(new GitHubMappingFile(path, displayName, false));
                        LOG.info("添加JSON文件: " + path + " -> " + displayName);
                    } else if ("dir".equals(type)) {
                        List<GitHubMappingFile> subFiles = getDirectoryContents(path);
                        files.addAll(subFiles);
                        LOG.info("添加目录 " + path + " 中的 " + subFiles.size() + " 个文件");
                    }
                }
                LOG.info("最终找到 " + files.size() + " 个映射文件");
            } else {
                LOG.warn("GitHub API响应为空");
            }
        } catch (IOException | JsonSyntaxException e) {
            LOG.error("获取GitHub仓库内容失败", e);
        }
        
        return files;
    }
    
    /**
     * 简单的HTTP连接
     */
    private void testSimpleConnection(String urlString) {
        try {
            LOG.info("开始简单连接测试: " + urlString);
            URL url = URI.create(urlString).toURL();
            
            HttpURLConnection directConnection = (HttpURLConnection) url.openConnection();
            directConnection.setRequestMethod("HEAD");
            directConnection.setConnectTimeout(5000);
            directConnection.setReadTimeout(5000);
            directConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = directConnection.getResponseCode();
            LOG.info("直连测试 - 响应码: " + responseCode);
            directConnection.disconnect();
            
            HttpURLConnection proxyConnection = createProxyConnection(url);
            proxyConnection.setRequestMethod("HEAD");
            proxyConnection.setConnectTimeout(5000);
            proxyConnection.setReadTimeout(5000);
            proxyConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int proxyResponseCode = proxyConnection.getResponseCode();
            LOG.info("代理连接测试 - 响应码: " + proxyResponseCode);
            proxyConnection.disconnect();
            
        } catch (Exception e) {
            LOG.warn("连接测试失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取目录内容
     */
    @NotNull
    private List<GitHubMappingFile> getDirectoryContents(String dirPath) {
        List<GitHubMappingFile> files = new ArrayList<>();
        
        try {
            String apiUrl = String.format("%s/repos/%s/%s/contents/%s", 
                GITHUB_API_BASE, REPO_OWNER, REPO_NAME, dirPath);
            String response = makeGitHubApiRequest(apiUrl);
            
            if (response != null) {
                JsonArray contents = gson.fromJson(response, JsonArray.class);
                for (JsonElement element : contents) {
                    JsonObject item = element.getAsJsonObject();
                    String path = item.get("path").getAsString();
                    String type = item.get("type").getAsString();
                    
                    if ("file".equals(type) && path.endsWith(".json")) {
                        String displayName = getFileNameFromPath(path);
                        files.add(new GitHubMappingFile(path, displayName, false));
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LOG.error("获取GitHub目录内容失败: " + dirPath, e);
        }
        
        return files;
    }
    
    /**
     * 下载映射库文件内容 - 使用多镜像源轮询重试
     */
    @Nullable
    public String downloadMappingFile(String filePath) {
        try {
            return downloadWithMultipleMirrors(filePath);
        } catch (IOException e) {
            LOG.error("下载GitHub映射库文件失败: " + filePath, e);
            return null;
        }
    }
    
    /**
     * 使用串行请求多镜像源下载文件
     */
    @Nullable
    private String downloadWithMultipleMirrors(String filePath) throws IOException {
        LOG.info("开始串行请求镜像源下载文件: " + filePath);
        
        // 依次尝试每个镜像源
        for (String mirrorUrl : MIRROR_URLS) {
            try {
                String fullUrl = buildMirrorUrl(mirrorUrl, filePath);
                LOG.info("尝试镜像源: " + mirrorUrl + ", URL: " + fullUrl);
                
                String content = makeRawContentRequest(fullUrl);
                if (content != null) {
                    LOG.info("镜像源" + mirrorUrl + "成功下载文件: " + filePath);
                    return content;
                }
                
            } catch (Exception e) {
                LOG.warn("镜像源" + mirrorUrl + "请求失败: " + e.getMessage());
                // 继续尝试下一个镜像源
            }
        }
        
        LOG.error("所有镜像源串行请求都失败，无法下载文件: " + filePath);
        return null;
    }
    
    /**
     * 解析映射库文件内容
     */
    @Nullable
    public GitHubMappingLibrary parseMappingFile(String jsonContent) {
        try {
            return gson.fromJson(jsonContent, GitHubMappingLibrary.class);
        } catch (Exception e) {
            LOG.error("解析GitHub映射库文件失败", e);
            return null;
        }
    }
    
    /**
     * 发送GitHub API请求 - 优化版本
     */
    @Nullable
    private String makeGitHubApiRequest(String urlString) throws IOException {
        LOG.info("发送GitHub API请求: " + urlString);
        
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = createProxyConnection(url);
        
        try {
            // 基本请求配置
            connection.setRequestMethod("GET");
            
            // 使用标准浏览器 User-Agent
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            // GitHub API 需要 JSON 格式响应
            connection.setRequestProperty("Accept", "application/json");
            
            // 优化的超时时间
            connection.setConnectTimeout(5000);  // 5秒连接超时
            connection.setReadTimeout(15000);     // 15秒读取超时
            
            // 禁用缓存以获取最新内容
            connection.setUseCaches(false);
            
            LOG.info("尝试连接GitHub API...");
            
            int responseCode = connection.getResponseCode();
            LOG.info("GitHub API响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    LOG.info("GitHub API请求成功，响应长度: " + response.length());
                    return response.toString();
                }
            } else if (responseCode == 403) {
                // 处理GitHub API速率限制
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    String errorMsg = errorResponse.toString();
                    if (errorMsg.contains("rate limit exceeded")) {
                        LOG.warn("GitHub API速率限制: " + errorMsg);
                        throw new IOException("GitHub API速率限制已达到，请稍后再试或使用GitHub Token进行认证");
                    } else {
                        LOG.warn("GitHub API访问被拒绝: " + errorMsg);
                        throw new IOException("GitHub API访问被拒绝: " + responseCode);
                    }
                }
            } else {
                LOG.warn("GitHub API请求失败，响应码: " + responseCode + ", URL: " + urlString);
                throw new IOException("GitHub API请求失败，响应码: " + responseCode);
            }
            
        } catch (java.net.SocketTimeoutException e) {
            LOG.warn("GitHub API连接超时: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            LOG.warn("GitHub API网络错误: " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * 发送Raw Content请求 - 优化版本
     */
    @Nullable
    private String makeRawContentRequest(String urlString) throws IOException {
        LOG.info("发送Raw Content请求: " + urlString);
        
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = createProxyConnection(url);
        
        try {
            // 基本请求配置
            connection.setRequestMethod("GET");
            
            // 使用标准浏览器 User-Agent
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            // 简化的请求头，只保留必要的
            connection.setRequestProperty("Accept", "*/*");
            
            // 调整超时时间以适应网络延迟
            connection.setConnectTimeout(5000);   // 5秒连接超时
            connection.setReadTimeout(15000);     // 15秒读取超时
            
            // 禁用缓存以获取最新内容
            connection.setUseCaches(false);
            
            LOG.info("尝试连接Raw Content...");
            
            int responseCode = connection.getResponseCode();
            LOG.info("Raw Content响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    LOG.info("Raw Content请求成功，响应长度: " + response.length());
                    return response.toString();
                }
            } else {
                LOG.warn("Raw Content请求失败，响应码: " + responseCode + ", URL: " + urlString);
            }
            
        } catch (java.net.SocketTimeoutException e) {
            LOG.warn("Raw Content连接超时: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            LOG.warn("Raw Content网络错误: " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
        
        return null;
    }
    
    /**
     * 构建镜像源URL
     */
    private String buildMirrorUrl(String mirrorUrl, String filePath) {
        if (mirrorUrl.contains("cdn.statically.io")) {
            // cdn.statically.io 需要使用 /gh/ 前缀
            return String.format("%s/gh/%s/%s/%s/%s", 
                mirrorUrl, REPO_OWNER, REPO_NAME, BRANCH, filePath);
        } else {
            // raw.githubusercontent.com 和 raw.githack.com 使用标准格式
            return String.format("%s/%s/%s/%s/%s", 
                mirrorUrl, REPO_OWNER, REPO_NAME, BRANCH, filePath);
        }
    }
    
    /**
     * 从完整路径中提取文件名
     */
    private String getFileNameFromPath(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return path;
        }
        return path.substring(lastSlashIndex + 1);
    }
    
    /**
     * GitHub映射库文件信息
     */
    public static class GitHubMappingFile {
        public final String path;
        public final String displayName;
        public final boolean isDirectory;
        
        public GitHubMappingFile(String path, String displayName, boolean isDirectory) {
            this.path = path;
            this.displayName = displayName;
            this.isDirectory = isDirectory;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * GitHub映射库数据结构
     */
    public static class GitHubMappingLibrary {
        public String name;
        public String version;
        public String description;
        public String author;
        public String[] tags;
        public String lastUpdated;
        public Mappings mappings;
        
        public static class Mappings {
            public java.util.Map<String, String> packages;
            public java.util.Map<String, String> files;
            public java.util.Map<String, String> packageMatch;
            public java.util.Map<String, String> fileMatch;
        }
    }
}
