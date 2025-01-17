/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm.datasource.undo.db2;
/**
 * @author qingjiusanliangsan
 */


import io.seata.common.loader.LoadLevel;
import io.seata.rm.datasource.undo.AbstractUndoExecutor;
import io.seata.rm.datasource.undo.SQLUndoLog;
import io.seata.rm.datasource.undo.UndoExecutorHolder;
import io.seata.rm.datasource.undo.db2.DB2UndoDeleteExecutor;
import io.seata.rm.datasource.undo.db2.DB2UndoInsertExecutor;
import io.seata.rm.datasource.undo.db2.DB2UndoUpdateExecutor;
import io.seata.sqlparser.util.JdbcConstants;

@LoadLevel(name = JdbcConstants.DB2)
public class DB2UndoExcutorHolder implements UndoExecutorHolder {
    @Override
    public AbstractUndoExecutor getInsertExecutor(SQLUndoLog sqlUndoLog) {
        return new DB2UndoInsertExecutor(sqlUndoLog);
    }

    @Override
    public AbstractUndoExecutor getUpdateExecutor(SQLUndoLog sqlUndoLog) {
        return new DB2UndoUpdateExecutor(sqlUndoLog);
    }

    @Override
    public AbstractUndoExecutor getDeleteExecutor(SQLUndoLog sqlUndoLog) {
        return new DB2UndoDeleteExecutor(sqlUndoLog);
    }
}
