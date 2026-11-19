package com.group4.kaoyan.modules.user.service;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.mybatis.EntityTimestamps;
import com.group4.kaoyan.modules.user.dto.StudyGoalDTO;
import com.group4.kaoyan.modules.user.dto.UserProfileDTO;
import com.group4.kaoyan.modules.user.entity.UserProfile;
import com.group4.kaoyan.modules.user.entity.UserStudyGoal;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import com.group4.kaoyan.modules.user.mapper.UserStudyGoalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 画像与备考目标的持久化。
 *
 * <p>全部使用原生 MyBatis：手写 SQL、显式时间戳、手写乐观锁与逻辑删除，
 * 不依赖 MyBatis-Plus 的 BaseMapper / MetaObjectHandler / @Version。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int STATUS_ACTIVE = 1;

    private final UserProfileMapper profileMapper;
    private final UserStudyGoalMapper goalMapper;

    // ============ 画像 ============

    /** 无记录时返回空对象，不抛 404（前端首次进入画像页是正常场景）。 */
    public UserProfile getProfile(Long userId) {
        UserProfile profile = profileMapper.selectByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setWeeklyStudyMinutes(0);
        }
        return profile;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfile saveProfile(Long userId, UserProfileDTO dto) {
        if (dto == null) {
            throw new BizException("画像参数不能为空");
        }
        if (dto.weeklyStudyMinutes() != null
                && (dto.weeklyStudyMinutes() < 0 || dto.weeklyStudyMinutes() > 10080)) {
            throw new BizException("每周学习分钟数不合法");
        }
        if (dto.gender() != null && (dto.gender() < 0 || dto.gender() > 2)) {
            throw new BizException("性别取值不合法");
        }
        if (dto.baseLevel() != null && (dto.baseLevel() < 1 || dto.baseLevel() > 4)) {
            throw new BizException("基础水平取值不合法");
        }
        if (dto.major() != null && dto.major().length() > 100) {
            throw new BizException("专业名称过长");
        }
        if (dto.grade() != null && dto.grade().length() > 32) {
            throw new BizException("年级名称过长");
        }

        UserProfile entity = new UserProfile();
        entity.setUserId(userId);
        entity.setGender(dto.gender());
        entity.setMajor(trim(dto.major()));
        entity.setGrade(trim(dto.grade()));
        entity.setBaseLevel(dto.baseLevel());
        entity.setWeeklyStudyMinutes(dto.weeklyStudyMinutes() == null ? 0 : dto.weeklyStudyMinutes());
        entity.setWeakPointsJson(dto.weakPoints());
        entity.setProfileJson(dto.profile());

        UserProfile existing = profileMapper.selectByUserId(userId);
        if (existing == null) {
            EntityTimestamps.fillOnInsert(entity);
            try {
                // 先查后插仍有并发窗口，靠 uk_user_profile_user 唯一索引兜底
                profileMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                log.debug("并发创建画像冲突，转为更新 userId={}", userId);
                EntityTimestamps.fillOnUpdate(entity);
                if (profileMapper.updateByUserId(entity) == 0) {
                    throw new BizException(409, "画像保存冲突，请刷新后重试");
                }
            }
        } else {
            EntityTimestamps.fillOnUpdate(entity);
            profileMapper.updateByUserId(entity);
        }
        return profileMapper.selectByUserId(userId);
    }

    // ============ 备考目标 ============

    public List<UserStudyGoal> listGoals(Long userId, Integer status) {
        return goalMapper.selectByUserId(userId, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserStudyGoal createGoal(Long userId, StudyGoalDTO dto) {
        validateGoal(dto, true);
        UserStudyGoal goal = new UserStudyGoal();
        goal.setUserId(userId);
        fillGoal(goal, dto);
        goal.setPlanType(dto.planType() == null ? 1 : dto.planType());
        goal.setStatus(dto.status() == null ? STATUS_ACTIVE : dto.status());
        EntityTimestamps.fillOnInsert(goal);
        try {
            goalMapper.insert(goal);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该年度目标已存在");
        }
        return goal;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserStudyGoal updateGoal(Long userId, Long id, StudyGoalDTO dto) {
        UserStudyGoal existing = goalMapper.selectById(id);
        if (existing == null || !userId.equals(existing.getUserId())) {
            throw new BizException(404, "备考目标不存在");
        }
        validateGoal(dto, false);

        UserStudyGoal goal = new UserStudyGoal();
        goal.setId(id);
        goal.setUserId(userId);
        fillGoal(goal, dto);
        goal.setPlanType(dto.planType() == null ? existing.getPlanType() : dto.planType());
        goal.setStatus(dto.status() == null ? existing.getStatus() : dto.status());
        goal.setVersion(existing.getVersion());
        EntityTimestamps.fillOnUpdate(goal);
        try {
            if (goalMapper.updateWithVersion(goal) == 0) {
                // 版本号已变（被其他请求改过），或记录已被逻辑删除
                throw new BizException(409, "数据已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该年度目标已存在");
        }
        return goalMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteGoal(Long userId, Long id) {
        if (goalMapper.logicDelete(id, userId) == 0) {
            throw new BizException(404, "备考目标不存在");
        }
    }

    // ============ 内部 ============

    private void validateGoal(StudyGoalDTO dto, boolean requireExamYear) {
        if (dto == null) {
            throw new BizException("备考目标参数不能为空");
        }
        if (dto.examYear() == null) {
            if (requireExamYear) {
                throw new BizException("考试年度不能为空");
            }
        } else if (dto.examYear() < 2000 || dto.examYear() > 2100) {
            throw new BizException("考试年度不合法");
        }
        if (dto.targetScore() != null && dto.targetScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("目标总分不能为负数");
        }
        if (dto.targetSchoolName() != null && dto.targetSchoolName().length() > 100) {
            throw new BizException("目标院校名称过长");
        }
        if (dto.targetMajor() != null && dto.targetMajor().length() > 100) {
            throw new BizException("目标专业名称过长");
        }
        if (dto.remark() != null && dto.remark().length() > 500) {
            throw new BizException("备注过长（最多 500 字）");
        }
    }

    private void fillGoal(UserStudyGoal goal, StudyGoalDTO dto) {
        goal.setTargetSchoolId(dto.targetSchoolId());
        goal.setTargetSchoolName(trim(dto.targetSchoolName()));
        goal.setTargetMajor(trim(dto.targetMajor()));
        goal.setTargetScore(dto.targetScore());
        goal.setTargetSubjectScores(dto.targetSubjectScores());
        goal.setExamYear(dto.examYear());
        goal.setExamDate(dto.examDate());
        goal.setRemark(dto.remark());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
