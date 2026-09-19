package com.club.dto;

import lombok.Data;

/**
 * 资料库文件出参（含社团名/上传人姓名展示字段）
 */
@Data
public class ResourceFileVO {

    private Long id;
    private Long clubId;
    /** 社团名称 */
    private String clubName;
    /** 分类：策划/总结/照片/预算/其他 */
    private String category;
    /** 资料标题 */
    private String title;
    /** 原始文件名 */
    private String fileName;
    private Long uploaderId;
    /** 上传人姓名 */
    private String uploaderName;
    /** 上传时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;
}
