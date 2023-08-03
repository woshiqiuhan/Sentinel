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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * The principle idea comes from Guava. However, the calculation of Guava is
 * rate-based, which means that we need to translate rate to QPS.
 * </p>
 *
 * <p>
 * Requests arriving at the pulse may drag down long idle systems even though it
 * has a much larger handling capability in stable period. It usually happens in
 * scenarios that require extra time for initialization, e.g. DB establishes a connection,
 * connects to a remote service, and so on. That’s why we need “warm up”.
 * </p>
 *
 * <p>
 * Sentinel's "warm-up" implementation is based on the Guava's algorithm.
 * However, Guava’s implementation focuses on adjusting the request interval,
 * which is similar to leaky bucket. Sentinel pays more attention to
 * controlling the count of incoming requests per second without calculating its interval,
 * which resembles token bucket algorithm.
 * </p>
 *
 * <p>
 * The remaining tokens in the bucket is used to measure the system utility.
 * Suppose a system can handle b requests per second. Every second b tokens will
 * be added into the bucket until the bucket is full. And when system processes
 * a request, it takes a token from the bucket. The more tokens left in the
 * bucket, the lower the utilization of the system; when the token in the token
 * bucket is above a certain threshold, we call it in a "saturation" state.
 * </p>
 *
 * <p>
 * Base on Guava’s theory, there is a linear equation we can write this in the
 * form y = m * x + b where y (a.k.a y(x)), or qps(q)), is our expected QPS
 * given a saturated period (e.g. 3 minutes in), m is the rate of change from
 * our cold (minimum) rate to our stable (maximum) rate, x (or q) is the
 * occupied token.
 * </p>
 *
 * @author jialiang.linjl
 */
public class WarmUpController implements TrafficShapingController {

    protected double count;
    private int coldFactor;
    protected int warningToken = 0;
    private int maxToken;
    protected double slope;

    // 当前令牌桶中令牌数
    protected AtomicLong storedTokens = new AtomicLong(0);
    // 上一次填充令牌桶的时间，存的一定是整秒
    protected AtomicLong lastFilledTime = new AtomicLong(0);

    public WarmUpController(double count, int warmUpPeriodInSec, int coldFactor) {
        construct(count, warmUpPeriodInSec, coldFactor);
    }

    public WarmUpController(double count, int warmUpPeriodInSec) {
        construct(count, warmUpPeriodInSec, 3);
    }

