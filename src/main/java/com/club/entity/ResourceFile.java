package com.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社团资料库文件表
 */
@Data
@TableName("resource_file")
public class ResourceFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 社团ID */
    private Long clubId;
    /** 资料分类：策划/总结/照片/预算/其他（见 ResourceCategory 枚举） */
    private String category;
    /** 资料标题（默认取原始文件名） */
    private String title;
    /** 原始文件名 */
    private String fileName;
    /** 存储相对路径：clubId/category/uuid.ext */
    private String filePath;
    /** 上传人用户ID */
    private Long uploaderId;
    private LocalDateTime createTime;
}
