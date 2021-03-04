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

package com.megaease.easeagent.sniffer;

import com.megaease.easeagent.core.utils.ContextUtils;
import com.megaease.easeagent.common.ForwardLock;
import com.megaease.easeagent.core.AdviceTo;
import com.megaease.easeagent.core.Definition;
import com.megaease.easeagent.core.Injection;
import com.megaease.easeagent.core.Transformation;
import com.megaease.easeagent.core.interceptor.AgentInterceptor;
import com.megaease.easeagent.core.jdbc.JdbcContextInfo;
import com.megaease.easeagent.core.jdbc.listener.JdbcListenerDispatcher;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.*;

@Injection.Provider(Provider.class)
public abstract class JdbcStatementAdvice implements Transformation {
    @Override
    public <T extends Definition> T define(Definition<T> def) {
        return def.type(isSubTypeOf(Statement.class))
                .transform(execute(nameStartsWith("execute")
                        .and(not(returns(TypeDescription.VOID)))
                        .and(isPublic())
                        .and(not(ElementMatchers.isAbstract()
                                .or(ElementMatchers.isBridge())
                                .or(ElementMatchers.isNative())
                        ))
                ))
                .transform(notExecute(named("addBatch").or(named("close")).or(named("clearBatch"))))
                .type(isSubTypeOf(Connection.class))
                .transform(connectionInvoke(named("createStatement").or(nameStartsWith("prepare"))))
                .end();
    }

    @AdviceTo(Execute.class)
    abstract Definition.Transformer execute(ElementMatcher<? super MethodDescription> matcher);

    @AdviceTo(StatementNotExecute.class)
    abstract Definition.Transformer notExecute(ElementMatcher<? super MethodDescription> matcher);

    @AdviceTo(ConnectionInvoke.class)
    abstract Definition.Transformer connectionInvoke(ElementMatcher<? super MethodDescription> matcher);

    static class BaseNotExecute {

        protected final ForwardLock lock;
        protected final JdbcListenerDispatcher jdbcListenerDispatcher;

        BaseNotExecute() {
            this.lock = new ForwardLock();
            this.jdbcListenerDispatcher = JdbcListenerDispatcher.DEFAULT;
        }

    }

    static class ConnectionInvoke extends BaseNotExecute {

        @Advice.OnMethodEnter
        ForwardLock.Release<Void> enter() {
            return lock.acquire(() -> null);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        void exit(@Advice.Enter ForwardLock.Release<Void> release,
                  @Advice.This Object invoker,
                  @Advice.Origin("#m") String method,
                  @Advice.AllArguments Object[] args,
                  @Advice.Return Object retValue,
                  @Advice.Thrown Exception exception) {
            release.apply(unused -> {
                if (exception == null) {
                    JdbcContextInfo jdbcContextInfo = JdbcContextInfo.getCurrent();
                    jdbcListenerDispatcher.dispatchAfter(jdbcContextInfo, invoker, method, getArgs(args), retValue);
                }
            });
        }
    }

    static class StatementNotExecute extends BaseNotExecute {

        @Advice.OnMethodEnter
        ForwardLock.Release<Void> enter(@Advice.This Object invoker,
                                        @Advice.Origin("#m") String method,
                                        @Advice.AllArguments Object[] args
        ) {
            return lock.acquire(() -> {
                JdbcContextInfo jdbcContextInfo = JdbcContextInfo.getCurrent();
                this.jdbcListenerDispatcher.dispatchBefore(jdbcContextInfo, invoker, method, getArgs(args));
                return null;
            });
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        void exit(@Advice.Enter ForwardLock.Release<Void> release,
                  @Advice.This Object invoker,
                  @Advice.Origin("#m") String method,
                  @Advice.AllArguments Object[] args,
                  @Advice.Thrown Exception exception) {
            release.apply(unused -> {
                if (exception == null) {
                    JdbcContextInfo jdbcContextInfo = JdbcContextInfo.getCurrent();
                    jdbcListenerDispatcher.dispatchAfter(jdbcContextInfo, invoker, method, getArgs(args), null);
                }
            });
        }
    }

    static class Execute {

        private final ForwardLock lock;
        private final JdbcListenerDispatcher jdbcListenerDispatcher;
        private final AgentInterceptor agentInterceptor;

        @Injection.Autowire
        Execute(@Injection.Qualifier("agentInterceptor4Stm") AgentInterceptor agentInterceptor4Stm) {
            this.lock = new ForwardLock();
            this.jdbcListenerDispatcher = JdbcListenerDispatcher.DEFAULT;
            this.agentInterceptor = agentInterceptor4Stm;
        }

        @Advice.OnMethodEnter
        ForwardLock.Release<Map<Object, Object>> enter(@Advice.This Object invoker,
                                                       @Advice.Origin("#m") String method,
                                                       @Advice.AllArguments Object[] args) {
            return lock.acquire(() -> {
                Map<Object, Object> map = ContextUtils.createContext();
                Object[] objs = getArgs(args);
                JdbcContextInfo jdbcContextInfo = JdbcContextInfo.getCurrent();
                map.put(JdbcContextInfo.class, jdbcContextInfo);
                this.jdbcListenerDispatcher.dispatchBefore(jdbcContextInfo, invoker, method, objs);
                agentInterceptor.before(invoker, method, objs, map);
                return map;
            });
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        void exit(@Advice.Enter ForwardLock.Release<Map<Object, Object>> release,
                  @Advice.This Object invoker,
                  @Advice.Origin("#m") String method,
                  @Advice.AllArguments Object[] args,
                  @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object retValue,
                  @Advice.Thrown Exception exception) {
            release.apply(map -> {
                ContextUtils.setEndTime(map);
                Object[] objs = getArgs(args);
                JdbcContextInfo jdbcContextInfo = JdbcContextInfo.getCurrent();
                jdbcListenerDispatcher.dispatchAfter(jdbcContextInfo, invoker, method, objs, retValue);
                agentInterceptor.after(invoker, method, objs, retValue, exception, map);
            });

        }

    }

    private static Object[] getArgs(Object[] args) {
        if (args != null && args.length == 0) {
            return null;
        }
        return args;
    }
}