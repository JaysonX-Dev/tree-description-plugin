package com.github.annotations.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地映射文件的数据结构
 * 与内置映射库格式完全统一
 */
public class LocalMappingFile {
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("version")
    private String version;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("author")
    private String author;
    

    
    @SerializedName("builtinMappingsEnabled")
    private Boolean builtinMappingsEnabled;
    
    @SerializedName("language")
    private String language;
    
    @SerializedName("mappings")
    private Mappings mappings;
    
    public LocalMappingFile() {
        this.mappings = new Mappings();
    }
    
    // Getters and Setters
    @Nullable
    public String getName() {
        return name;
    }
    
    public void setName(@Nullable String name) {
        this.name = name;
    }
    
    @Nullable
    public String getVersion() {
        return version;
    }
    
    public void setVersion(@Nullable String version) {
        this.version = version;
    }
    
    @Nullable
    public String getDescription() {
        return description;
    }
    
    public void setDescription(@Nullable String description) {
        this.description = description;
    }
    
    @Nullable
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(@Nullable String author) {
        this.author = author;
    }
    

    
    @Nullable
    public Boolean getBuiltinMappingsEnabled() {
        return builtinMappingsEnabled;
    }
    
    public void setBuiltinMappingsEnabled(@Nullable Boolean builtinMappingsEnabled) {
        this.builtinMappingsEnabled = builtinMappingsEnabled;
    }
    
    @Nullable
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(@Nullable String language) {
        this.language = language;
    }
    
    @NotNull
    public Mappings getMappings() {
        if (mappings == null) {
            mappings = new Mappings();
        }
        return mappings;
    }
    
    public void setMappings(@NotNull Mappings mappings) {
        this.mappings = mappings;
    }
    
    /**
     * 映射内容结构，与内置映射库完全一致
     */
    public static class Mappings {
        @SerializedName("packages")
        private Map<String, String> packages;
        
        @SerializedName("files")
        private Map<String, String> files;
        
        @SerializedName("packageMatch")
        private Map<String, String> packageMatch;
        
        @SerializedName("fileMatch")
        private Map<String, String> fileMatch;
        
        @SerializedName("packagesTextColor")
        private Map<String, String> packagesTextColor;
        
        @SerializedName("filesTextColor")
        private Map<String, String> filesTextColor;
        
        public Mappings() {
            this.packages = new HashMap<>();
            this.files = new HashMap<>();
            this.packageMatch = new HashMap<>();
            this.fileMatch = new HashMap<>();
            this.packagesTextColor = new HashMap<>();
            this.filesTextColor = new HashMap<>();
        }
        
        @NotNull
        public Map<String, String> getPackages() {
            if (packages == null) {
                packages = new HashMap<>();
            }
            return packages;
        }
        
        public void setPackages(@Nullable Map<String, String> packages) {
            this.packages = packages != null ? packages : new HashMap<>();
        }
        
        @NotNull
        public Map<String, String> getFiles() {
            if (files == null) {
                files = new HashMap<>();
            }
            return files;
        }
        
        public void setFiles(@Nullable Map<String, String> files) {
            this.files = files != null ? files : new HashMap<>();
        }
        
        @NotNull
        public Map<String, String> getPackageMatch() {
            if (packageMatch == null) {
                packageMatch = new HashMap<>();
            }
            return packageMatch;
        }
        
        public void setPackageMatch(@Nullable Map<String, String> packageMatch) {
            this.packageMatch = packageMatch != null ? packageMatch : new HashMap<>();
        }
        
        @NotNull
        public Map<String, String> getFileMatch() {
            if (fileMatch == null) {
                fileMatch = new HashMap<>();
            }
            return fileMatch;
        }
        
        public void setFileMatch(@Nullable Map<String, String> fileMatch) {
            this.fileMatch = fileMatch != null ? fileMatch : new HashMap<>();
        }
        
        @NotNull
        public Map<String, String> getPackagesTextColor() {
            if (packagesTextColor == null) {
                packagesTextColor = new HashMap<>();
            }
            return packagesTextColor;
        }
        
        public void setPackagesTextColor(@Nullable Map<String, String> packagesTextColor) {
            this.packagesTextColor = packagesTextColor != null ? packagesTextColor : new HashMap<>();
        }
        
        @NotNull
        public Map<String, String> getFilesTextColor() {
            if (filesTextColor == null) {
                filesTextColor = new HashMap<>();
            }
            return filesTextColor;
        }
        
        public void setFilesTextColor(@Nullable Map<String, String> filesTextColor) {
            this.filesTextColor = filesTextColor != null ? filesTextColor : new HashMap<>();
        }
    }
}
