# 智慧社团Web系统 后端 REST API 清单

> Base URL: `http://localhost:8092`
> 认证方式：除 `/api/auth/login` 外，所有接口需在请求头携带 `satoken: <token>`（登录后返回）。
> 统一返回结构：`{"code": 200, "msg": "", "data": ...}`；出错时 code 为 400（业务）/401（未登录）/403（无权限）/500（系统异常）。

## 账号（密码均为 123456）

| 账号 | 姓名 | 角色 |
|---|---|---|
| admin | 团委老师 | 学校管理员 |
| 20230001 | 张明 | 社团发起负责人（计算机协会会长） |
| 20230002 | 李婷 | 社团参与负责人（摄影协会会长） |
| 20230003 | 王强 | 社团发起负责人（志愿者协会会长） |
| 20230101 | 陈晨 | 普通学生 |
| 20230102 | 刘洋 | 普通学生 |

---

## 1. 认证 /api/auth

### 1.1 登录 `POST /api/auth/login`（免鉴权）
```json
// 请求
{ "studentNo": "20230001", "password": "123456" }
// 响应 data
{ "id": 2, "studentNo": "20230001", "name": "张明", "roleCodes": ["CLUB_LEADER"],
  "roleNames": ["社团发起负责人"], "token": "xxx-xxx", "managedClubId": 1, "aiRecommend": 1 }
```

### 1.2 登出 `POST /api/auth/logout`
响应 `data: null`

### 1.3 当前用户 `GET /api/auth/me`
响应 data 同 1.1（token 为 null），前端据此判断角色渲染菜单。

---

## 2. 用户 /api/user（仅管理员）

### 2.1 用户列表 `GET /api/user/list?keyword=张`（管理员）
### 2.2 新增用户 `POST /api/user`（管理员，默认密码123456）
```json
{ "studentNo": "20230108", "name": "何佳", "phone": "13800000000", "roleId": 4 }
```
### 2.3 用户详情 `GET /api/user/{id}`（管理员）

---

## 3. 社团 /api/club

### 3.1 社团列表 `GET /api/club/list?keyword=摄影`
学生只返回正常社团；管理员返回全部。data 元素：
```json
{ "id": 1, "name": "计算机协会", "intro": "以代码会友...", "tags": "科技,编程", "status": 1, "leaderId": 2 }
```
### 3.2 社团详情 `GET /api/club/{id}`
### 3.3 申请创建社团 `POST /api/club/apply`（学生）
```json
{ "name": "辩论社", "intro": "锻炼思辨与表达", "tags": "文艺,思辨" }
```
申请人自动成为会长，社团进入待审核（status=0）。
### 3.4 待审核列表 `GET /api/club/pending`（管理员）
### 3.5 社团审核 `PUT /api/club/audit`（管理员）
```json
{ "id": 5, "auditResult": 1 }        // 1通过（授予申请人CLUB_LEADER角色）
{ "id": 5, "auditResult": 2, "rejectReason": "材料不全" }   // 2驳回
```
### 3.6 我管理的社团 `GET /api/club/my`（负责人）

---

## 4. 社团成员 /api/club

### 4.1 成员列表 `GET /api/club/{clubId}/member/list`（本社团负责人/管理员）
```json
[{ "id": 1, "userId": 2, "memberRole": "会长", "status": 1, "statusDesc": "正常", "userName": "张明", "studentNo": "20230001" }]
```
### 4.2 申请入社 `POST /api/club/{clubId}/member/join`（学生）
### 4.3 退出社团 `POST /api/club/{clubId}/member/quit`（会长不可退出）
### 4.4 入社审核 `PUT /api/club/member/{memberId}/audit?approved=true`（本社团负责人）

---

## 5. 活动 /api/activity

活动状态码（后端状态机，前端只展示）：
`0草稿 1待社团确认(仅联合) 2待校方审批 3进行中 4已结束 5已驳回`
联合确认状态：`0待确认 1同意 2拒绝`

### 5.1 创建活动 `POST /api/activity`（社团负责人）
普通活动（进入草稿）：
```json
{ "title": "AI编程训练营第一期", "isJoint": 0, "location": "实训楼401",
  "startTime": "2026-10-01 14:00:00", "endTime": "2026-10-01 17:00:00",
  "capacity": 30, "intro": "活动文案（可由AI生成后回填）" }
```
联合活动（直接进入 1待社团确认，并给受邀社团负责人推待办消息）：
```json
{ "title": "校园科技嘉年华联合专场", "isJoint": 1, "location": "中心广场",
  "startTime": "2026-11-01 09:00:00", "endTime": "2026-11-01 17:00:00",
  "capacity": 100, "safetyOfficer": "张明", "inviteClubIds": [2, 3] }
```
响应 `data: 活动ID`。

### 5.2 编辑草稿 `PUT /api/activity`（发起负责人，仅草稿可改）
body 同 5.1，需带 `"id": 活动ID`。

