package com.dragons.core.service.dbwrite;

import com.dragons.core.dao.TreeHoleMapper;
import com.dragons.core.entity.TreeHole;
import com.dragons.core.retry.DbWriteRetry;
import com.dragons.core.retry.DbWriteReturnedFalseException;
import org.springframework.stereotype.Service;

/** 树洞写库服务：承载树洞表的重试写操作。 */
@Service
public class TreeHoleDbWriteService {

    private final TreeHoleMapper treeHoleMapper;

    public TreeHoleDbWriteService(TreeHoleMapper treeHoleMapper) {
        this.treeHoleMapper = treeHoleMapper;
    }

    /** 按主键更新树洞状态等字段，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void updateById(TreeHole treeHole) {
        int rows = treeHoleMapper.updateById(treeHole);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("treeHole.updateById");
        }
    }
}
