﻿# 智慧社团Web系统（club-web）

校园社团智能协同系统：覆盖社团申请审核、成员管理、普通/联合活动全流程（发布 → 报名 → 签到 → 总结）、
换届管理、社团资料库、站内消息与数据统计，并基于 Spring AI Tool Call 集成 8 个 AI 技能，
支持学校管理员 / 社团负责人 / 学生三角色权限体系。

## 技术栈

| 项 | 版本 | 说明 |
|---|---|---|
| Java | 17 | |
| Spring Boot | 3.4.1 | |
| Sa-Token | 1.39.0（sa-token-spring-boot3-starter） | 登录认证 + 角色权限 |
| MyBatis-Plus | 3.5.7（mybatis-plus-spring-boot3-starter） | BaseMapper + 分页插件 |
| Spring AI | 1.0.0（spring-ai-starter-model-openai） | OpenAI 兼容接口 + Tool Call 工具调用 |
| MySQL | 8.0 | 库名 `club_web` |

> 以上均为真实依赖，从 Maven 中央仓库下载成功，**未使用降级方案**。

## 目录结构

```
club-web/
├── pom.xml
├── db/
│   ├── init.sql                       # 全量建库脚本（12张表 + 种子数据）
│   └── upgrade_handover_resource.sql  # 存量库增量脚本（换届/资料库两表）
├── docs/
│   ├── api.md                         # REST API 清单（前端对接用）
│   └── login.yaml                     # 登录接口定义
├── test.sh                            # 可重复执行的闭环测试脚本（证据输出到 docs/test-evidence.txt）
├── frontend/                          # Vue3 前端工程（源码）
│   ├── src/views/                     # 页面：login/admin/clubManager/student/common
│   ├── src/components/ai-generate-dialog.vue  # 复用的 AI 弹窗组件（7 种弹窗技能共用）
│   └── src/components/ai-chat-assistant.vue  # AI 活动问答助手（悬浮聊天面板，全角色可见）
└── src/main/java/com/club/
    ├── config/                 # Sa-Token拦截、CORS、全局异常、MP分页
    ├── controller/             # auth/user/club/member/activity/message/ai/file/handover/resource
    ├── service/ + service.impl/
    ├── mapper/                 # MyBatis-Plus BaseMapper
    ├── entity/  dto/  enums/
    ├── util/                   # Result统一返回、AuthUtil功能+数据权限
    └── agent/                  # 【重点】Spring AI 智能体
        ├── AgentService.java        # 统一入口：分发技能、写调用记录、异常降级
        ├── AiPermissionChecker.java # 权限前置校验（调模型前拦截，避免越权返回200）
        └── tools/                   # 5个 @Tool 技能类
            ├── DocumentGenerateTool.java   # 文案生成（活动方案+总结+通知润色+联合方案）
            ├── ClubAnalyseTool.java        # 社团运营分析（查库）
            ├── ActivityRecommendTool.java  # 个性化活动推荐
            ├── RiskInspectTool.java        # 活动风险巡检（仅管理员，四维风险扫描）
            └── ActivityQaTool.java         # 活动问答助手（查真实数据后组织回答）
```

## 启动步骤

1. **建库**（已执行过则跳过；脚本会重建 club_web）：
   ```bash
   mysql -uroot -p < db/init.sql
   ```
2. **编译**：Maven 3.8+（或直接用 IDEA 内置 Maven）：
   ```bash
   mvn compile
   ```
3. **启动**：
   ```bash
   mvn spring-boot:run
   ```
   服务地址：http://localhost:8092

## 账号表（密码均 123456）

| 账号 | 姓名 | 角色 | 说明 |
|---|---|---|---|
| admin | 团委老师 | 学校管理员 | 审批社团/活动 |
| 20230001 | 张明 | 社团发起负责人 | 计算机协会会长 |
| 20230002 | 李婷 | 社团参与负责人 | 摄影协会会长 |
| 20230003 | 王强 | 社团发起负责人 | 青年志愿者协会会长 |
| 20230101 | 陈晨 | 普通学生 | |
| 20230102 | 刘洋 | 普通学生 | |
| 20230103 | 孙浩 | 普通学生 | |