    /**
     * count 每秒允许通过的请求数<p/>
     * warmUpPeriodInSec 预热时长<p/>
     * coldFactor有关，默认值为3<p/>
     *
     * stableInterval 稳定生产一个令牌需要的时间<p/>
     * coldInterval 生产一个令牌需要的最大时间，与冷启动因子有关<p/>
     *
     * thresholdPermits/warningToken 令牌桶中的警戒线，当令牌数高于警戒线时，开始预热<p/>
     * maxPermits/maxToken 令牌桶中最大的令牌数<p/>
     * slope 函数图像中斜率
     *
     * 关联关系及参数计算公式推导，具体可参考 <a href="https://blog.csdn.net/weixin_39838028/article/details/111003647">博客</a><p/>
     *  stableInterval = 1 / count<p/>
     *  coldInterval = stableInterval * coldFactor = coldFactor / count<p/>
     *
     *  warmUpPeriodInSec = (stableInterval + coldInterval) * (maxPermits - thresholdPermits) / 2<p/>
     *  (coldFactor - 1) * (stableInterval * thresholdPermits) = (stableInterval + coldInterval) * (maxPermits - thresholdPermits) / 2 = warmUpPeriodInSec<p/>
     *
     *  (coldFactor - 1) * (stableInterval * thresholdPermits) = warmUpPeriodInSec<p/>
     *  thresholdPermits = warmUpPeriodInSec / stableInterval / (coldFactor - 1) = warmUpPeriodInSec * count / (coldFactor - 1)<p/>
     *  thresholdPermits = warmUpPeriodInSec * count / (coldFactor - 1)<p/>
     *
     *  (stableInterval + coldInterval) * (maxPermits - thresholdPermits) / 2 = warmUpPeriodInSec<p/>
     *  maxPermits = thresholdPermits + 2 * warmUpPeriodInSec / (stableInterval + coldInterval) = thresholdPermits + 2 * warmUpPeriodInSec * count / (1 + coldFactor)<p/>
     *
     *
     *  slope = (coldInterval - stableInterval) / (maxPermits - thresholdPermits) = (coldFactor - 1) / count / (maxPermits - thresholdPermits)<p/>
     */
    private void construct(double count, int warmUpPeriodInSec, int coldFactor) {

        if (coldFactor <= 1) {
            throw new IllegalArgumentException("Cold factor should be larger than 1");
        }

        this.count = count;

        this.coldFactor = coldFactor;

        // thresholdPermits = 0.5 * warmUpPeriodInSec / stableInterval.
        // warningToken = 100;
        warningToken = (int) (warmUpPeriodInSec * count) / (coldFactor - 1);
        // / maxPermits = thresholdPermits + 2 * warmupPeriod /
        // (stableInterval + coldInterval)
        // maxToken = 200
        maxToken = warningToken + (int) (2 * warmUpPeriodInSec * count / (1.0 + coldFactor));

        // slope
        // slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits
        // - thresholdPermits);
        slope = (coldFactor - 1.0) / count / (maxToken - warningToken);

        /**
         * 假设 count = 100, warmUpPeriodInSec = 5, coldFactor = 3;
         *
         * warningToken = 100 * 5 / (3 - 1) = 250
         * maxToken = 250 + 2 * 5 * 100 / (1 + 3) = 500
         * slope = (3 - 1) / 100 / (350 - 250) = 0.00008
         */
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        long passQps = (long) node.passQps();

        // 获取上一秒通过的qps
        long previousQps = (long) node.previousPassQps();
        syncToken(previousQps);

        // 开始计算它的斜率
        // 如果进入了警戒线，开始调整他的qps
        long restToken = storedTokens.get();
        if (restToken >= warningToken) {
            long aboveToken = restToken - warningToken;
            // 消耗的速度要比warning快，但是要比慢
            // current interval = restToken*slope+1/count

            // aboveToken * slope + 1.0 / count 为当前每张令牌产生需要的时间
            // 1.0 / (aboveToken * slope + 1.0 / count) 当前1s内可以产生的令牌数，即当前可通过的qps
            double warningQps = Math.nextUp(1.0 / (aboveToken * slope + 1.0 / count));
            if (passQps + acquireCount <= warningQps) {
                return true;
            }
        } else {
            if (passQps + acquireCount <= count) {
                return true;
            }
        }

        return false;
    }

    protected void syncToken(long passQps) {
        long currentTime = TimeUtil.currentTimeMillis();
        currentTime = currentTime - currentTime % 1000;


        long oldLastFillTime = lastFilledTime.get();
        if (currentTime <= oldLastFillTime) {
            // 已经填充过了，直接返回
            return;
        }

        long oldValue = storedTokens.get();
        long newValue = coolDownTokens(currentTime, passQps);

        if (storedTokens.compareAndSet(oldValue, newValue)) {
            long currentValue = storedTokens.addAndGet(0 - passQps);
            if (currentValue < 0) {
                storedTokens.set(0L);
            }
            lastFilledTime.set(currentTime);
        }

    }

    private long coolDownTokens(long currentTime, long passQps) {
        long oldValue = storedTokens.get();
        long newValue = oldValue;

        // 添加令牌的判断前提条件:
        // 当令牌的消耗程度远远低于警戒线的时候
        // 或者当令牌数大于警戒线，且上一窗口通过的qps小于阈值/冷却因子
        if (oldValue < warningToken) {
            newValue = (long) (oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
        } else if (oldValue > warningToken) {
            if (passQps < (int) count / coldFactor) {
                newValue = (long) (oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
            }
        }
        return Math.min(newValue, maxToken);
    }

    public static void main(String[] args) {
        int count = 100, warmUpPeriodInSec = 5, coldFactor = 3;

        int warningToken = (int) (warmUpPeriodInSec * count) / (coldFactor - 1);

        int maxToken = warningToken + (int) (2 * warmUpPeriodInSec * count / (1.0 + coldFactor));

        double slope = (coldFactor - 1.0) / count / (maxToken - warningToken);


        /**
         * warningToken = 250
         * maxToken = 500
         * slope = 8.0E-5 = 0.00008
         */

        System.out.println("warningToken = " + warningToken);
        System.out.println("maxToken = " + maxToken);
        System.out.println("slope = " + slope);

    }
}
