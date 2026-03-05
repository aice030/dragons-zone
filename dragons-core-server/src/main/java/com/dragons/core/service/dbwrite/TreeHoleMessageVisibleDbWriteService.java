package com.dragons.core.service.dbwrite;

import com.dragons.core.dao.TreeHoleMessageVisibleMapper;
import com.dragons.core.entity.TreeHoleMessageVisible;
import com.dragons.core.retry.DbWriteRetry;
import com.dragons.core.retry.DbWriteReturnedFalseException;
import org.springframework.stereotype.Service;

/** 树洞分享可见表写库服务：承载分享记录的重试写操作。 */
@Service
public class TreeHoleMessageVisibleDbWriteService {

    private final TreeHoleMessageVisibleMapper treeHoleMessageVisibleMapper;

    public TreeHoleMessageVisibleDbWriteService(TreeHoleMessageVisibleMapper treeHoleMessageVisibleMapper) {
        this.treeHoleMessageVisibleMapper = treeHoleMessageVisibleMapper;
    }

    /** 新增分享记录，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void insert(TreeHoleMessageVisible visible) {
        int rows = treeHoleMessageVisibleMapper.insert(visible);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("treeHoleMessageVisible.insert");
        }
    }
}
