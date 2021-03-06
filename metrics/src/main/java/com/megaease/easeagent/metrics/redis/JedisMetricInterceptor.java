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

package com.megaease.easeagent.metrics.redis;

import com.codahale.metrics.MetricRegistry;
import com.megaease.easeagent.config.Config;
import com.megaease.easeagent.core.interceptor.MethodInfo;

import java.util.Map;

public class JedisMetricInterceptor extends AbstractRedisMetricInterceptor {

    public JedisMetricInterceptor(MetricRegistry metricRegistry, Config config) {
        super(metricRegistry, config);
    }

    @Override
    public String getKey(MethodInfo methodInfo, Map<Object, Object> context) {
        return methodInfo.getMethod();
    }

}
