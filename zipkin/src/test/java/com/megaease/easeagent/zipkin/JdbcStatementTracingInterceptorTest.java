/*
 * Copyright (c) 2017, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent.zipkin;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import com.megaease.easeagent.core.utils.SQLCompression;
import com.megaease.easeagent.core.jdbc.JdbcContextInfo;
import com.megaease.easeagent.zipkin.jdbc.JdbcStatementTracingInterceptor;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JdbcStatementTracingInterceptorTest extends BaseZipkinTest {

    @Test
    public void success() {
        Map<String, String> spanInfoMap = new HashMap<>();
        StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

        Tracer tracer = Tracing.newBuilder()
                .currentTraceContext(currentTraceContext)
                .addSpanHandler(new SpanHandler() {
                    @Override
                    public boolean end(TraceContext context, MutableSpan span, Cause cause) {
                        Map<String, String> tmpMap = new HashMap<>(span.tags());
                        tmpMap.put("name", span.name());
                        tmpMap.put("remoteServiceName", span.remoteServiceName());
                        tmpMap.put("remotePort", span.remotePort() + "");
                        tmpMap.put("remoteIp", span.remoteIp() + "");
                        tmpMap.put("kind", span.kind().name());
                        spanInfoMap.putAll(tmpMap);
                        return super.end(context, span, cause);
                    }
                })
                .build().tracer();


        ScopedSpan root = tracer.startScopedSpan("root");

        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        try {
            when(metaData.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/demo?useUnicode=true&characterEncoding=utf-8&autoReconnectForPools=true&autoReconnect=true");
            when(connection.getMetaData()).thenReturn(metaData);
            when(connection.getCatalog()).thenReturn("demo");
        } catch (SQLException ignored) {
        }

        String sql = "select * from user";

        JdbcContextInfo jdbcContextInfo = JdbcContextInfo.create();
        Map<Object, Object> context = new HashMap<>();
        context.put(JdbcContextInfo.class, jdbcContextInfo);

        jdbcContextInfo.updateOnCreateStatement(connection, statement, sql);

        JdbcStatementTracingInterceptor interceptor = new JdbcStatementTracingInterceptor(SQLCompression.DEFAULT);
        interceptor.before(statement, "execute", new Object[]{sql}, context);
        interceptor.after(statement, "execute", new Object[]{sql}, null, null, context);
        root.finish();

        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put(JdbcStatementTracingInterceptor.SPAN_SQL_QUERY_TAG_NAME, sql);
        expectedMap.put(JdbcStatementTracingInterceptor.SPAN_URL, "jdbc:mysql://127.0.0.1:3306/demo");
        expectedMap.put(JdbcStatementTracingInterceptor.SPAN_LOCAL_COMPONENT_TAG_NAME, "database");
        expectedMap.put("name", "execute");
        expectedMap.put("remoteServiceName", "demo");
        expectedMap.put("remotePort", "3306");
        expectedMap.put("remoteIp", "127.0.0.1");
        expectedMap.put("kind", Span.Kind.CLIENT.name());
        Assert.assertEquals(expectedMap, spanInfoMap);
    }

}