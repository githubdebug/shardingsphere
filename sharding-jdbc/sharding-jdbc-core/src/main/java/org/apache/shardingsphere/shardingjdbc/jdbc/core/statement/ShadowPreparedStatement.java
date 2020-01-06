/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.jdbc.core.statement;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.route.SQLLogger;
import org.apache.shardingsphere.core.route.SQLUnit;
import org.apache.shardingsphere.shadow.rewrite.PreparedJudgementEngine;
import org.apache.shardingsphere.shadow.rewrite.ShadowJudgementEngine;
import org.apache.shardingsphere.shadow.rewrite.context.ShadowSQLRewriteContextDecorator;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.AbstractShardingPreparedStatementAdapter;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.connection.ShadowConnection;
import org.apache.shardingsphere.sql.parser.relation.SQLStatementContextFactory;
import org.apache.shardingsphere.sql.parser.relation.metadata.RelationMetaData;
import org.apache.shardingsphere.sql.parser.relation.metadata.RelationMetas;
import org.apache.shardingsphere.sql.parser.relation.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.underlying.common.constant.properties.PropertiesConstant;
import org.apache.shardingsphere.underlying.common.metadata.table.TableMetaData;
import org.apache.shardingsphere.underlying.common.metadata.table.TableMetas;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.impl.DefaultSQLRewriteEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Shadow prepared statement.
 *
 * @author zhyee
 */
public final class ShadowPreparedStatement extends AbstractShardingPreparedStatementAdapter {
    
    private final ShadowPreparedStatementGenerator preparedStatementGenerator;
    
    private final String sql;
    
    private ShadowJudgementEngine shadowJudgementEngine;
    
    private PreparedStatement preparedStatement;
    
    private ResultSet resultSet;
    
