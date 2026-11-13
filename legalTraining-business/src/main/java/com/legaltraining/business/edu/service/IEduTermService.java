package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduTerm;

/**
 * 学年学期 服务层
 *
 * 对应需求 MG-01-01（学期维护与当前学期切换）、P10（归档与新学期切换）
 */
public interface IEduTermService
{
    /**
     * 查询学年学期列表
     */
    public List<EduTerm> selectEduTermList(EduTerm eduTerm);

    /**
     * 查询学年学期详情
     */
    public EduTerm selectEduTermById(Long termId);

    /**
     * 查询当前学期
     */
    public EduTerm selectCurrentTerm();

    /**
     * 新增学年学期（学年+学期序号唯一）
     */
    public int insertEduTerm(EduTerm eduTerm);

    /**
     * 修改学年学期
     */
    public int updateEduTerm(EduTerm eduTerm);

    /**
     * 批量删除（当前学期、已归档学期不可删）
     */
    public int deleteEduTermByIds(Long[] termIds);

    /**
     * 切换当前学期。
     * 返回 提示信息，含"仍有 x 个任务进行中"，这些任务继续在原学期下运行（P10-N05）。
     */
    public Map<String, Object> switchCurrent(Long termId);

    /**
     * 归档前置检查，返回四项未完成事项统计（P10-N01）。
     */
    public Map<String, Object> checkArchive(Long termId);

    /**
     * 执行归档（P10-N02）。
     *
     * @param termId 学期ID
     * @param force  存在未批阅提交时是否强制归档
     * @param reason 强制归档原因（force=true 时必填）
     */
    public void archive(Long termId, boolean force, String reason);
}
