/*
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
package io.trino.sql.planner.iterative.rule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.sql.planner.iterative.rule.test.BaseRuleTest;
import io.trino.sql.planner.plan.SampleNode.Type;
import io.trino.sql.tree.ComparisonExpression;
import io.trino.sql.tree.LongLiteral;
import io.trino.sql.tree.SymbolReference;
import org.junit.jupiter.api.Test;

import static io.trino.sql.planner.assertions.PlanMatchPattern.values;
import static io.trino.sql.tree.ComparisonExpression.Operator.GREATER_THAN;

public class TestEvaluateZeroSample
        extends BaseRuleTest
{
    @Test
    public void testDoesNotFire()
    {
        tester().assertThat(new EvaluateZeroSample())
                .on(p ->
                        p.sample(
                                0.15,
                                Type.BERNOULLI,
                                p.values(p.symbol("a"))))
                .doesNotFire();
    }

    @Test
    public void test()
    {
        tester().assertThat(new EvaluateZeroSample())
                .on(p ->
                        p.sample(
                                0,
                                Type.BERNOULLI,
                                p.filter(
                                        new ComparisonExpression(GREATER_THAN, new SymbolReference("b"), new LongLiteral("5")),
                                        p.values(
                                                ImmutableList.of(p.symbol("a"), p.symbol("b")),
                                                ImmutableList.of(
                                                        ImmutableList.of(new LongLiteral("1"), new LongLiteral("10")),
                                                        ImmutableList.of(new LongLiteral("2"), new LongLiteral("11")))))))
                // TODO: verify contents
                .matches(values(ImmutableMap.of()));
    }
}
