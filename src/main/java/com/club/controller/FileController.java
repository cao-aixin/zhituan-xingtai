package com.club.controller;

import com.club.util.BizException;
import com.club.util.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传接口（本地存储简化实现，不接OSS）
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Value("${club.upload-dir}")
    private String uploadDir;

    /** 允许上传的文件扩展名白名单 */
    private static final List<String> ALLOWED = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".txt");

    /** 图片/文件上传，返回可访问URL：/file/xxx */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        // 简单白名单校验，防止上传可执行脚本
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new BizException("不支持的文件类型: " + ext);
        }
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            file.transferTo(dir.resolve(fileName).toAbsolutePath().toFile());
            return Result.ok(Map.of("fileName", original, "url", "/file/" + fileName));
        } catch (IOException e) {
            throw new BizException("文件上传失败，请稍后重试");
        }
    }
}
