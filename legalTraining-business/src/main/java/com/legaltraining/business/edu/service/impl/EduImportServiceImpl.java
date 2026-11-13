package com.legaltraining.business.edu.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.business.edu.domain.EduClass;
import com.legaltraining.business.edu.domain.EduImportBatch;
import com.legaltraining.business.edu.domain.EduImportRow;
import com.legaltraining.business.edu.domain.EduStudent;
import com.legaltraining.business.edu.domain.EduStudentImportVo;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.mapper.EduClassMapper;
import com.legaltraining.business.edu.mapper.EduImportMapper;
import com.legaltraining.business.edu.mapper.EduStudentMapper;
import com.legaltraining.business.edu.mapper.EduTermMapper;
import com.legaltraining.business.edu.service.IEduImportService;
import com.legaltraining.business.edu.service.IEduStudentService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 学生账号批量导入 服务实现
 *
 * 关键口径（MG-01-04）：
 * 1. 预检不落账号，只写批次与逐行结果；
 * 2. 已存在账号做"更新班级"，在报告中单列为"更新"类，不新建；
 * 3. 同一文件内学号重复，后者覆盖前者，前者记为失败并在原因里写明"被第 N 行覆盖"；
 * 4. 单批失败率 > 30% 时整批不落库，提示模板错误；
 * 5. 单批建议 ≤ 5000 行，超出给出分批提示但不阻断。
 */
@Service
public class EduImportServiceImpl implements IEduImportService
{
    private static final Logger log = LoggerFactory.getLogger(EduImportServiceImpl.class);

    /** 失败率阈值：超过即整批回滚 */
    private static final double FAIL_RATE_LIMIT = 0.30D;

    /** 单批建议行数 */
    private static final int SUGGEST_BATCH_ROWS = 5000;

    /** 逐行结果 */
    private static final int ROW_OK = 0;
    private static final int ROW_FAIL = 1;
    private static final int ROW_UPDATE = 2;

    /** 批次状态 */
    private static final int BATCH_PREVIEWED = 1;
    private static final int BATCH_CONFIRMED = 2;
    private static final int BATCH_ROLLBACK = 3;

    @Autowired
    private EduImportMapper eduImportMapper;

    @Autowired
    private EduStudentMapper eduStudentMapper;

    @Autowired
    private EduClassMapper eduClassMapper;

    @Autowired
    private EduTermMapper eduTermMapper;

    @Autowired
    private IEduStudentService eduStudentService;

