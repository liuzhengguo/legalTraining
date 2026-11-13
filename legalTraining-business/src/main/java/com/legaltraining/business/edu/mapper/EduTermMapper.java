package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduTerm;

/**
 * 学年学期 数据层
 *
 * 对应需求 MG-01-01 / P10
 */
public interface EduTermMapper
{
    /**
     * 查询学年学期
     */
    public EduTerm selectEduTermById(Long termId);

    /**
     * 查询学年学期列表
     */
    public List<EduTerm> selectEduTermList(EduTerm eduTerm);

    /**
     * 查询当前学期（is_current = '1'，全局唯一）
     */
    public EduTerm selectCurrentTerm();

    /**
     * 新增学年学期
     */
    public int insertEduTerm(EduTerm eduTerm);

    /**
     * 修改学年学期
     */
    public int updateEduTerm(EduTerm eduTerm);

    /**
     * 批量删除学年学期
     */
    public int deleteEduTermByIds(Long[] termIds);

    /**
     * 学年+学期序号唯一性校验
     */
    public int countBySchoolYearAndTermNo(@Param("schoolYear") String schoolYear,
                                          @Param("termNo") Integer termNo,
                                          @Param("excludeId") Long excludeId);

    /**
     * 清除当前学期标记（切换当前学期时先清空）
     */
    public int clearCurrentTerm(@Param("excludeId") Long excludeId);

    /**
     * 归档前置检查：进行中的任务数
     */
    public int countInProgressTasks(@Param("termId") Long termId);

    /**
     * 归档前置检查：待批阅提交数
     */
    public int countUnreviewedSubmissions(@Param("termId") Long termId);

    /**
     * 归档前置检查：未发布成绩数
     */
    public int countUnpublishedScores(@Param("termId") Long termId);

    /**
     * 归档前置检查：待授权/待审核的成果推荐数
     */
    public int countPendingRecommendations(@Param("termId") Long termId);

    /**
     * 归档：本学期的任务统一转"已归档"（状态机 6.1）
     */
    public int archiveTasks(@Param("termId") Long termId);

    /**
     * 归档：对存在未批提交的任务打"归档时未批"标记
     */
    public int markUnreviewedTasks(@Param("termId") Long termId);
}
