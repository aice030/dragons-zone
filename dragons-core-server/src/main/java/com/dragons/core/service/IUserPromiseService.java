package com.dragons.core.service;

import com.dragons.core.entity.UserPromise;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户上传前承诺记录 服务类
 * </p>
 *
 * @author aice
 * @since 2026-02-27
 */
public interface IUserPromiseService extends IService<UserPromise> {

    /**
     * 为指定用户记录一次上传前承诺
     *
     * @param userId 当前登录用户ID（也是承诺记录的 user_id）
     */
    void recordUserPromise(Long userId);
}
