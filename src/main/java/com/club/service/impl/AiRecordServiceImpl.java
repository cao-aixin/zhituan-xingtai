package com.club.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.entity.AiGenerateRecord;
import com.club.mapper.AiGenerateRecordMapper;
import com.club.service.AiRecordService;
import com.club.util.AuthUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 调用记录服务实现
 */
@Service
public class AiRecordServiceImpl extends ServiceImpl<AiGenerateRecordMapper, AiGenerateRecord> implements AiRecordService {

    private final AuthUtil authUtil;

    public AiRecordServiceImpl(AuthUtil authUtil) {
        this.authUtil = authUtil;
    }

    @Override
    public List<AiGenerateRecord> myRecords() {
        return lambdaQuery()
                .eq(AiGenerateRecord::getUserId, authUtil.currentUserId())
                .orderByDesc(AiGenerateRecord::getCreateTime)
                .last("LIMIT 50")
                .list();
    }
}
