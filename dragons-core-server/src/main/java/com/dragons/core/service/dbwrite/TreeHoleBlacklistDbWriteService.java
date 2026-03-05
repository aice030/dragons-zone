package com.dragons.core.service.dbwrite;

import com.dragons.core.dao.TreeHoleBlacklistMapper;
import com.dragons.core.entity.TreeHoleBlacklist;
import com.dragons.core.retry.DbWriteRetry;
import com.dragons.core.retry.DbWriteReturnedFalseException;
import org.springframework.stereotype.Service;

/** 树洞黑名单写库服务：承载黑名单表的重试写操作。 */
@Service
public class TreeHoleBlacklistDbWriteService {

    private final TreeHoleBlacklistMapper treeHoleBlacklistMapper;

    public TreeHoleBlacklistDbWriteService(TreeHoleBlacklistMapper treeHoleBlacklistMapper) {
        this.treeHoleBlacklistMapper = treeHoleBlacklistMapper;
    }

    /** 新增黑名单记录，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void insert(TreeHoleBlacklist entity) {
        int rows = treeHoleBlacklistMapper.insert(entity);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("treeHoleBlacklist.insert");
        }
    }

    /** 更新黑名单记录，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void updateById(TreeHoleBlacklist entity) {
        int rows = treeHoleBlacklistMapper.updateById(entity);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("treeHoleBlacklist.updateById");
        }
    }
}
