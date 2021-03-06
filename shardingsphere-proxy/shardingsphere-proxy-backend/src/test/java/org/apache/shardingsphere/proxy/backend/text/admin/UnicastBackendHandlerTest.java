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

package org.apache.shardingsphere.proxy.backend.text.admin;

import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.auth.Authentication;
import org.apache.shardingsphere.infra.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.kernel.context.SchemaContext;
import org.apache.shardingsphere.kernel.context.SchemaContexts;
import org.apache.shardingsphere.proxy.backend.communication.DatabaseCommunicationEngine;
import org.apache.shardingsphere.proxy.backend.communication.DatabaseCommunicationEngineFactory;
import org.apache.shardingsphere.proxy.backend.communication.jdbc.connection.BackendConnection;
import org.apache.shardingsphere.proxy.backend.response.BackendResponse;
import org.apache.shardingsphere.proxy.backend.response.update.UpdateResponse;
import org.apache.shardingsphere.proxy.backend.schema.ProxySchemaContexts;
import org.apache.shardingsphere.transaction.core.TransactionType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public final class UnicastBackendHandlerTest {
    
    @Spy
    private BackendConnection backendConnection = new BackendConnection(TransactionType.LOCAL);
    
    @Mock
    private DatabaseCommunicationEngineFactory databaseCommunicationEngineFactory;
    
    @Before
    @SneakyThrows(ReflectiveOperationException.class)
    public void setUp() {
        Field schemaContexts = ProxySchemaContexts.getInstance().getClass().getDeclaredField("schemaContexts");
        schemaContexts.setAccessible(true);
        schemaContexts.set(ProxySchemaContexts.getInstance(), new SchemaContexts(getSchemaContextMap(), new ConfigurationProperties(new Properties()), new Authentication()));
        setUnderlyingHandler(new UpdateResponse());
    }
    
    private Map<String, SchemaContext> getSchemaContextMap() {
        Map<String, SchemaContext> result = new HashMap<>(10);
        for (int i = 0; i < 10; i++) {
            result.put("schema_" + i, mock(SchemaContext.class));
        }
        return result;
    }
    
    @Test
    public void assertExecuteWhileSchemaIsNull() {
        UnicastBackendHandler backendHandler = new UnicastBackendHandler("show variable like %s", backendConnection);
        backendConnection.setCurrentSchema("schema_8");
        setDatabaseCommunicationEngine(backendHandler);
        BackendResponse actual = backendHandler.execute();
        assertThat(actual, instanceOf(UpdateResponse.class));
        backendHandler.execute();
    }
    
    @Test
    public void assertExecuteWhileSchemaNotNull() {
        backendConnection.setCurrentSchema("schema_0");
        UnicastBackendHandler backendHandler = new UnicastBackendHandler("show variable like %s", backendConnection);
        setDatabaseCommunicationEngine(backendHandler);
        BackendResponse actual = backendHandler.execute();
        assertThat(actual, instanceOf(UpdateResponse.class));
        backendHandler.execute();
    }
    
    private void setUnderlyingHandler(final BackendResponse backendResponse) {
        DatabaseCommunicationEngine databaseCommunicationEngine = mock(DatabaseCommunicationEngine.class);
        when(databaseCommunicationEngine.execute()).thenReturn(backendResponse);
        when(databaseCommunicationEngineFactory.newTextProtocolInstance(any(), anyString(), any())).thenReturn(databaseCommunicationEngine);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void setDatabaseCommunicationEngine(final UnicastBackendHandler unicastSchemaBackendHandler) {
        Field field = unicastSchemaBackendHandler.getClass().getDeclaredField("databaseCommunicationEngineFactory");
        field.setAccessible(true);
        field.set(unicastSchemaBackendHandler, databaseCommunicationEngineFactory);
    }
}
