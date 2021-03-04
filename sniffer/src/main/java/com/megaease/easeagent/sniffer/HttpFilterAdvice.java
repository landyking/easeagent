package com.megaease.easeagent.sniffer;

import com.megaease.easeagent.core.utils.ContextUtils;
import com.megaease.easeagent.common.ForwardLock;
import com.megaease.easeagent.core.utils.ServletUtils;
import com.megaease.easeagent.core.AdviceTo;
import com.megaease.easeagent.core.Definition;
import com.megaease.easeagent.core.Injection;
import com.megaease.easeagent.core.Transformation;
import com.megaease.easeagent.core.interceptor.AgentInterceptor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.*;

@Injection.Provider(Provider.class)
public abstract class HttpFilterAdvice implements Transformation {
    private static final String FILTER_NAME = "org.springframework.web.filter.CharacterEncodingFilter";
    static final String SERVLET_REQUEST = "javax.servlet.http.HttpServletRequest";
    static final String SERVLET_RESPONSE = "javax.servlet.http.HttpServletResponse";
    static final String FILTER_CHAIN = "javax.servlet.FilterChain";

    @Override
    public <T extends Definition> T define(Definition<T> def) {
        return def.type(
                hasSuperType(named(FILTER_NAME)))
                .transform(doFilterInternal(
                        named("doFilterInternal").and(takesArguments(3))
                                .and(takesArgument(0, named(SERVLET_REQUEST)))
                                .and(takesArgument(1, named(SERVLET_RESPONSE)))
                                .and(takesArgument(2, named(FILTER_CHAIN)))
                        )
                ).end();
    }

    @AdviceTo(DoFilterInternal.class)
    protected abstract Definition.Transformer doFilterInternal(ElementMatcher<? super MethodDescription> matcher);

    static class DoFilterInternal {

        private final ForwardLock lock;
        private final AgentInterceptor agentInterceptor;

        @Injection.Autowire
        DoFilterInternal(@Injection.Qualifier("agentInterceptor4HttpFilter") AgentInterceptor agentInterceptor) {
            this.lock = new ForwardLock();
            this.agentInterceptor = agentInterceptor;
        }

        @Advice.OnMethodEnter
        ForwardLock.Release<Map<Object, Object>> enter(
                @Advice.Origin Object invoker,
                @Advice.Origin("#m") String method,
                @Advice.AllArguments Object[] args
        ) {
            return lock.acquire(() -> {
                Map<Object, Object> map = ContextUtils.createContext();
                HttpServletRequest httpServletRequest = (HttpServletRequest) args[0];
                ServletUtils.setHttpRouteAttribute(httpServletRequest);
                this.agentInterceptor.before(invoker, method, args, map);
                return map;
            });

        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        void exit(@Advice.Enter ForwardLock.Release<Map<Object, Object>> release,
                  @Advice.Origin Object invoker,
                  @Advice.Origin("#m") String method,
                  @Advice.AllArguments Object[] args,
                  @Advice.Thrown Exception exception
        ) {
            release.apply(map -> {
                ContextUtils.setEndTime(map);
                this.agentInterceptor.after(invoker, method, args, null, exception, map);
            });
        }
    }
}