### 5.3 提交校方审批 `POST /api/activity/{id}/submit`（发起负责人）
- 普通活动：0草稿 → 2待校方审批
- 联合活动：任一受邀社团 join_status=0 时返回 400：`以下受邀社团尚未确认参与，不能提交校方审批：xxx`；全部确认后 1 → 2。

### 5.4 管理员审批 `POST /api/activity/{id}/audit`（管理员）
```json
{ "approved": true }
{ "approved": false, "rejectReason": "场地时间冲突" }
```
通过 → 3进行中并生成4位签到码（推送通知含签到码）；驳回 → 5已驳回。

### 5.5 受邀社团确认 `POST /api/activity/joint-confirm`（受邀社团负责人）
```json
{ "activityId": 7, "agree": true }
{ "activityId": 7, "agree": false, "refuseReason": "档期冲突" }
```
**确认语义（重要）**：
- 受邀社团**拒绝(2)=该社团退出本次联合活动，不阻塞发起人提交校方审批**；
  提交审批的前置校验只拦截「待确认(0)」状态。
- 邀请一旦**同意/拒绝即为最终态，不可更改**；再次操作返回 400：
  「该邀请已处理（同意/拒绝为最终态），如需重新邀请请重新创建联合活动」。

### 5.6 活动列表 `GET /api/activity/list?status=3&clubId=1`
自动按角色过滤：管理员全部；负责人=我发起的+受邀参与的联合活动；学生=进行中/已结束。
data 元素（ActivityVO）：
```json
{ "id": 7, "title": "校园科技嘉年华联合专场", "isJoint": 1, "clubId": 1, "clubName": "计算机协会",
  "location": "中心广场", "startTime": "2026-11-01 09:00:00", "endTime": "2026-11-01 17:00:00",
  "capacity": 100, "intro": null, "safetyOfficer": "张明", "status": 1, "statusDesc": "待社团确认",
  "rejectReason": null, "checkinCode": null, "summary": null,
  "jointJoins": [ { "id": 4, "clubId": 2, "clubName": "摄影协会", "joinStatus": 0, "joinStatusDesc": "待确认", "refuseReason": null } ],
  "signed": false }
```
`checkinCode` 仅发起负责人/管理员可见。

### 5.7 活动详情 `GET /api/activity/{id}`

### 5.8 学生报名 `POST /api/activity/{id}/signup`（仅进行中；校验重复与名额）

### 5.9 签到 `POST /api/activity/signup/checkin`（学生）
```json
{ "activityId": 6, "checkinCode": "4885" }
```

### 5.10 报名列表 `GET /api/activity/{id}/signups`（数据权限）
- 管理员 / 发起负责人：全部
- 受邀社团负责人：仅本社团成员的报名
- 学生：仅本人
```json
[{ "id": 9, "activityId": 7, "userId": 6, "userName": "刘洋", "studentNo": "20230102", "clubName": "计算机协会", "checked": 0 }]
```

### 5.11 我报名的活动 `GET /api/activity/my-signed`（学生）

### 5.12 结束活动 `POST /api/activity/{id}/finish`（发起负责人/管理员，3→4）
结束后前端可调 AI 总结（skill=ACTIVITY_SUMMARY）生成总结文案，确认后回填。

---

## 6. 站内消息 /api/message

消息类型：`0待办任务 1普通通知`

### 6.1 我的消息 `GET /api/message/list?msgType=0`（msgType 可选）
```json
[{ "id": 9, "title": "联合活动确认邀请", "content": "《校园科技嘉年华联合专场》...",
   "msgType": 0, "msgTypeDesc": "待办任务", "isRead": 0, "bizId": 7, "createTime": "2026-09-12 14:47:42" }]
```
`bizId` 为关联活动ID，前端跳转处理页用。

### 6.2 未读数 `GET /api/message/unread-count?msgType=0`
`{ "unreadCount": 1 }` —— 首页角标用。

### 6.3 标记已读 `PUT /api/message/{id}/read`
### 6.4 全部已读 `PUT /api/message/read-all`

---

## 7. AI 智能体 /api/ai

### 7.1 AI 生成（统一入口）`POST /api/ai/generate`
```json
// 请求（旧技能，向后兼容）
{ "skill": "ACTIVITY_PLAN", "activityId": 6, "extraInput": "面向零基础学生" }
```
```json
// 请求（扩展字段示例）
// 通知润色：text=草稿正文，style=风格
{ "skill": "NOTICE_POLISH", "text": "本周五下午三点开例会，请准时参加。", "style": "亲切" }
// 联合活动方案：extraInput=主题，clubIds=受邀社团ID列表
{ "skill": "JOINT_PLAN", "extraInput": "校园环保公益行", "clubIds": [2, 3] }
```

**入参字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| skill | String | 技能名（见下表），必传 |
| activityId | Long | 活动ID（ACTIVITY_PLAN/ACTIVITY_SUMMARY/RISK_INSPECT） |
| clubId | Long | 社团ID（CLUB_ANALYSE） |
| extraInput | String | 补充输入：主题 / 要求 / 学生提问 |
| text | String | **扩展**：待处理正文（NOTICE_POLISH 的通知草稿） |
| style | String | **扩展**：风格偏好（NOTICE_POLISH：正式 / 亲切 / 紧急，缺省按正式） |
| clubIds | Long[] | **扩展**：受邀社团ID列表（JOINT_PLAN） |

