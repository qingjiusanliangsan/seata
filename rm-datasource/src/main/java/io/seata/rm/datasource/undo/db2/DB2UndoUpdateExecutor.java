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

import java.util.List;
import java.util.stream.Collectors;

import io.seata.common.exception.ShouldNeverHappenException;
import io.seata.common.util.CollectionUtils;
import io.seata.rm.datasource.ColumnUtils;
import io.seata.rm.datasource.SqlGenerateUtils;
import io.seata.rm.datasource.sql.struct.Field;
import io.seata.rm.datasource.sql.struct.Row;
import io.seata.rm.datasource.sql.struct.TableRecords;
import io.seata.rm.datasource.undo.AbstractUndoExecutor;
import io.seata.rm.datasource.undo.SQLUndoLog;
import io.seata.sqlparser.util.JdbcConstants;

public class DB2UndoUpdateExecutor extends AbstractUndoExecutor {

    /**
     * Instantiates a new DB2 undo update executor.
     *
     * @param sqlUndoLog the sql undo log
     */
    public DB2UndoUpdateExecutor(SQLUndoLog sqlUndoLog) {
        super(sqlUndoLog);
    }

    /**
     * UPDATE a SET x = ?, y = ?, z = ? WHERE pk1 = ? and pk2 = ?
     */
    private static final String UPDATE_SQL_TEMPLATE = "UPDATE %s SET %s WHERE %s ";

    @Override
    protected String buildUndoSQL() {
        TableRecords beforeImage = sqlUndoLog.getBeforeImage();
        List<Row> beforeImageRows = beforeImage.getRows();
        if (CollectionUtils.isEmpty(beforeImageRows)) {
            throw new ShouldNeverHappenException("Invalid UNDO LOG"); // TODO
        }
        Row row = beforeImageRows.get(0);

        List<Field> nonPkFields = row.nonPrimaryKeys();
        // update sql undo log before image all field come from table meta. need add escape.
        // see BaseTransactionalExecutor#buildTableRecords
        String updateColumns = nonPkFields.stream().map(
                field -> ColumnUtils.addEscape(field.getName(), JdbcConstants.POSTGRESQL) + " = ?").collect(
                Collectors.joining(", "));

        List<String> pkNameList = getOrderedPkList(beforeImage, row, JdbcConstants.POSTGRESQL).stream().map(
                e -> e.getName()).collect(Collectors.toList());
        String whereSql = SqlGenerateUtils.buildWhereConditionByPKs(pkNameList, JdbcConstants.POSTGRESQL);

        return String.format(UPDATE_SQL_TEMPLATE, sqlUndoLog.getTableName(), updateColumns, whereSql);
    }

    @Override
    protected TableRecords getUndoRows() {
        return sqlUndoLog.getBeforeImage();
    }
}
