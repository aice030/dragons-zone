package com.dragons.core.service.dbwrite;

import com.dragons.core.dao.TreeHoleMessageMapper;
import com.dragons.core.entity.TreeHoleMessage;
import com.dragons.core.retry.DbWriteRetry;
import com.dragons.core.retry.DbWriteReturnedFalseException;
import org.springframework.stereotype.Service;

/** 树洞留言写库服务：承载留言表的重试写操作。 */
@Service
public class TreeHoleMessageDbWriteService {

    private final TreeHoleMessageMapper treeHoleMessageMapper;

    public TreeHoleMessageDbWriteService(TreeHoleMessageMapper treeHoleMessageMapper) {
        this.treeHoleMessageMapper = treeHoleMessageMapper;
    }

    /** 按主键更新留言，按 3 次重试。 */
    @DbWriteRetry(maxAttempts = 3)
    public void updateById(TreeHoleMessage message) {
        int rows = treeHoleMessageMapper.updateById(message);
        if (rows <= 0) {
            throw new DbWriteReturnedFalseException("treeHoleMessage.updateById");
        }
    }
}
