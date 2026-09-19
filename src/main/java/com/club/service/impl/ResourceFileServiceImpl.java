package com.club.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.club.dto.ResourceFileVO;
import com.club.entity.Club;
import com.club.entity.ResourceFile;
import com.club.entity.SysUser;
import com.club.enums.ResourceCategory;
import com.club.mapper.ResourceFileMapper;
import com.club.mapper.SysUserMapper;
import com.club.mapper.ClubMapper;
import com.club.service.ResourceFileService;
import com.club.util.AuthUtil;
import com.club.util.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 社团资料库服务实现
 * 存储规则：{upload-dir}/{clubId}/{category}/{uuid}{ext}，按社团+分类归档，
 * 换届后资料随社团保留，天然支持完整移交。
 */
@Service
public class ResourceFileServiceImpl extends ServiceImpl<ResourceFileMapper, ResourceFile> implements ResourceFileService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 允许上传的文件扩展名白名单 */
    private static final List<String> ALLOWED_EXT = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".zip");

    private final ClubMapper clubMapper;
    private final SysUserMapper userMapper;
    private final AuthUtil authUtil;

    @Value("${club.upload-dir}")
    private String uploadDir;

    public ResourceFileServiceImpl(ClubMapper clubMapper, SysUserMapper userMapper, AuthUtil authUtil) {
        this.clubMapper = clubMapper;
        this.userMapper = userMapper;
        this.authUtil = authUtil;
    }

    @Override
    public void upload(Long clubId, String category, String title, MultipartFile file) {
        Club club = clubMapper.selectById(clubId);
        if (club == null) {
            throw new BizException("社团不存在");
        }
        if (!ResourceCategory.isValid(category)) {
            throw new BizException("资料分类不合法，仅支持：策划/总结/照片/预算/其他");
        }
        // 功能+数据权限：仅本社团负责人（或管理员）可上传
        authUtil.checkClubLeader(clubId);
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("不支持的文件类型: " + ext);
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        String relPath = clubId + "/" + category + "/" + stored;
        // 注意：transferTo 相对路径会被解析到容器临时目录，必须先转为绝对路径
        File dir = java.nio.file.Paths.get(uploadDir, String.valueOf(clubId), category)
                .toAbsolutePath().normalize().toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("创建存储目录失败");
        }
        try {
            file.transferTo(new File(dir, stored));
        } catch (IOException e) {
            throw new BizException("文件保存失败，请稍后重试");
        }
        ResourceFile rf = new ResourceFile();
        rf.setClubId(clubId);
        rf.setCategory(category);
        rf.setTitle(title == null || title.isBlank() ? original : title);
        rf.setFileName(original);
        rf.setFilePath(relPath);
        rf.setUploaderId(authUtil.currentUserId());
        rf.setCreateTime(LocalDateTime.now());
        save(rf);
    }

    @Override
    public List<ResourceFileVO> list(Long clubId, String category, String keyword) {
        // 数据权限：管理员全部；社团负责人仅本社团；学生无权查看
        Long visibleClubId = resolveVisibleClubId(clubId);
        List<ResourceFile> records = list(new LambdaQueryWrapper<ResourceFile>()
                .eq(visibleClubId != null, ResourceFile::getClubId, visibleClubId)
                .eq(category != null && !category.isBlank(), ResourceFile::getCategory, category)
                .like(keyword != null && !keyword.isBlank(), ResourceFile::getTitle, keyword)
                .orderByDesc(ResourceFile::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, String> clubNames = clubMapper.selectList(new LambdaQueryWrapper<Club>()
                        .in(Club::getId, records.stream().map(ResourceFile::getClubId).distinct().toList()))
                .stream().collect(Collectors.toMap(Club::getId, Club::getName));
        Map<Long, String> userNames = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, records.stream().map(ResourceFile::getUploaderId).distinct().toList()))
                .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getName));
        return records.stream().map(rf -> {
            ResourceFileVO vo = new ResourceFileVO();
            vo.setId(rf.getId());
            vo.setClubId(rf.getClubId());
            vo.setClubName(clubNames.get(rf.getClubId()));
            vo.setCategory(rf.getCategory());
            vo.setTitle(rf.getTitle());
            vo.setFileName(rf.getFileName());
            vo.setUploaderId(rf.getUploaderId());
            vo.setUploaderName(userNames.get(rf.getUploaderId()));
            vo.setCreateTime(rf.getCreateTime() == null ? null : rf.getCreateTime().format(FMT));
            return vo;
        }).toList();
    }

    @Override
    public File download(Long id) {
        ResourceFile rf = getById(id);
        if (rf == null) {
            throw new BizException("资料不存在");
        }
        // 数据权限：下载同样仅管理员与本社团负责人
        resolveVisibleClubId(rf.getClubId());
        File f = new File(uploadDir, rf.getFilePath());
        if (!f.exists()) {
            throw new BizException("文件已丢失，可能未随种子数据初始化");
        }
        return f;
    }

    @Override
    public void delete(Long id) {
        ResourceFile rf = getById(id);
        if (rf == null) {
            throw new BizException("资料不存在");
        }
        // 功能+数据权限：仅本社团负责人（或管理员）可删除
        authUtil.checkClubLeader(rf.getClubId());
        removeById(id);
        new File(uploadDir, rf.getFilePath()).delete();
    }

    /**
     * 数据权限解析：返回实际可查询的社团ID（null 表示可查全部）
     */
    private Long resolveVisibleClubId(Long clubId) {
        if (authUtil.isAdmin()) {
            return clubId;
        }
        if (authUtil.isLeader()) {
            Long myClubId = authUtil.requireManagedClubId();
            if (clubId != null && !Objects.equals(clubId, myClubId)) {
                throw new BizException(403, "无权限：只能访问本社团的资料");
            }
            return myClubId;
        }
        throw new BizException(403, "无权限：仅学校管理员与社团负责人可访问资料库");
    }
}