## AI 智能体配置与降级

`application.yml`：

```yaml
spring.ai:
  openai:
    api-key: ${AI_API_KEY:}                 # 默认空 -> 自动降级
    base-url: ${AI_BASE_URL:https://api.deepseek.com}
    chat:
      options:
        model: ${AI_MODEL:deepseek-chat}
```

- **Key 配置**：`api-key` 默认为占位符 `${AI_API_KEY:}`（**仓库不含任何真实密钥**），
  通过环境变量 `AI_API_KEY=sk-xxx` 注入即可启用真实大模型；兼容 OpenAI 接口的服务均可
  （DeepSeek / 通义千问等），`AI_BASE_URL`、`AI_MODEL` 按服务商替换。
  也可直接把默认值写进 yml 占位符 `${AI_API_KEY:sk-你的key}`（切勿提交真实 Key 到公开仓库）。
- **api-key 为空或调用失败/超时时，自动降级为本地模板生成**（复用同一套 Tool 方法，数据口径一致），
  返回内容带 `source` 标注（`AI大模型` / `本地降级模板`），不阻断任何业务。
- 实现说明：因 Spring AI 的 OpenAI 自动装配在空 api-key 下会导致应用启动失败，
  已通过 `spring.autoconfigure.exclude` 排除其自动装配，改为 `AgentService` 在 key 非空时手工构建
  `OpenAiChatModel + ChatClient`，Tool Call 循环仍由 Spring AI 完成（真实 Spring AI 依赖）。
- AI 生成内容**不直接入库**：仅返回给前端弹窗预览，用户确认编辑后回填保存。
- 每次调用写入 `ai_generate_record`（提示词、上下文、结果、状态、降级原因）。
- Agent 无会话记忆（每次独立上下文），同步调用，不做 MQ。

### AI 技能清单（8 个）

| 技能 | 场景入口 | 权限 | 说明 |
|---|---|---|---|
| ACTIVITY_PLAN | 新建活动页·AI 文案 | 负责人/管理员 | 生成活动方案文案，回填 intro |
| ACTIVITY_SUMMARY | 活动结束·AI 总结 | 该活动发起负责人 | 基于真实报名/签到数据生成总结 |
| CLUB_ANALYSE | 统计页·AI 运营分析 | 该社团负责人/管理员 | 成员规模+活动+报名签到数据分析 |
| ACTIVITY_RECOMMEND | 学生首页·AI 推荐 | 登录学生 | 按兴趣标签匹配并过滤已报名 |
| **RISK_INSPECT** | 活动审批页·AI 风险巡检 | **仅管理员** | 时间冲突/安全/合规/资源四维扫描，仅对待审批活动，**不改变审批结果** |
| **NOTICE_POLISH** | 社团信息页·AI 通知润色 | 负责人/管理员 | 正式/亲切/紧急三风格改写，保留原意 |
| **JOINT_PLAN** | 联合活动 Tab·AI 联合方案 | 负责人/管理员 | 按主题+受邀社团生成分工/时间线/宣传/应急预案 |
| **ACTIVITY_QA** | 全局悬浮·活动问答助手 | 登录即可 | 对话式问答，回答仅基于该用户有权限查看的数据 |

**接入方式**

- 弹窗类技能（前 7 个）统一复用 `frontend/src/components/ai-generate-dialog.vue`，
  组件按 `skillType` 自动选择入参（activityId / clubId / text+style / clubIds），旧调用不受影响。
- 问答助手为独立组件 `frontend/src/components/ai-chat-assistant.vue`（悬浮按钮 + 聊天面板），
  挂在 `layout/index.vue`，**所有角色可见**；前端保留聊天记录展示，后端不做会话记忆。
- 请求入参 `AiRequestDTO` 已扩展 `text` / `style` / `clubIds` 三个可选字段，保持向后兼容。

**权限前置校验（重要）**