    @Override
    public Map<String, Object> preview(MultipartFile file, Long termId)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要导入的 Excel 文件");
        }
        EduTerm term = resolveTerm(termId);
        if (term.getStatus() != null && term.getStatus() == 2)
        {
            throw new ServiceException("学期「" + term.getTermName() + "」已归档，不能导入学生");
        }

        List<EduStudentImportVo> rows;
        try (InputStream in = file.getInputStream())
        {
            ExcelUtil<EduStudentImportVo> util = new ExcelUtil<EduStudentImportVo>(EduStudentImportVo.class);
            rows = util.importExcel(in);
        }
        catch (Exception e)
        {
            log.error("导入文件解析失败", e);
            throw new ServiceException("文件解析失败，请确认使用平台提供的模板（.xlsx）");
        }
        if (rows == null || rows.isEmpty())
        {
            throw new ServiceException("文件内容为空，请填写后再上传");
        }

        // 班级编码 → 班级（一次查全，避免逐行查库）
        Map<String, EduClass> classMap = new HashMap<>();
        for (EduClass c : eduClassMapper.selectEduClassList(termScoped(term.getTermId())))
        {
            classMap.put(c.getClassCode(), c);
        }

        List<EduImportRow> results = new ArrayList<>(rows.size());
        Map<String, Integer> firstRowOfStudent = new HashMap<>();
        int ok = 0, fail = 0, update = 0;
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++)
        {
            EduStudentImportVo vo = rows.get(i);
            int rowNo = i + 2; // Excel 含表头，从 2 开始
            EduImportRow row = new EduImportRow();
            row.setRowNo(rowNo);
            row.setRawData(JSON.toJSONString(vo));

            String studentNo = trim(vo.getStudentNo());
            String realName = trim(vo.getRealName());
            String classCode = trim(vo.getClassCode());
            String phone = trim(vo.getPhone());
            String reason = null;

            if (StringUtils.isEmpty(studentNo))
            {
                reason = "学号不能为空";
            }
            else if (StringUtils.isEmpty(realName))
            {
                reason = "姓名不能为空";
            }
            else if (StringUtils.isEmpty(classCode))
            {
                reason = "班级编码不能为空";
            }
            else if (!classMap.containsKey(classCode))
            {
                reason = "班级编码「" + classCode + "」在本学期不存在";
            }
            else if (StringUtils.isNotEmpty(phone) && phone.length() != 11)
            {
                reason = "手机号须为 11 位，当前为 " + phone.length() + " 位";
            }

            // 文件内重复：后者覆盖前者，把先出现的行改成失败并注明被谁覆盖
            if (reason == null && firstRowOfStudent.containsKey(studentNo))
            {
                int prevRowNo = firstRowOfStudent.get(studentNo);
                for (int k = results.size() - 1; k >= 0; k--)
                {
                    EduImportRow prev = results.get(k);
                    if (prev.getRowNo() == prevRowNo)
                    {
                        prev.setResult(ROW_FAIL);
                        prev.setFailReason("学号「" + studentNo + "」在本文件内重复，已按第 " + rowNo + " 行处理");
                        break;
                    }
                }
                firstRowOfStudent.put(studentNo, rowNo);
            }
            else if (reason == null)
            {
                firstRowOfStudent.put(studentNo, rowNo);
            }

            if (reason != null)
            {
                row.setResult(ROW_FAIL);
                row.setFailReason(reason);
                fail++;
            }
            else
            {
                boolean exists = eduStudentMapper.countStudentNo(studentNo, null) > 0;
                row.setResult(exists ? ROW_UPDATE : ROW_OK);
                if (exists)
                {
                    update++;
                }
                else
                {
                    ok++;
                }
            }
            results.add(row);
        }

        // 上面的"重复行"处理会同时改写前一行的结果，这里以最终结果为准重算统计
        ok = 0;
        fail = 0;
        update = 0;
        for (EduImportRow r : results)
        {
            if (r.getResult() == ROW_OK)
            {
                ok++;
            }
            else if (r.getResult() == ROW_UPDATE)
            {
                update++;
            }
            else
            {
                fail++;
            }
        }

        int total = results.size();
        double failRate = total == 0 ? 0D : (double) fail / total;
        if (total > SUGGEST_BATCH_ROWS)
        {
            warnings.add("本次共 " + total + " 行，超过建议的 " + SUGGEST_BATCH_ROWS
                    + " 行，建议拆分为多个文件分批导入，单批失败可独立回滚");
        }

        EduImportBatch batch = new EduImportBatch();
        batch.setBizType("student");
        batch.setTermId(term.getTermId());
        batch.setFileName(file.getOriginalFilename());
        batch.setTotalRows(total);
        batch.setSuccessRows(ok);
        batch.setUpdateRows(update);
        batch.setFailRows(fail);
        batch.setStatus(BATCH_PREVIEWED);
        batch.setCreateBy(currentUsername());

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("totalRows", total);
        preview.put("successRows", ok);
        preview.put("updateRows", update);
        preview.put("failRows", fail);
        preview.put("failRate", round(failRate));
        batch.setPreviewJson(JSON.toJSONString(preview));
        eduImportMapper.insertBatch(batch);

        for (EduImportRow r : results)
        {
            r.setBatchId(batch.getBatchId());
        }
        eduImportMapper.batchInsertRows(results);

        Map<String, Object> result = new LinkedHashMap<>(preview);
        result.put("batchId", batch.getBatchId());
        result.put("termId", term.getTermId());
        result.put("termName", term.getTermName());
        result.put("overLimit", failRate > FAIL_RATE_LIMIT);
        result.put("warnings", warnings);
        // 预检只回传前 100 条失败明细，完整清单走失败清单接口
        List<Map<String, Object>> failSample = new ArrayList<>();
        for (EduImportRow r : results)
        {
            if (r.getResult() == ROW_FAIL && failSample.size() < 100)
            {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rowNo", r.getRowNo());
                m.put("reason", r.getFailReason());
                failSample.add(m);
            }
        }
        result.put("failSample", failSample);
        result.put("message", failRate > FAIL_RATE_LIMIT
                ? "预检失败率 " + Math.round(failRate * 100) + "%，超过 30%，确认导入时整批不会落库，请先修正模板"
                : "预检完成：可导入 " + ok + " 行，更新 " + update + " 行，失败 " + fail + " 行");
        return result;
    }

    @Override
    public Map<String, Object> confirm(Long batchId)
    {
        EduImportBatch batch = eduImportMapper.selectBatchById(batchId);
        if (batch == null)
        {
            throw new ServiceException("导入批次不存在");
        }
        if (batch.getStatus() != null && batch.getStatus() == BATCH_CONFIRMED)
        {
            throw new ServiceException("该批次已导入，不能重复确认");
        }
        if (batch.getStatus() != null && batch.getStatus() == BATCH_ROLLBACK)
        {
            throw new ServiceException("该批次已回滚，请重新上传文件预检");
        }

        List<EduImportRow> rows = eduImportMapper.selectRows(batchId, null);
        int total = rows.size();
        long failCount = rows.stream().filter(r -> r.getResult() == ROW_FAIL).count();
        double failRate = total == 0 ? 0D : (double) failCount / total;
        if (failRate > FAIL_RATE_LIMIT)
        {
            // 整批不落库：把批次标记为已回滚，便于导入人看清原因后重新上传
            EduImportBatch mark = new EduImportBatch();
            mark.setBatchId(batchId);
            mark.setStatus(BATCH_ROLLBACK);
            mark.setFinishTime(new Date());
            eduImportMapper.updateBatch(mark);
            throw new ServiceException("预检失败率 " + Math.round(failRate * 100)
                    + "% 超过 30%，整批已回滚。请按失败清单修正模板后重新上传");
        }

        int created = 0, updated = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        Map<Long, Integer> touchedClass = new HashMap<>();

        for (EduImportRow row : rows)
        {
            if (row.getResult() == ROW_FAIL)
            {
                failed++;
                continue;
            }
            EduStudentImportVo vo = JSON.parseObject(row.getRawData(), EduStudentImportVo.class);
            EduClass clazz = eduClassMapper.selectByTermAndCode(batch.getTermId(), trim(vo.getClassCode()));
            if (clazz == null)
            {
                row.setResult(ROW_FAIL);
                row.setFailReason("班级编码「" + vo.getClassCode() + "」不存在，可能已被删除");
                failed++;
                continue;
            }
            try
            {
                if (row.getResult() == ROW_UPDATE)
                {
                    updateMembership(vo.getStudentNo(), clazz);
                    touchedClass.put(clazz.getClassId(), 1);
                    updated++;
                }
                else
                {
                    EduStudent student = new EduStudent();
                    student.setStudentNo(trim(vo.getStudentNo()));
                    student.setNickName(trim(vo.getRealName()));
                    student.setPhonenumber(trim(vo.getPhone()));
                    student.setClassId(clazz.getClassId());
                    student.setTermId(batch.getTermId());
                    eduStudentService.addStudent(student);
                    touchedClass.put(clazz.getClassId(), 1);
                    created++;
                }
            }
            catch (Exception e)
            {
                row.setResult(ROW_FAIL);
                row.setFailReason(StringUtils.isEmpty(e.getMessage()) ? "入库失败" : e.getMessage());
                failed++;
                if (errors.size() < 20)
                {
                    errors.add("第 " + row.getRowNo() + " 行：" + row.getFailReason());
                }
            }
        }

        // 人数统一重算，避免逐行 +/- 误差
        for (Long classId : touchedClass.keySet())
        {
            eduClassMapper.updateStudentCount(classId, eduClassMapper.countCurrentStudents(classId));
        }

        // 逐行结果回写（覆盖预检结果，反映真实入库情况）
        eduImportMapper.deleteRowsByBatch(batchId);
        eduImportMapper.batchInsertRows(rows);

        EduImportBatch done = new EduImportBatch();
        done.setBatchId(batchId);
        done.setTotalRows(total);
        done.setSuccessRows(created);
        done.setUpdateRows(updated);
        done.setFailRows(failed);
        done.setStatus(BATCH_CONFIRMED);
        done.setFinishTime(new Date());
        eduImportMapper.updateBatch(done);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("totalRows", total);
        result.put("createdRows", created);
        result.put("updatedRows", updated);
        result.put("failedRows", failed);
        result.put("errors", errors);
        result.put("message", "导入完成：新增 " + created + " 个账号，更新班级 " + updated + " 个，失败 " + failed + " 行");
        return result;
    }

    @Override
    public EduImportBatch selectBatchById(Long batchId)
    {
        return eduImportMapper.selectBatchById(batchId);
    }

    @Override
    public List<EduImportBatch> selectBatchList(EduImportBatch query)
    {
        return eduImportMapper.selectBatchList(query);
    }

    @Override
    public List<EduImportRow> selectFailRows(Long batchId)
    {
        return eduImportMapper.selectRows(batchId, ROW_FAIL);
    }

    /**
     * 已存在账号改为新班级：关闭原归属、建立新归属（保留历史归属，MG-01-06）
     */
    private void updateMembership(String studentNo, EduClass clazz)
    {
        Long userId = eduStudentMapper.selectUserIdByStudentNo(trim(studentNo));
        if (userId == null)
        {
            throw new ServiceException("学号「" + studentNo + "」对应账号不存在，无法更新班级");
        }
        eduStudentMapper.closeCurrentMembership(userId, null);
        eduStudentMapper.insertMembership(clazz.getClassId(), userId, clazz.getTermId(), currentUsername());
        eduStudentMapper.updateProfile(userId, null, clazz.getMajorId(), clazz.getGradeYear());
    }

    private EduTerm resolveTerm(Long termId)
    {
        EduTerm term = termId != null ? eduTermMapper.selectEduTermById(termId) : eduTermMapper.selectCurrentTerm();
        if (term == null)
        {
            throw new ServiceException(termId != null ? "指定学期不存在" : "尚未设置当前学期，请先切换当前学期");
        }
        return term;
    }

    private EduClass termScoped(Long termId)
    {
        EduClass query = new EduClass();
        query.setTermId(termId);
        return query;
    }

    private String trim(String s)
    {
        return s == null ? null : s.trim();
    }

    private double round(double v)
    {
        return Math.round(v * 10000D) / 10000D;
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
