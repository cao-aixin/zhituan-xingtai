package com.club.controller;

import com.club.dto.ResourceFileVO;
import com.club.service.ResourceFileService;
import com.club.util.Result;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 资料库接口：上传 / 列表 / 下载 / 删除（移植自旧版 zhituan-system）
 */
@RestController
@RequestMapping("/api/resource")
public class ResourceFileController {

    private final ResourceFileService resourceFileService;

    public ResourceFileController(ResourceFileService resourceFileService) {
        this.resourceFileService = resourceFileService;
    }

    /** 上传资料：clubId + category(策划/总结/照片/预算/其他) + 可选标题 + 文件 */
    @PostMapping("/upload")
    public Result<Void> upload(@RequestParam Long clubId,
                               @RequestParam String category,
                               @RequestParam(required = false) String title,
                               @RequestParam("file") MultipartFile file) {
        resourceFileService.upload(clubId, category, title, file);
        return Result.ok();
    }

    /** 资料列表（clubId/category/keyword 均可选；数据权限：管理员全部、负责人本社团） */
    @GetMapping("/list")
    public Result<List<ResourceFileVO>> list(@RequestParam(required = false) Long clubId,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(resourceFileService.list(clubId, category, keyword));
    }

    /** 下载资料 */
    @GetMapping("/download/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        File file = resourceFileService.download(id);
        String name = resourceFileService.getById(id).getFileName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    /** 删除资料（社团负责人/管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceFileService.delete(id);
        return Result.ok();
    }
}
