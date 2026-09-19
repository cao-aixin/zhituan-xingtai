package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.dto.ResourceFileVO;
import com.club.entity.ResourceFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 社团资料库服务
 */
public interface ResourceFileService extends IService<ResourceFile> {

    /** 上传资料（社团负责人/管理员；按 clubId/category 归档存储） */
    void upload(Long clubId, String category, String title, MultipartFile file);

    /** 资料列表（数据权限：管理员全部；社团负责人仅本社团；学生无权查看） */
    List<ResourceFileVO> list(Long clubId, String category, String keyword);

    /** 下载资料：返回磁盘上的实际文件（数据权限同列表） */
    java.io.File download(Long id);

    /** 删除资料（社团负责人/管理员；同时清理磁盘文件） */
    void delete(Long id);
}