    private final Collection<SQLUnit> sqlUnits = new LinkedList<>();
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql) {
        this(connection, sql, -1, -1, -1, -1, null, null);
    }
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql, final int resultSetType, final int resultSetConcurrency) {
        this(connection, sql, resultSetType, resultSetConcurrency, -1, -1, null, null);
    }
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        this(connection, sql, resultSetType, resultSetConcurrency, resultSetHoldability, -1, null, null);
    }
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql, final int autoGeneratedKeys) {
        this(connection, sql, -1, -1, -1, autoGeneratedKeys, null, null);
    }
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql, final int[] columnIndexes) {
        this(connection, sql, -1, -1, -1, -1, columnIndexes, null);
    }
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql, final String[] columnNames) {
        this(connection, sql, -1, -1, -1, -1, null, columnNames);
    }
    
    public ShadowPreparedStatement(final ShadowConnection connection, final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability,
                                   final int autoGeneratedKeys, final int[] columnIndexes, final String[] columnNames) {
        this.sql = sql;
        preparedStatementGenerator = new ShadowPreparedStatementGenerator(connection, resultSetType, resultSetConcurrency, resultSetHoldability, autoGeneratedKeys, columnIndexes, columnNames);
    }
    
    @Override
    public ResultSet executeQuery() throws SQLException {
        try {
            SQLUnit sqlUnit = getSQLUnit(sql);
            preparedStatement = preparedStatementGenerator.createPreparedStatement(sqlUnit.getSql());
            replayMethodsInvocation(preparedStatement);
            replaySetParameter(preparedStatement, sqlUnit.getParameters());
            resultSet = preparedStatement.executeQuery();
            return resultSet;
        } finally {
            clearParameters();
        }
    }
    
    @Override
    public int executeUpdate() throws SQLException {
        try {
            SQLUnit sqlUnit = getSQLUnit(sql);
            preparedStatement = preparedStatementGenerator.createPreparedStatement(sqlUnit.getSql());
            replayMethodsInvocation(preparedStatement);
            replaySetParameter(preparedStatement, sqlUnit.getParameters());
            return preparedStatement.executeUpdate();
        } finally {
            clearParameters();
        }
    }
    
    @Override
    public boolean execute() throws SQLException {
        try {
            SQLUnit sqlUnit = getSQLUnit(sql);
            preparedStatement = preparedStatementGenerator.createPreparedStatement(sqlUnit.getSql());
            replayMethodsInvocation(preparedStatement);
            replaySetParameter(preparedStatement, sqlUnit.getParameters());
            boolean result = preparedStatement.execute();
            resultSet = preparedStatement.getResultSet();
            return result;
        } finally {
            clearParameters();
        }
    }
    
    @Override
    public void addBatch() {
        sqlUnits.add(getSQLUnit(sql));
        clearParameters();
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        if (0 == sqlUnits.size()) {
            return new int[0];
        }
        try {
            preparedStatement = preparedStatementGenerator.createPreparedStatement(sqlUnits.iterator().next().getSql());
            replayMethodsInvocation(preparedStatement);
            replayBatchPreparedStatement();
            return preparedStatement.executeBatch();
        } finally {
            clearBatch();
        }
    }
    
    private void replayBatchPreparedStatement() throws SQLException {
        for (SQLUnit each : sqlUnits) {
            replaySetParameter(preparedStatement, each.getParameters());
            preparedStatement.addBatch();
        }
    }
    
    @SuppressWarnings("unchecked")
    private SQLUnit getSQLUnit(final String sql) {
        ShadowConnection connection = preparedStatementGenerator.connection;
        SQLStatement sqlStatement = connection.getRuntimeContext().getParseEngine().parse(sql, true);
        SQLStatementContext sqlStatementContext = SQLStatementContextFactory.newInstance(
                getRelationMetas(connection.getRuntimeContext().getMetaData().getTables()), sql, getParameters(), sqlStatement);
        shadowJudgementEngine = new PreparedJudgementEngine(connection.getRuntimeContext().getRule(), sqlStatementContext, getParameters());
        SQLRewriteContext sqlRewriteContext = new SQLRewriteContext(getRelationMetas(connection.getRuntimeContext().getMetaData().getTables()), sqlStatementContext, sql, getParameters());
        new ShadowSQLRewriteContextDecorator().decorate(connection.getRuntimeContext().getRule(), connection.getRuntimeContext().getProperties(), sqlRewriteContext);
        sqlRewriteContext.generateSQLTokens();
        SQLRewriteResult sqlRewriteResult = new DefaultSQLRewriteEngine().rewrite(sqlRewriteContext);
        showSQL(sqlRewriteResult.getSql());
        return new SQLUnit(sqlRewriteResult.getSql(), sqlRewriteResult.getParameters());
    }
    
    private RelationMetas getRelationMetas(final TableMetas tableMetas) {
        Map<String, RelationMetaData> result = new HashMap<>(tableMetas.getAllTableNames().size());
        for (String each : tableMetas.getAllTableNames()) {
            TableMetaData tableMetaData = tableMetas.get(each);
            result.put(each, new RelationMetaData(tableMetaData.getColumns().keySet()));
        }
        return new RelationMetas(result);
    }
    
    private void showSQL(final String sql) {
        boolean showSQL = preparedStatementGenerator.connection.getRuntimeContext().getProperties().<Boolean>getValue(PropertiesConstant.SQL_SHOW);
        if (showSQL) {
            //todo
            SQLLogger.logShadowSQL(sql, "");
        }
    }
    
    @Override
    protected boolean isAccumulate() {
        return false;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected Collection<? extends Statement> getRoutedStatements() {
        Collection<Statement> result = new LinkedList();
        if (null == preparedStatement) {
            return result;
        }
        result.add(preparedStatement);
        return result;
    }
    
    @Override
    public ResultSet getResultSet() {
        return resultSet;
    }
    
    @Override
    public int getResultSetConcurrency() {
        return preparedStatementGenerator.resultSetConcurrency;
    }
    
    @Override
    public int getResultSetType() {
        return preparedStatementGenerator.resultSetType;
    }
    
    @Override
    public Connection getConnection() {
        return preparedStatementGenerator.connection;
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return preparedStatement.getGeneratedKeys();
    }
    
    @Override
    public int getResultSetHoldability() {
        return preparedStatementGenerator.resultSetHoldability;
    }
    
    @Override
    public void clearBatch() throws SQLException {
        if (null != preparedStatement) {
            preparedStatement.clearBatch();
        }
        sqlUnits.clear();
        clearParameters();
    }
    
    @RequiredArgsConstructor
    private final class ShadowPreparedStatementGenerator {
        
        private final ShadowConnection connection;
        
        private final int resultSetType;
        
        private final int resultSetConcurrency;
        
        private final int resultSetHoldability;
        
        private final int autoGeneratedKeys;
        
        private final int[] columnIndexes;
        
        private final String[] columnNames;
        
        private PreparedStatement createPreparedStatement(final String sql) throws SQLException {
            if (-1 != resultSetType && -1 != resultSetConcurrency && -1 != resultSetHoldability) {
                return shadowJudgementEngine.isShadowSQL() ? connection.getShadowConnection().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability)
                        : connection.getActualConnection().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
            }
            if (-1 != resultSetType && -1 != resultSetConcurrency) {
                return shadowJudgementEngine.isShadowSQL() ? connection.getShadowConnection().prepareStatement(sql, resultSetType, resultSetConcurrency)
                        : connection.getActualConnection().prepareStatement(sql, resultSetType, resultSetConcurrency);
            }
            if (-1 != autoGeneratedKeys) {
                return shadowJudgementEngine.isShadowSQL() ? connection.getShadowConnection().prepareStatement(sql, autoGeneratedKeys)
                        : connection.getActualConnection().prepareStatement(sql, autoGeneratedKeys);
            }
            if (null != columnIndexes) {
                return shadowJudgementEngine.isShadowSQL() ? connection.getShadowConnection().prepareStatement(sql, columnIndexes)
                        : connection.getActualConnection().prepareStatement(sql, columnIndexes);
            }
            if (null != columnNames) {
                return shadowJudgementEngine.isShadowSQL() ? connection.getShadowConnection().prepareStatement(sql, columnNames)
                        : connection.getActualConnection().prepareStatement(sql, columnNames);
            }
            return shadowJudgementEngine.isShadowSQL() ? connection.getShadowConnection().prepareStatement(sql)
                    : connection.getActualConnection().prepareStatement(sql);
        }
    }
}
