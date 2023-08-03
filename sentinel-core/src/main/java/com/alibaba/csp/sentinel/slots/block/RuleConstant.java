/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block;

import com.alibaba.csp.sentinel.node.IntervalProperty;

/**
 * @author youji.zj
 * @author jialiang.linjl
 */
public final class RuleConstant {

    public static final int FLOW_GRADE_THREAD = 0;
    public static final int FLOW_GRADE_QPS = 1;

    public static final int DEGRADE_GRADE_RT = 0;
    /**
     * Degrade by biz exception ratio in the current {@link IntervalProperty#INTERVAL} second(s).
     */
    public static final int DEGRADE_GRADE_EXCEPTION_RATIO = 1;
    /**
     * Degrade by biz exception count in the last 60 seconds.
     */
    public static final int DEGRADE_GRADE_EXCEPTION_COUNT = 2;

    public static final int DEGRADE_DEFAULT_SLOW_REQUEST_AMOUNT = 5;
    public static final int DEGRADE_DEFAULT_MIN_REQUEST_AMOUNT = 5;

    public static final int AUTHORITY_WHITE = 0;
    public static final int AUTHORITY_BLACK = 1;

    /**
     * <a href="https://cloud.tencent.com/developer/article/2223699?areaSource=102001.2&traceId=pOq3mLZtnne4-Bwqf_gPW">流控模式</a>
     */
    // 0：直接模式，针对当前访问资源，如果超过阈值直接限流
    public static final int STRATEGY_DIRECT = 0;
    // 1：关联模式，针对当前访问资源，如果关联的资源达到阈值，则限流
    // eg：当前资源A，关联资源B，阈值为5，若资源B每秒请求数超过5，则资源A被限流，而资源B请求不会被限流
    public static final int STRATEGY_RELATE = 1;
    // 2：链路模式，针对当前访问资源，如果指定链路访问当前资源的请求数达到阈值，则限流
    // eg：当前资源A，链路为 B -> A (资源B请求资源A)，阈值为5，若资源B每秒请求资源A的次数超过5，则资源A被限流，而资源B请求不会被限流
    public static final int STRATEGY_CHAIN = 2;

    /**
     * 流控效果，流量整形，即触发流控后的行为
     */
    // 0：快速失败，直接失败，抛出异常
    public static final int CONTROL_BEHAVIOR_DEFAULT = 0;
    // 1：预热模式，配置中存在一个预热时间，在预热时间内，将阈值线性提升到目标值，初始阈值 = 配置阈值 / 加载冷因子，其中 默认加载冷因子 = 3
    // 这种方式可以平缓拉高系统水位，避免突发流量对当前处于低水位的系统的可用性造成破坏
    // eg：配置阈值为10，预热时间为5秒，则初始阈值为 10 / 3 = 3，5秒后达到10
    public static final int CONTROL_BEHAVIOR_WARM_UP = 1;
    // 2：排队等待，即请求进入排队队列，根据队列的情况，漏桶模型，当前请求进入后判断当前能否直接通过，如果不能看需等待时间是否超过最大等待时间，如果超过则直接失败，否则等待
    public static final int CONTROL_BEHAVIOR_RATE_LIMITER = 2;
    // 3：预热 + 排队等待模式，结合预热模式和排队等待
    public static final int CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER = 3;

    public static final int DEFAULT_BLOCK_STRATEGY = 0;
    public static final int TRY_AGAIN_BLOCK_STRATEGY = 1;
    public static final int TRY_UNTIL_SUCCESS_BLOCK_STRATEGY = 2;

    public static final int DEFAULT_RESOURCE_TIMEOUT_STRATEGY = 0;
    public static final int RELEASE_RESOURCE_TIMEOUT_STRATEGY = 1;
    public static final int KEEP_RESOURCE_TIMEOUT_STRATEGY = 2;

    public static final String LIMIT_APP_DEFAULT = "default";
    public static final String LIMIT_APP_OTHER = "other";

    public static final int DEFAULT_SAMPLE_COUNT = 2;
    public static final int DEFAULT_WINDOW_INTERVAL_MS = 1000;

    private RuleConstant() {}
}
