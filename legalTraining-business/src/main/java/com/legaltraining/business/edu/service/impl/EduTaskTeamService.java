package com.legaltraining.business.edu.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.mapper.EduTaskMapper;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EduTaskTeamService
{
    private final JdbcTemplate jdbc;
    private final EduTaskMapper tasks;

    public EduTaskTeamService(JdbcTemplate jdbc, EduTaskMapper tasks)
    {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    public record Team(long id, long leaderId, boolean confirmed) {}

    public Team forStudent(EduTask task, long userId)
    {
        if (task.getFinishMode() == null || task.getFinishMode() == 0) return null;
        return jdbc.query("""
                select g.group_id,g.leader_id,g.status,g.group_type
                from edu_task_participant p join edu_group g on g.group_id=p.group_id
                where p.task_id=? and p.user_id=? and g.status<>2
                  and (g.task_id=p.task_id or (g.task_id is null and g.group_type=0))
                """, (rs, row) -> new Team(rs.getLong(1), rs.getLong(2), rs.getInt(3) == 1 || rs.getInt(4) == 0),
                task.getTaskId(), userId).stream().findFirst().orElse(null);
    }

    public void lockTask(long taskId)
    {
        jdbc.queryForList("select task_id from edu_task where task_id=? for update", Long.class, taskId);
    }

    public Map<String, Object> overview(long taskId)
    {
        long userId = SecurityUtils.getUserId();
        EduTask task = participantTask(taskId, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        Team team = forStudent(task, userId);
        result.put("team", team);
        result.put("selfId", userId);
        result.put("anyMemberSubmit", anyMemberSubmit(taskId));
        result.put("memberMin", minimum(task));
        result.put("memberMax", maximum(task));
        result.put("members", team == null ? List.of() : members(taskId, team.id()));
        result.put("invites", jdbc.queryForList("""
                select g.group_id as groupId,g.group_name as groupName,u.nick_name as leaderName
                from edu_group g join edu_group_member m on m.group_id=g.group_id
                join sys_user u on u.user_id=g.leader_id
                where g.task_id=? and g.status=0 and m.user_id=? and m.confirm_status=0
                order by g.group_id
                """, taskId, userId));
        return result;
    }

    public List<Map<String, Object>> members(long taskId, long groupId)
    {
        return jdbc.queryForList("""
                select u.user_id as userId,u.nick_name as name,m.confirm_status as confirmStatus,tc.contribution
                from edu_group_member m join sys_user u on u.user_id=m.user_id
                left join edu_task_team_contribution tc on tc.task_id=? and tc.group_id=m.group_id and tc.user_id=m.user_id
                where m.group_id=? and (m.confirm_status=0 or exists (
                    select 1 from edu_task_participant p where p.task_id=? and p.group_id=m.group_id and p.user_id=m.user_id))
                order by m.id
                """, taskId, groupId, taskId);
    }

    @Transactional
    public Map<String, Object> create(long taskId, String name)
    {
        long userId = SecurityUtils.getUserId();
        EduTask task = openTeamTask(taskId, userId);
        if (forStudent(task, userId) != null) throw new ServiceException("你已加入本任务的队伍");
        List<Long> classes = jdbc.queryForList("""
                select cs.class_id from edu_class_student cs join edu_class c on c.class_id=cs.class_id
                join edu_course co on co.term_id=c.term_id
                where co.course_id=? and cs.user_id=? and cs.is_current=1 order by cs.class_id limit 1
                """, Long.class, task.getCourseId(), userId);
        if (classes.isEmpty()) throw new ServiceException("未找到课程学期的班级归属");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            var stmt = conn.prepareStatement("""
                    insert into edu_group(class_id,task_id,group_name,leader_id,member_min,member_max,group_type,status,create_time)
                    values (?,?,?,?,?,?,1,0,now())
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            stmt.setLong(1, classes.get(0));
            stmt.setLong(2, taskId);
            stmt.setString(3, name.trim());
            stmt.setLong(4, userId);
            stmt.setInt(5, minimum(task));
            stmt.setInt(6, maximum(task));
            return stmt;
        }, key);
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
        join(taskId, id, userId);
        jdbc.update("insert into edu_group_member(group_id,user_id,confirm_status,join_time) values(?,?,1,now())", id, userId);
        return overview(taskId);
    }

    @Transactional
    public Map<String, Object> invite(long taskId, String studentNo)
    {
        long userId = SecurityUtils.getUserId();
        EduTask task = openTeamTask(taskId, userId);
        Team team = leaderTeam(task, userId);
        List<Long> students = jdbc.queryForList("""
                select p.user_id from edu_task_participant p join edu_user_profile u on u.user_id=p.user_id
                where p.task_id=? and u.student_no=? and p.group_id is null
                """, Long.class, taskId, studentNo.trim());
        if (students.size() != 1) throw new ServiceException("该学号不在可邀请的任务参与人中");
        boolean crossClass = jdbc.queryForList("""
                select config_value from sys_config where config_key='biz.group.crossClass'
                """, String.class).stream().anyMatch("1"::equals);
        if (!crossClass && jdbc.queryForObject("""
                select count(*) from edu_group g join edu_class_student cs on cs.class_id=g.class_id
                where g.group_id=? and cs.user_id=? and cs.is_current=1
                """, Integer.class, team.id(), students.get(0)) == 0)
            throw new ServiceException("当前仅允许邀请同班成员");
        if (members(taskId, team.id()).size() >= maximum(task))
        {
            throw new ServiceException("已达到组队人数上限");
        }
        jdbc.update("""
                insert into edu_group_member(group_id,user_id,confirm_status) values(?,?,0)
                on duplicate key update confirm_status=0
                """, team.id(), students.get(0));
        return overview(taskId);
    }

    @Transactional
    public Map<String, Object> respond(long taskId, long groupId, boolean accept)
    {
        long userId = SecurityUtils.getUserId();
        EduTask task = openTeamTask(taskId, userId);
        if (accept && forStudent(task, userId) != null) throw new ServiceException("你已加入其他队伍");
        int changed = jdbc.update("""
                update edu_group_member m join edu_group g on g.group_id=m.group_id
                set m.confirm_status=?,m.join_time=now()
                where g.task_id=? and g.group_id=? and g.status=0 and m.confirm_status=0 and m.user_id=?
                """, accept ? 1 : 2, taskId, groupId, userId);
        if (changed == 0) throw new ServiceException("邀请已失效");
        if (accept)
        {
            join(taskId, groupId, userId);
            jdbc.update("""
                    update edu_group_member m join edu_group g on g.group_id=m.group_id
                    set m.confirm_status=2 where g.task_id=? and m.user_id=? and m.confirm_status=0
                    """, taskId, userId);
        }
        return overview(taskId);
    }

    @Transactional
    public Map<String, Object> confirm(long taskId)
    {
        long userId = SecurityUtils.getUserId();
        EduTask task = openTeamTask(taskId, userId);
        Team team = leaderTeam(task, userId);
        long count = members(taskId, team.id()).stream().filter(row -> ((Number) row.get("confirmStatus")).intValue() == 1).count();
        if (count < minimum(task) || count > maximum(task)) throw new ServiceException("已接受邀请的人数不符合任务要求");
        jdbc.update("update edu_group set status=1,update_time=now() where group_id=?", team.id());
        jdbc.update("update edu_group_member set confirm_status=2 where group_id=? and confirm_status=0", team.id());
        return overview(taskId);
    }

    @Transactional
    public void contribution(long taskId, String text)
    {
        long userId = SecurityUtils.getUserId();
        lockTask(taskId);
        EduTask task = participantTask(taskId, userId);
        Team team = forStudent(task, userId);
        if (team == null || !team.confirmed()) throw new ServiceException("请先完成组队");
        if (jdbc.queryForObject("""
                select count(*) from edu_submission where task_id=? and group_id=? and subject_type=1
                and status in (2,3)
                """, Integer.class, taskId, team.id()) > 0) throw new ServiceException("已提交，贡献说明不可修改");
        jdbc.update("""
                insert into edu_task_team_contribution(task_id,group_id,user_id,contribution) values(?,?,?,?)
                on duplicate key update contribution=values(contribution)
                """, taskId, team.id(), userId, text);
    }

    public List<Map<String, Object>> editors(long taskId)
    {
        return editing(taskId, null);
    }

    @Transactional
    public List<Map<String, Object>> editing(long taskId, Boolean active)
    {
        long userId = SecurityUtils.getUserId();
        if (active != null) lockTask(taskId);
        EduTask task = participantTask(taskId, userId);
        Team team = forStudent(task, userId);
        if (team == null || !team.confirmed() || !List.of(1, 2).contains(task.getStatus())) return List.of();
        if (jdbc.queryForObject("""
                select count(*) from edu_submission where task_id=? and subject_type=1 and group_id=? and status in (2,3)
                """, Integer.class, taskId, team.id()) > 0) return List.of();
        if (Boolean.TRUE.equals(active))
        {
            jdbc.update("""
                    insert into edu_task_team_editor(task_id,group_id,user_id,last_seen) values (?,?,?,now())
                    on duplicate key update last_seen=now()
                    """, taskId, team.id(), userId);
        }
        else if (Boolean.FALSE.equals(active))
        {
            jdbc.update("delete from edu_task_team_editor where task_id=? and group_id=? and user_id=?",
                    taskId, team.id(), userId);
        }
        return jdbc.queryForList("""
                select e.user_id as userId,u.nick_name as name
                from edu_task_team_editor e join sys_user u on u.user_id=e.user_id
                join edu_task_participant p on p.task_id=e.task_id and p.group_id=e.group_id and p.user_id=e.user_id
                where e.task_id=? and e.group_id=? and e.last_seen>=timestampadd(second,-45,now())
                order by e.last_seen desc,e.user_id
                """, taskId, team.id());
    }

    @Transactional
    public void migrateLegacy(long taskId, long submissionId)
    {
        lockTask(taskId);
        EduTask task = tasks.selectEduTaskById(taskId);
        if (task == null || task.getFinishMode() == null || task.getFinishMode() == 0)
            throw new ServiceException("只有分组任务可迁移");
        List<Long> owners = jdbc.queryForList("""
                select user_id from edu_submission where task_id=? and submission_id=? and subject_type=0
                """, Long.class, taskId, submissionId);
        if (owners.isEmpty() || owners.get(0) == null) throw new ServiceException("未找到旧版个人作业");
        Team team = forStudent(task, owners.get(0));
        if (team == null || !team.confirmed() || team.leaderId() <= 0)
            throw new ServiceException("原作业学生尚未加入已确认的分组");
        List<Long> related = jdbc.queryForList("""
                select s.submission_id from edu_submission s
                where s.task_id=? and ((s.subject_type=1 and s.group_id=?) or exists (
                   select 1 from edu_task_participant p where p.task_id=s.task_id and p.group_id=? and p.user_id=s.user_id))
                for update
                """, Long.class, taskId, team.id(), team.id());
        if (!related.equals(List.of(submissionId)))
            throw new ServiceException("该组存在多份作业，请核对原作业再处理");
        int grades = jdbc.queryForObject("""
                select count(*) from edu_score sc join edu_task_participant p on p.task_id=sc.task_id and p.user_id=sc.user_id
                where p.task_id=? and p.group_id=?
                """, Integer.class, taskId, team.id());
        int reviews = jdbc.queryForObject("select count(*) from edu_review where submission_id=?", Integer.class, submissionId);
        if (grades > 0 || reviews > 0)
            throw new ServiceException("该作业已进入批阅或成绩流程，需保留原有归属");
        int updated = jdbc.update("""
                update edu_submission set subject_type=1, group_id=?, user_id=?, update_time=now()
                where submission_id=? and task_id=? and subject_type=0
                """, team.id(), team.leaderId(), submissionId, taskId);
        if (updated != 1) throw new ServiceException("作业状态已变化，请刷新");
        jdbc.update("""
                update edu_task_participant p join edu_submission s on s.task_id=p.task_id and s.group_id=p.group_id
                set p.submit_status=s.status,p.overdue_flag=s.is_overdue where s.submission_id=?
                """, submissionId);
    }

    public boolean hasLegacyWork(long taskId, long groupId)
    {
        return jdbc.queryForObject("""
                select count(*) from edu_submission s
                join edu_task_participant p on p.task_id=s.task_id and p.user_id=s.user_id
                where s.task_id=? and p.group_id=? and s.subject_type=0
                """, Integer.class, taskId, groupId) > 0;
    }

    public boolean anyMemberSubmit(long taskId)
    {
        return !jdbc.queryForList("""
                select task_id from edu_task_team_option where task_id=? and any_member_submit=1
                """, Long.class, taskId).isEmpty();
    }

    @Transactional
    public void setAnyMemberSubmit(long taskId, boolean enabled)
    {
        lockTask(taskId);
        EduTask task = tasks.selectEduTaskById(taskId);
        if (task == null || task.getFinishMode() == null || task.getFinishMode() == 0)
            throw new ServiceException("只有分组任务可配置提交权限");
        jdbc.update("""
                insert into edu_task_team_option(task_id,any_member_submit) values(?,?)
                on duplicate key update any_member_submit=values(any_member_submit)
                """, taskId, enabled);
    }

    private Team leaderTeam(EduTask task, long userId)
    {
        Team team = forStudent(task, userId);
        if (team == null || team.leaderId() != userId || team.confirmed())
            throw new ServiceException("只有组建中的队伍组长可以执行该操作");
        return team;
    }

    private void join(long taskId, long groupId, long userId)
    {
        if (jdbc.update("update edu_task_participant set group_id=? where task_id=? and user_id=? and group_id is null",
                groupId, taskId, userId) != 1) throw new ServiceException("无法加入该队伍");
    }

    private EduTask openTeamTask(long taskId, long userId)
    {
        lockTask(taskId);
        EduTask task = participantTask(taskId, userId);
        if (!Integer.valueOf(2).equals(task.getFinishMode()) || !Integer.valueOf(1).equals(task.getStatus())
                || (task.getDeadline() != null && new Date().after(task.getDeadline())))
            throw new ServiceException("当前任务不在自由组队期间");
        return task;
    }

    private EduTask participantTask(long taskId, long userId)
    {
        EduTask task = tasks.selectEduTaskById(taskId);
        if (task == null || tasks.countParticipant(taskId, userId) == 0)
            throw new ServiceException("你不在本任务的参与人名单中");
        return task;
    }

    private int minimum(EduTask task) { return task.getGroupMin() == null ? 2 : task.getGroupMin(); }
    private int maximum(EduTask task) { return task.getGroupMax() == null ? 10 : task.getGroupMax(); }
}
