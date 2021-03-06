/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.client.routing;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class WeightedRoundRobinStrategy implements EndpointSelectionStrategy<WeightedEndpoint> {

    @Override
    @SuppressWarnings("unchecked")
    public EndpointSelector<WeightedEndpoint> newSelector(EndpointGroup<? extends WeightedEndpoint> endpointGroup) {
        return new RoundRobinSelector((EndpointGroup<WeightedEndpoint>) endpointGroup);
    }


    /**
     * A weighted round robin select strategy.
     * <p>
     * For nodes a,b,c<br>
     * if endpoint weights are 1,1,1(or 2,2,2),then select result is abc abc ...<br>
     * if endpoint weights are 1,2,3(or 2,4,6),then select result is abcbcc(or abcabcbcbccc) ...<br>
     * if endpoint weights are 3,5,7,then select result is abcabcabcbcbcbb abcabcabcbcbcbb ...
     */
    final static class RoundRobinSelector implements EndpointSelector<WeightedEndpoint> {
        private EndpointGroup<WeightedEndpoint> endpointGroup;

        private final AtomicLong sequence = new AtomicLong();

        private int minWeight = Integer.MAX_VALUE;

        private int maxWeight = Integer.MIN_VALUE;

        private int sumWeight = 0;

        RoundRobinSelector(EndpointGroup<WeightedEndpoint> endpointGroup) {
            requireNonNull(endpointGroup, "group");

            this.endpointGroup = endpointGroup;
            endpointGroup.endpoints().stream().forEach(e -> {
                int weight = e.weight();
                minWeight = Math.min(minWeight, weight);
                maxWeight = Math.max(maxWeight, weight);
                sumWeight += weight;
            });
        }

        @Override
        public EndpointGroup<WeightedEndpoint> group() {
            return endpointGroup;
        }

        @Override
        public EndpointSelectionStrategy strategy() {
            return EndpointSelectionStrategy.WEIGHTED_ROUND_ROBIN;
        }

        @Override
        public WeightedEndpoint select() {
            List<WeightedEndpoint> endpoints = endpointGroup.endpoints();
            long currentSequence = sequence.getAndIncrement();

            if (minWeight < maxWeight) {
                HashMap<WeightedEndpoint, AtomicInteger> endpointWeights = new LinkedHashMap<>();
                for (WeightedEndpoint endpoint : endpoints) {
                    endpointWeights.put(endpoint, new AtomicInteger(endpoint.weight()));
                }

                int mod = (int) (currentSequence % sumWeight);
                for (int i = 0; i < maxWeight; i++) {
                    for (Map.Entry<WeightedEndpoint, AtomicInteger> entry : endpointWeights.entrySet()) {
                        AtomicInteger weight = entry.getValue();
                        if (mod == 0 && weight.get() > 0) {
                            return entry.getKey();
                        }
                        if (weight.get() > 0) {
                            weight.decrementAndGet();
                            mod--;
                        }
                    }
                }
            }
            //endpoints weight equal
            return endpoints.get((int) (currentSequence % endpoints.size()));
        }
    }
}
