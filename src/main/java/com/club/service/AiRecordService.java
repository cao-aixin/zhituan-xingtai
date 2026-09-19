package com.club.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.club.entity.AiGenerateRecord;

import java.util.List;

/** AI 调用记录服务 */
public interface AiRecordService extends IService<AiGenerateRecord> {

    /** 我的调用记录（最近50条） */
    List<AiGenerateRecord> myRecords();
}