**技能清单**

| skill | 场景 | 必传参数 | 所需角色 |
|---|---|---|---|
| ACTIVITY_PLAN | 新建活动页·AI文案 | activityId 或 extraInput(主题) | 社团负责人 |
| ACTIVITY_SUMMARY | 活动结束页·AI总结 | activityId | 该活动发起负责人 |
| CLUB_ANALYSE | 统计页·AI运营分析 | clubId | 该社团负责人/管理员 |
| ACTIVITY_RECOMMEND | 学生首页·AI推荐 | 无 | 登录学生 |
| **RISK_INSPECT** | 活动审批页·AI风险巡检 | activityId（仅待审批活动） | **仅学校管理员** |
| **NOTICE_POLISH** | 社团信息页·AI通知润色 | text（style 可选） | 社团负责人/管理员 |
| **JOINT_PLAN** | 联合活动Tab·AI联合方案 | extraInput(主题) + clubIds | 社团负责人/管理员 |
| **ACTIVITY_QA** | 全局悬浮·活动问答助手 | extraInput(提问) | 登录即可（数据按权限过滤） |

**扩展技能说明**

- **RISK_INSPECT 活动风险巡检**：对「待校方审批」状态的活动做四维扫描——时间冲突（同场地邻近时段已通过活动）、安全风险（安全负责人/规模/户外场地）、内容合规（敏感与商用关键词、介绍完整度、社团资质）、预算与资源合理性（报名与名额匹配度），并检查联合活动受邀社团确认情况，输出风险等级 + 风险清单 + 处置建议。**报告仅作审批参考，不改变审批结果**。非待审批活动返回 400。
- **NOTICE_POLISH 通知润色**：保留原文关键信息（时间、地点、要求）前提下按风格改写；风格取值正式 / 亲切 / 紧急，非法值按「正式」处理。
- **JOINT_PLAN 联合活动方案**：依据受邀社团**真实名称与标签**生成贴合实际的分工建议（如摄影协会→影像记录、志愿者协会→现场秩序），含主题定位、各社团分工、筹备时间线（T-14 至 T+2）、宣传方案、安全应急预案。
- **ACTIVITY_QA 活动问答助手**：对话式问答，后端**不做会话记忆**（前端保留聊天记录展示）。回答只基于该用户有权限查看的数据——活动数据统一走 `ActivityService.listActivities`，内部已按角色做数据权限过滤（学生仅见进行中/已结束活动）。多轮提问即多次独立调用本接口。

```json
// 响应 data
{ "recordId": 2, "source": "AI大模型", "content": "【活动主题】..." }
```
- `source`：`AI大模型`（配置了 spring.ai.openai.api-key 且调用成功）/ `本地降级模板`（key 为空或调用失败）。
- 内容仅供弹窗预览+编辑，用户确认后由前端回填表单提交，**后端不会自动入库**。
- AI 挂掉不报 500，始终返回可用内容或明确业务提示。
- **权限前置校验**：为避免 @Tool 内异常被 Spring AI 捕获后仍返回 200，权限在调用大模型**之前**校验（`AiPermissionChecker`）。越权请求直接返回 403，并在 `ai_generate_record` 记一条失败记录（call_status=0）。
- 降级路径复用**同一套 Tool 方法**，权限校验与数据口径和 AI 版本完全一致。

### 7.2 我的AI调用记录 `GET /api/ai/record/list`（最近50条）

---

## 8. 文件上传 /api/file

### 8.1 上传 `POST /api/file/upload`（multipart，字段名 file）
支持 jpg/jpeg/png/gif/pdf/doc/docx/xls/xlsx/txt，最大10MB。
```json
{ "fileName": "海报.png", "url": "/file/xxxx.png" }
```
静态访问：`http://localhost:8092/file/xxxx.png`（本地存储于后端 `./upload/`）。

---

## 附：状态流转（全部由后端控制）

```
普通活动: 0草稿 --提交--> 2待校方审批 --通过--> 3进行中 --结束--> 4已结束
                                  └--驳回--> 5已驳回
联合活动: 1待社团确认(创建即入) --受邀社团全部处理--> 2待校方审批 --通过--> 3进行中 --> 4已结束
                                          └--驳回--> 5已驳回
* 提交校方审批前置校验：仅「待社团确认(1)」状态可提交，其他状态一律 400；
  且任一受邀社团 join_status=0待确认 → 拒绝提交(400)
* 受邀社团拒绝(2)=该社团退出本次联合活动，不阻塞提交审批；
  同意/拒绝为最终态不可更改，如需重新邀请请重新创建联合活动
* 签到码 checkinCode 仅发起社团负责人/管理员可见（详情与列表接口均已脱敏）
```