配置真实 api-key 后，`@Tool` 内部抛出的 `BizException` 会被 Spring AI 捕获并转成错误文本喂回大模型，
模型据此组织「无权访问」文案后**仍按成功返回 200**，导致 API 契约失真。因此新增
`AiPermissionChecker`，在**调用大模型之前**先做一次与 Tool 内部一致的前置校验：
不通过直接抛 403，并在 `ai_generate_record` 记一条失败记录（`call_status=0`），不进入模型调用。

## 关键设计

- **状态机**：活动状态全部由后端控制（枚举 `ActivityStatus`，无魔法数字）；前端只展示。
  联合活动确认语义：受邀社团**拒绝=退出本次联合活动**，不阻塞发起人提交校方审批；
  邀请**同意/拒绝为最终态不可更改**，如需重新邀请请重新创建联合活动。
  提交审批仅允许「待社团确认(1)」状态，防止已审批/已驳回活动状态回退。
- **双层权限**：Sa-Token 功能权限（登录拦截+角色判断）+ Service 内手动数据过滤。
  受邀社团负责人查联合活动报名数据时，只返回本社团成员记录（见 `ActivityServiceImpl#listSignups`）；
  活动签到码 `checkinCode` 仅发起社团负责人/管理员可见（详情与列表接口均脱敏）。
- **Tool 权限**：5 个 @Tool 技能类内部均先做权限校验，防止越权查其他社团数据；Tool 只编排，
  数据查询复用已有 Service（**不在 Tool 内直查 Mapper**）。新增技能同步在 `AiPermissionChecker`
  与 Tool 内各校验一次，两层口径一致。
- **全局异常**：`GlobalExceptionHandler` 统一转 `{code,msg,data}`，AI 报错不抛堆栈到前端。

## 测试

```bash
bash test.sh    # 需服务已启动；证据输出到 docs/test-evidence.txt
```
覆盖：三角色登录 → 普通活动闭环 → 联合活动闭环（含"任一待确认禁止提交"与拒绝路径）→
消息未读/已读 → AI 4场景降级 + 越权403 → 数据权限过滤 → ai_generate_record 落库。

## 前端

Vue3 单页应用，位于 `frontend/`，构建产物集成到后端 `src/main/resources/static/` 同域部署。

### 技术栈

| 项 | 版本 | 说明 |
|---|---|---|
| Vue | 3.5 | Composition API（`<script setup>`） |
| Element Plus | 2.8 | UI 组件库（全量引入 + 中文语言包） |
| Vue Router | 4.4 | history 模式，按角色（ADMIN/CLUB_LEADER/STUDENT）动态渲染菜单 + 路由守卫 |
| Pinia | 2.2 | 用户信息 / token 全局状态（持久化 localStorage） |
| Axios | 1.7 | 统一封装：请求拦截注入 `satoken` 头（无 Bearer 前缀），响应拦截 code!==200 弹 msg、401 跳登录 |
| Vite | 5.4 | 构建工具 |

### 目录结构

```
frontend/src/
├── api/            # request.js（axios 实例+拦截器）、index.js（全部接口封装，与 docs/api.md 一一对应）
├── components/
│   └── ai-generate-dialog.vue   # 【全局复用】AI 生成弹窗（4 种技能共用，预览+编辑+确认回填，不自动提交表单）
├── layout/         # 后台布局 + 按角色侧边栏 + 待办消息未读数角标（30s 轮询）
├── router/         # 路由 + 角色守卫（meta.roles）+ 角色默认首页
├── stores/         # Pinia：user（token/角色/管理社团）
└── views/
    ├── login/                  # 登录页
    ├── admin/                  # 学校管理员：社团审核 / 活动审批 / 全局数据
    ├── clubManager/            # 社团负责人：社团信息 / 成员管理 / 新建活动（普通+联合双Tab，AI文案）
    │                           #   联合活动（受邀同意/拒绝+拒绝理由弹窗）/ 社团统计（AI运营分析）
    ├── student/                # 学生：社团列表 / 活动大厅（自动加载AI推荐+报名签到）/ 我的报名
    └── common/messages.vue     # 消息中心（待办任务/普通通知 两个Tab，全角色）
```

