package com.dragons.core.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dragons.core.dao.UserPromiseMapper;
import com.dragons.core.dto.ResponseCode;
import com.dragons.core.entity.UserPromise;
import com.dragons.core.exception.BusinessException;
import com.dragons.core.service.IUserPromiseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户上传前承诺记录 服务实现类
 * </p>
 *
 * @author aice
 * @since 2026-02-27
 */
@Slf4j
@Service
public class UserPromiseServiceImpl extends ServiceImpl<UserPromiseMapper, UserPromise> implements IUserPromiseService {

    @Override
    public void recordUserPromise(Long userId) {
        if (userId == null || userId <= 0) {
            log.warn("recordUserPromise invalid userId={}", userId);
            throw new BusinessException(ResponseCode.BAD_REQUEST);
        }

        UserPromise promise = new UserPromise();
        promise.setUserId(userId);
        // agree_time 使用数据库默认 CURRENT_TIMESTAMP

        boolean ok = this.save(promise);
        if (!ok) {
            log.error("recordUserPromise save failed userId={}", userId);
            throw new BusinessException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
