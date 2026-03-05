package com.dragons.core.service.dbwrite;

import com.dragons.core.dao.UserMapper;
import com.dragons.core.entity.User;
import com.dragons.core.retry.DbWriteRetry;
import com.dragons.core.retry.DbWriteReturnedFalseException;
import org.springframework.stereotype.Service;

/**
 * 用户写库服务。
 *
 * 说明：
 * - 只放纯 DB 写操作，不放业务编排逻辑。
 * - 通过 @DbWriteRetry 让切面自动处理重试。
 */
@Service
public class UserDbWriteService {

    private final UserMapper userMapper;

    public UserDbWriteService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 插入用户，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void insert(User user) {
        int rows = userMapper.insert(user);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("user.insert");
        }
    }

    /** 按主键更新用户，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void updateById(User user) {
        int rows = userMapper.updateById(user);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("user.updateById");
        }
    }
}
