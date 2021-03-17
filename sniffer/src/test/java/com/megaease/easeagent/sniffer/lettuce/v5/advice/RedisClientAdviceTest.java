package com.megaease.easeagent.sniffer.lettuce.v5.advice;

import com.megaease.easeagent.core.Classes;
import com.megaease.easeagent.core.Definition;
import com.megaease.easeagent.core.QualifiedBean;
import com.megaease.easeagent.core.interceptor.AgentInterceptor;
import com.megaease.easeagent.core.interceptor.AgentInterceptorChain;
import com.megaease.easeagent.core.interceptor.AgentInterceptorChainInvoker;
import com.megaease.easeagent.core.interceptor.DefaultAgentInterceptorChain;
import com.megaease.easeagent.core.utils.AgentFieldAccessor;
import com.megaease.easeagent.sniffer.BaseSnifferTest;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StatefulRedisConnectionImpl;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;

public class RedisClientAdviceTest extends BaseSnifferTest {

    static List<Class<?>> classList;

    static AgentInterceptorChainInvoker chainInvoker;
    static AgentInterceptorChain.Builder builder4RedisClientConnectAsync;
    static AgentInterceptor interceptor4ConnectASync;

    @Before
    public void before() {
        if (classList == null) {
            interceptor4ConnectASync = mock(AgentInterceptor.class);
            builder4RedisClientConnectAsync = new DefaultAgentInterceptorChain.Builder().addInterceptor(interceptor4ConnectASync);
            chainInvoker = spy(AgentInterceptorChainInvoker.getInstance());
            Definition.Default def = new GenRedisClientAdvice().define(Definition.Default.EMPTY);
            ClassLoader loader = this.getClass().getClassLoader();
            classList = Classes.transform(this.getClass().getName() + "$MyRedisClient")
                    .with(def,
                            new QualifiedBean("builder4RedisClientConnectAsync", builder4RedisClientConnectAsync),
                            new QualifiedBean("", chainInvoker)
                    )
                    .load(loader);
        }
    }

    @Test
    public void connect() throws Exception {
        MyRedisClient myRedisClient = (MyRedisClient) classList.get(0).newInstance();
        myRedisClient.connectStandaloneAsync();
        this.verifyInterceptorTimes(interceptor4ConnectASync, 1, false);

    }

    @Test
    public void field() {
        Field field = AgentFieldAccessor.getFieldFromClass(StatefulRedisConnectionImpl.class, "channelWriter");
        Assert.assertNotNull(field);
    }

    static class MyRedisClient extends RedisClient {

        private ConnectionFuture<StatefulRedisConnection> connectStandaloneAsync() throws ExecutionException, InterruptedException {
            ConnectionFuture<StatefulRedisConnection> connectionFuture = mock(ConnectionFuture.class);
            when(connectionFuture.get()).thenReturn(mock(StatefulRedisConnection.class));
            return connectionFuture;
        }

        @Override
        public <K, V> ConnectionFuture<StatefulRedisConnection<K, V>> connectAsync(RedisCodec<K, V> codec, RedisURI redisURI) {
            return mock(ConnectionFuture.class);
        }
    }

}