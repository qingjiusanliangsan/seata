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
package io.seata.rm.datasource.exec.db2;

import io.seata.common.exception.NotSupportYetException;
import io.seata.common.exception.ShouldNeverHappenException;
import io.seata.common.util.StringUtils;
import io.seata.rm.datasource.StatementProxy;
import io.seata.rm.datasource.exec.BaseInsertExecutor;
import io.seata.rm.datasource.exec.StatementCallback;
import io.seata.rm.datasource.sql.struct.ColumnMeta;
import io.seata.sqlparser.SQLRecognizer;
import io.seata.sqlparser.struct.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qingjiusanliangsan
 */
public class DB2InsertExecutor extends BaseInsertExecutor implements Defaultable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DB2InsertExecutor.class);

    public static final String ERR_SQL_STATE = "S1009";

    /**
     * The cache of auto increment step of database
     * the key is the db's resource id
     * the value is the step
     */
    public static final Map<String, BigDecimal> RESOURCE_ID_STEP_CACHE = new ConcurrentHashMap<>(8);

    /**
     * Instantiates a new Abstract dml base executor.
     *
     * @param statementProxy    the statement proxy
     * @param statementCallback the statement callback
     * @param sqlRecognizer     the sql recognizer
     */
    public DB2InsertExecutor(StatementProxy statementProxy, StatementCallback statementCallback,
                                SQLRecognizer sqlRecognizer) {
        super(statementProxy, statementCallback, sqlRecognizer);
    }

    @Override
    public Map<String, List<Object>> getPkValues() throws SQLException {
        Map<String,List<Object>> pkValuesMap = null;
        Boolean isContainsPk = containsPK();
        List<String> pkColumnNameList = getTableMeta().getPrimaryKeyOnlyName();
        //when there is only one pk in the table
        if (pkColumnNameList.size() == 1) {
            if (isContainsPk) {
                pkValuesMap = getPkValuesByColumn();
            } else if (containsColumns()) {
                pkValuesMap = getPkValuesByAuto();
            } else {
                pkValuesMap = getPkValuesByColumn();
            }
        } else{
            throw new NotSupportYetException("composite primary key is not supported in db2");
        }
        return pkValuesMap;
    }

    public Map<String, List<Object>> getPkValuesByAuto() throws SQLException {
        // PK is just auto generated
        Map<String, List<Object>> pkValuesMap = new HashMap<>(8);
        Map<String, ColumnMeta> pkMetaMap = getTableMeta().getPrimaryKeyMap();
        String autoColumnName = null;
        for (Map.Entry<String, ColumnMeta> entry : pkMetaMap.entrySet()) {
            if (entry.getValue().isAutoincrement()) {
                autoColumnName = entry.getKey();
                break;
            }
        }
        if (StringUtils.isBlank(autoColumnName)) {
            throw new ShouldNeverHappenException();
        }

        ResultSet genKeys;
        try {
            genKeys = statementProxy.getGeneratedKeys();
        } catch (SQLException e) {
            // java.sql.SQLException: Generated keys not requested. You need to
            // specify Statement.RETURN_GENERATED_KEYS to
            // Statement.executeUpdate() or Connection.prepareStatement().
            if (ERR_SQL_STATE.equalsIgnoreCase(e.getSQLState())) {
                LOGGER.error("Fail to get auto-generated keys, use 'IDENTITY_VAL_LOCAL()' instead. Be cautious, " +
                        "statement could be polluted. Recommend you set the statement to return generated keys.");
                int updateCount = statementProxy.getUpdateCount();
                ResultSet firstId = genKeys = statementProxy.getTargetStatement().executeQuery("IDENTITY_VAL_LOCAL()");

                // If there is batch insert
                // do auto increment base LAST_INSERT_ID and variable `auto_increment_increment`
                if (updateCount > 1 && canAutoIncrement(pkMetaMap)) {
                    firstId.next();
                    return autoGeneratePks(new BigDecimal(firstId.getString(1)), autoColumnName, updateCount);
                }
            } else {
                throw e;
            }
        }
        List<Object> pkValues = new ArrayList<>();
        while (genKeys.next()) {
            Object v = genKeys.getObject(1);
            pkValues.add(v);
        }
        try {
            genKeys.beforeFirst();
        } catch (SQLException e) {
            LOGGER.warn("Fail to reset ResultSet cursor. can not get primary key value");
        }
        pkValuesMap.put(autoColumnName,pkValues);
        return pkValuesMap;
    }

    @Override
    public Map<String,List<Object>> getPkValuesByColumn() throws SQLException {
        Map<String,List<Object>> pkValuesMap  = parsePkValuesFromStatement();
        Set<String> keySet = new HashSet<>(pkValuesMap.keySet());
        //auto increment
        for (String pkKey:keySet) {
            List<Object> pkValues = pkValuesMap.get(pkKey);
            // pk auto generated while single insert primary key is expression
            if (pkValues.size() == 1 && (pkValues.get(0) instanceof SqlMethodExpr)) {
                pkValuesMap.putAll(getPkValuesByAuto());
            }
            // pk auto generated while column exists and value is null
            else if (!pkValues.isEmpty() && pkValues.get(0) instanceof Null) {
                pkValuesMap.putAll(getPkValuesByAuto());
            }
        }
        return pkValuesMap;
    }

    @Override
    public List<Object> getPkValuesByDefault() {
        //Get form the tableMetaData
        throw new NotSupportYetException("Default value is not yet supported");
    }

    protected Map<String, List<Object>> autoGeneratePks(BigDecimal cursor, String autoColumnName, Integer updateCount) throws SQLException {
        BigDecimal step = BigDecimal.ONE;
        String resourceId = statementProxy.getConnectionProxy().getDataSourceProxy().getResourceId();
        if (RESOURCE_ID_STEP_CACHE.containsKey(resourceId)) {
            step = RESOURCE_ID_STEP_CACHE.get(resourceId);
        } else {
            ResultSet increment = statementProxy.getTargetStatement().executeQuery("SHOW VARIABLES LIKE 'auto_increment_increment'");

            increment.next();
            step = new BigDecimal(increment.getString(2));
            RESOURCE_ID_STEP_CACHE.put(resourceId, step);
        }

        List<Object> pkValues = new ArrayList<>();
        for (int i = 0; i < updateCount; i++) {
            pkValues.add(cursor);
            cursor = cursor.add(step);
        }

        Map<String, List<Object>> pkValuesMap = new HashMap<>(1, 1.001f);
        pkValuesMap.put(autoColumnName,pkValues);
        return pkValuesMap;
    }

    protected boolean canAutoIncrement(Map<String, ColumnMeta> primaryKeyMap) {
        if (primaryKeyMap.size() != 1) {
            return false;
        }

        for (ColumnMeta pk : primaryKeyMap.values()) {
            return pk.isAutoincrement();
        }
        return false;
    }


}