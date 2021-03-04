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

package com.megaease.easeagent.zipkin.jdbc;

import brave.Span;
import brave.propagation.ThreadLocalSpan;
import com.megaease.easeagent.core.utils.SQLCompression;
import com.megaease.easeagent.core.interceptor.AgentInterceptor;
import com.megaease.easeagent.core.jdbc.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

public class JdbcStatementTracingInterceptor implements AgentInterceptor {

    public static final String SPAN_SQL_QUERY_TAG_NAME = "sql";
    public static final String SPAN_ROW_COUNT_TAG_NAME = "row-count";
    public static final String SPAN_ERROR_TAG_NAME = "error";
    public static final String SPAN_LOCAL_COMPONENT_TAG_NAME = "local-component";
    public static final String SPAN_URL = "url";

    private final SQLCompression sqlCompression;

    public JdbcStatementTracingInterceptor(SQLCompression sqlCompression) {
        this.sqlCompression = sqlCompression;
    }

    @Override
    public void before(Object invoker, String method, Object[] args, Map<Object, Object> context) {
        Optional.ofNullable(ThreadLocalSpan.CURRENT_TRACER.next()).ifPresent(span -> {
            JdbcContextInfo jdbcContextInfo = (JdbcContextInfo) context.get(JdbcContextInfo.class);
            ExecutionInfo executionInfo = jdbcContextInfo.getExecutionInfo((Statement) invoker);
            span.name(method);
            span.kind(Span.Kind.CLIENT);
            span.tag(SPAN_SQL_QUERY_TAG_NAME, sqlCompression.compress(executionInfo.getSql()));
            span.tag(SPAN_LOCAL_COMPONENT_TAG_NAME, "database");
            Connection conn = executionInfo.getConnection();
            String url = JdbcUtils.getUrl(conn);
            if (url != null) {
                span.tag(SPAN_URL, url);
            }
            DatabaseInfo databaseInfo = DatabaseInfo.getFromConnection(conn);
            if (databaseInfo != null) {
                span.remoteServiceName(databaseInfo.getDatabase());
                span.remoteIpAndPort(databaseInfo.getHost(), databaseInfo.getPort());
            }
            span.start();
        });
    }

    @Override
    public void after(Object invoker, String method, Object[] args, Object retValue, Exception exception, Map<Object, Object> context) {
        Optional.ofNullable(ThreadLocalSpan.CURRENT_TRACER.remove()).ifPresent(span -> {
            JdbcContextInfo jdbcContextInfo = (JdbcContextInfo) context.get(JdbcContextInfo.class);
            ExecutionInfo executionInfo = jdbcContextInfo.getExecutionInfo((Statement) invoker);
            if (exception == null) {
//                span.tag(SPAN_ROW_COUNT_TAG_NAME, String.valueOf(executionInfo.getResult()));
            } else {
                span.tag(SPAN_ERROR_TAG_NAME, getExceptionMessage(exception));
            }
            span.finish();
        });
    }

    private String getExceptionMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}