### 开发模式

```bash
cd frontend
npm install --registry=https://registry.npmmirror.com   # 首次安装依赖
npm run dev      # 启动 Vite 开发服务器 http://localhost:5173
```

开发模式下 `vite.config.js` 已配置代理：`/api` 与 `/file` 请求自动转发到后端 `http://localhost:8092`，无需处理跨域。

### 构建与部署

```bash
cd frontend
npm run build    # 产物输出到 frontend/dist/

# 集成到后端（同域部署）：清空旧产物后拷贝
rm -rf ../src/main/resources/static/*
cp -r dist/* ../src/main/resources/static/

# 重启后端后访问 http://localhost:8092 即为前端页面
```

- 后端已添加 `SpaForwardController`（`com.club.controller`）：将 `/login`、`/messages`、`/admin/**`、`/clubManager/**`、`/student/**` 等非 /api 路径 forward 到 `/index.html`，支持 Vue Router history 模式刷新不 404。
- 演示账号见上方账号表，密码均为 `123456`。


## 换届管理 + 资料库

以下两个功能按 club-web 架构（`com.club` 包、MyBatis-Plus、Sa-Token、
枚举规范、数据权限）实现：

### 1. 社团换届管理

| 接口 | 说明 |
| --- | --- |
| `POST /api/handover` | 发起换届：body `{clubId, newLeaderStudentNo, note}` |
| `GET /api/handover/list?clubId=` | 换届记录列表（clubId 可选） |

业务闭环：校验发起人必须是本社团负责人（或管理员）→ 变更 `club.leader_id` →
新负责人写入/提升为成员表「会长」→ 新负责人尚无负责人角色时自动授予
`CLUB_LEADER`（`sys_user_role`，实时生效无需重新登录）→ 写入 `club_handover`
换届记录 → 站内消息通知新旧负责人。
数据权限：管理员可见全部记录；社团负责人仅本社团；学生 403。

### 2. 社团资料库

| 接口 | 说明 |
| --- | --- |
| `POST /api/resource/upload` | 上传：clubId + category(策划/总结/照片/预算/其他) + 可选 title + file |
| `GET /api/resource/list?clubId=&category=&keyword=` | 资料列表（分类/标题关键字过滤） |
| `GET /api/resource/download/{id}` | 下载（UTF-8 原始文件名） |
| `DELETE /api/resource/{id}` | 删除（同时清理磁盘文件） |

存储规则 `{upload-dir}/{clubId}/{category}/{uuid}{ext}`，换届后资料随社团保留、
天然完整移交；扩展名白名单与分类枚举（`ResourceCategory`）校验。
数据权限：上传/删除仅本社团负责人（或管理员）；列表/下载管理员全部、负责人本社团；学生 403。

### 涉及文件

- 后端：`entity/ClubHandover`、`entity/ResourceFile`、`enums/ResourceCategory`、
  `mapper/ClubHandoverMapper`、`mapper/ResourceFileMapper`、`dto/HandoverDTO|HandoverVO|ResourceFileVO`、
  `service/ClubHandoverService(Impl)`、`service/ResourceFileService(Impl)`、
  `controller/ClubHandoverController`、`controller/ResourceFileController`
- 数据库：`db/init.sql`（第 11/12 张表 + 换届演示种子）、增量脚本 `db/upgrade_handover_resource.sql`
  （存量库执行）
- 前端：`views/clubManager/handover.vue`（换届管理页）、
  `views/clubManager/resourceLibrary.vue`（资料库页）、`api/index.js`、
  路由与侧边菜单（负责人角色可见）

### 实测结论

换届：负责人发起 200 → club.leader_id 变更、成员表升会长、自动授予 CLUB_LEADER、
双方收到站内通知；其他社团负责人/学生越权 403；学号不存在 400。
资料库：上传/列表/下载（内容一致、文件名 UTF-8）/删除（DB+磁盘同步清理）全链路 200；
跨社团负责人与学生越权 403；非法分类 400。
回归：登录、活动列表、AI 生成、消息未读数均正常未受影响。
