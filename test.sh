#!/bin/bash
# 智慧社团Web系统 后端闭环测试脚本（输出测试证据）
BASE=http://localhost:8092
EV="$(dirname "$0")/docs/test-evidence.txt"
: > "$EV"

req() { # method path token data
  local m=$1 p=$2 t=$3 d=$4
  if [ -n "$d" ]; then
    curl -s -m 10 -X "$m" "$BASE$p" -H "Content-Type: application/json" ${t:+-H "satoken: $t"} -d "$d"
  else
    curl -s -m 10 -X "$m" "$BASE$p" ${t:+-H "satoken: $t"}
  fi
}
tok() { sed -E 's/.*"token":"([^"]+)".*/\1/'; }

echo "=== 1. 三种角色登录 + 获取当前用户 ===" | tee -a "$EV"
ADMIN=$(req POST /api/auth/login "" '{"studentNo":"admin","password":"123456"}')
LEADER=$(req POST /api/auth/login "" '{"studentNo":"20230001","password":"123456"}')
PARTICIPANT=$(req POST /api/auth/login "" '{"studentNo":"20230002","password":"123456"}')
STUDENT=$(req POST /api/auth/login "" '{"studentNo":"20230101","password":"123456"}')
STUDENT2=$(req POST /api/auth/login "" '{"studentNo":"20230102","password":"123456"}')
echo "admin登录: $ADMIN" | tee -a "$EV"
echo "张明(发起负责人)登录: $LEADER" | tee -a "$EV"
echo "李婷(参与负责人)登录: $PARTICIPANT" | tee -a "$EV"
echo "陈晨(学生)登录: $STUDENT" | tee -a "$EV"
T_ADMIN=$(echo "$ADMIN" | tok); T_LEADER=$(echo "$LEADER" | tok); T_PART=$(echo "$PARTICIPANT" | tok); T_STU=$(echo "$STUDENT" | tok); T_STU2=$(echo "$STUDENT2" | tok)
echo "当前用户(admin): $(req GET /api/auth/me "$T_ADMIN")" | tee -a "$EV"
echo "当前用户(张明): $(req GET /api/auth/me "$T_LEADER")" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 2. 普通活动闭环：创建->草稿->提交->审批->报名->签到 ===" | tee -a "$EV"
ACT=$(req POST /api/activity "$T_LEADER" '{"title":"AI编程训练营第一期","isJoint":0,"location":"实训楼401","startTime":"2026-10-01 14:00:00","endTime":"2026-10-01 17:00:00","capacity":30,"intro":"初稿介绍"}')
echo "创建普通活动: $ACT" | tee -a "$EV"
AID=$(echo "$ACT" | sed -E 's/.*"data":([0-9]+).*/\1/')
echo "保存草稿(编辑): $(req PUT /api/activity "$T_LEADER" "{\"id\":$AID,\"title\":\"AI编程训练营第一期(改)\",\"isJoint\":0,\"location\":\"实训楼401\",\"startTime\":\"2026-10-01 14:00:00\",\"endTime\":\"2026-10-01 17:00:00\",\"capacity\":30,\"intro\":\"AI生成并经用户确认的文案\"}")" | tee -a "$EV"
echo "提交审批: $(req POST /api/activity/$AID/submit "$T_LEADER")" | tee -a "$EV"
echo "审批通过: $(req POST /api/activity/$AID/audit "$T_ADMIN" '{"approved":true}')" | tee -a "$EV"
echo "活动详情(含签到码): $(req GET /api/activity/$AID "$T_LEADER")" | tee -a "$EV"
echo "学生报名: $(req POST /api/activity/$AID/signup "$T_STU")" | tee -a "$EV"
CODE=$(req GET /api/activity/$AID "$T_LEADER" | sed -E 's/.*"checkinCode":"([0-9]+)".*/\1/')
echo "学生签到(码=$CODE): $(req POST /api/activity/signup/checkin "$T_STU" "{\"activityId\":$AID,\"checkinCode\":\"$CODE\"}")" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 3. 联合活动闭环：创建(2受邀)->同意/待确认拦截->全部确认->提交->审批 ===" | tee -a "$EV"
JOINT=$(req POST /api/activity "$T_LEADER" '{"title":"校园科技嘉年华联合专场","isJoint":1,"location":"中心广场","startTime":"2026-11-01 09:00:00","endTime":"2026-11-01 17:00:00","capacity":100,"safetyOfficer":"张明","inviteClubIds":[2,3]}')
echo "创建联合活动(邀请社团2,3): $JOINT" | tee -a "$EV"
JID=$(echo "$JOINT" | sed -E 's/.*"data":([0-9]+).*/\1/')
echo "活动状态(应为1待社团确认): $(req GET /api/activity/$JID "$T_LEADER")" | tee -a "$EV"
echo "受邀社团2(李婷)同意: $(req POST /api/activity/joint-confirm "$T_PART" "{\"activityId\":$JID,\"agree\":true}")" | tee -a "$EV"
echo "任一待确认时提交审批(应被拒): $(req POST /api/activity/$JID/submit "$T_LEADER")" | tee -a "$EV"
T_WANG=$(req POST /api/auth/login "" '{"studentNo":"20230003","password":"123456"}' | tok)
echo "受邀社团3(王强)同意: $(req POST /api/activity/joint-confirm "$T_WANG" "{\"activityId\":$JID,\"agree\":true}")" | tee -a "$EV"
echo "全部确认后提交审批: $(req POST /api/activity/$JID/submit "$T_LEADER")" | tee -a "$EV"
echo "管理员审批通过: $(req POST /api/activity/$JID/audit "$T_ADMIN" '{"approved":true}')" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 3b. 联合活动拒绝路径（拒绝必填理由） ===" | tee -a "$EV"
JOINT2=$(req POST /api/activity "$T_LEADER" '{"title":"周末联合观影会","isJoint":1,"location":"小剧场","startTime":"2026-11-15 18:00:00","endTime":"2026-11-15 21:00:00","capacity":50,"safetyOfficer":"张明","inviteClubIds":[2]}')
JID2=$(echo "$JOINT2" | sed -E 's/.*"data":([0-9]+).*/\1/')
echo "拒绝(无理由,应报错): $(req POST /api/activity/joint-confirm "$T_PART" "{\"activityId\":$JID2,\"agree\":false}")" | tee -a "$EV"
echo "拒绝(有理由): $(req POST /api/activity/joint-confirm "$T_PART" "{\"activityId\":$JID2,\"agree\":false,\"refuseReason\":\"档期冲突\"}")" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 4. 站内消息：未读计数 / 已读 ===" | tee -a "$EV"
echo "王强待办未读数: $(req GET "/api/message/unread-count?msgType=0" "$T_WANG")" | tee -a "$EV"
echo "王强消息列表: $(req GET "/api/message/list?msgType=0" "$T_WANG")" | tee -a "$EV"
FIRST_MID=$(req GET "/api/message/list?msgType=0" "$T_WANG" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "标记已读(消息$FIRST_MID): $(req PUT "/api/message/$FIRST_MID/read" "$T_WANG")" | tee -a "$EV"
echo "再查未读数: $(req GET "/api/message/unread-count?msgType=0" "$T_WANG")" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 5. AI 四场景（key为空 -> 降级模板，写 ai_generate_record） ===" | tee -a "$EV"
echo "AI活动文案: $(req POST /api/ai/generate "$T_LEADER" '{"skill":"ACTIVITY_PLAN","activityId":'"$AID"',"extraInput":"面向零基础学生的AI编程体验"}')" | tee -a "$EV"
echo "AI活动总结: $(req POST /api/ai/generate "$T_LEADER" '{"skill":"ACTIVITY_SUMMARY","activityId":'"$AID"'}')" | tee -a "$EV"
echo "AI运营分析: $(req POST /api/ai/generate "$T_LEADER" '{"skill":"CLUB_ANALYSE","clubId":1,"extraInput":"招新转化"}')" | tee -a "$EV"
echo "AI个性推荐: $(req POST /api/ai/generate "$T_STU" '{"skill":"ACTIVITY_RECOMMEND"}')" | tee -a "$EV"
echo "越权校验(学生调运营分析,应403): $(req POST /api/ai/generate "$T_STU" '{"skill":"CLUB_ANALYSE","clubId":1}')" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 6. 数据权限：受邀负责人查报名数据仅见本社团成员 ===" | tee -a "$EV"
req POST /api/activity/$JID/signup "$T_STU" >/dev/null   # 陈晨(club1成员)报名联合活动
req POST /api/activity/$JID/signup "$T_STU2" >/dev/null  # 刘洋(club2成员)报名联合活动
echo "发起负责人(张明)看全部报名: $(req GET /api/activity/$JID/signups "$T_LEADER")" | tee -a "$EV"
echo "受邀负责人(李婷)仅见本社团报名: $(req GET /api/activity/$JID/signups "$T_PART")" | tee -a "$EV"
echo "学生(陈晨)仅见本人报名: $(req GET /api/activity/$JID/signups "$T_STU")" | tee -a "$EV"

echo "" | tee -a "$EV"
echo "=== 7. ai_generate_record 落库验证（由MySQL查询见交付报告） ===" | tee -a "$EV"
echo "我的AI记录条数: $(req GET /api/ai/record/list "$T_LEADER" | grep -o '"id"' | wc -l)" | tee -a "$EV"
echo "测试完成"
