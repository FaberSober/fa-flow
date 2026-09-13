package com.faber.api.flow.core.biz;

import java.io.Serializable;
import java.util.Collections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aizuda.bpm.engine.dao.FlwExtInstanceDao;
import com.aizuda.bpm.engine.dao.FlwHisTaskActorDao;
import com.aizuda.bpm.engine.dao.FlwHisTaskDao;
import com.faber.api.flow.core.entity.FaFlwHisInstance;
import com.faber.api.flow.core.mapper.FaFlwHisInstanceMapper;
import com.faber.core.web.biz.BaseBiz;

import jakarta.annotation.Resource;

/**
 * 历史流程实例表
 *
 * @author xu.pengfei
 * @email 1508075252@qq.com
 * @date 2025-08-25 17:26:47
 */
@Service
public class FlwHisInstanceBiz extends BaseBiz<FaFlwHisInstanceMapper, FaFlwHisInstance> {

    @Resource FlwExtInstanceDao flwExtInstanceDao;
    @Resource FlwHisTaskDao flwHisTaskDao;
    @Resource FlwHisTaskActorDao flwHisTaskActorDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        Long instanceId = toLong(id);
        flwHisTaskActorDao.deleteByInstanceIds(Collections.singletonList(instanceId));
        flwHisTaskDao.deleteByInstanceIds(Collections.singletonList(instanceId));
        flwExtInstanceDao.deleteById(instanceId);
        return super.removeById(id);
    }

    private Long toLong(Serializable id) {
        if (id instanceof Number number) return number.longValue();
        return Long.valueOf(id.toString());
    }
}
