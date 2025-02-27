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
import io.trino.sql.planner.Symbol;
import io.trino.sql.planner.assertions.RowNumberSymbolMatcher;
import io.trino.sql.planner.iterative.rule.test.BaseRuleTest;
import io.trino.sql.tree.Cast;
import io.trino.sql.tree.ComparisonExpression;
import io.trino.sql.tree.GenericLiteral;
import io.trino.sql.tree.LogicalExpression;
import io.trino.sql.tree.LongLiteral;
import io.trino.sql.tree.SymbolReference;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.trino.sql.planner.assertions.PlanMatchPattern.dataType;
import static io.trino.sql.planner.assertions.PlanMatchPattern.filter;
import static io.trino.sql.planner.assertions.PlanMatchPattern.rowNumber;
import static io.trino.sql.planner.assertions.PlanMatchPattern.values;
import static io.trino.sql.tree.ComparisonExpression.Operator.EQUAL;
import static io.trino.sql.tree.ComparisonExpression.Operator.GREATER_THAN;
import static io.trino.sql.tree.ComparisonExpression.Operator.LESS_THAN;
import static io.trino.sql.tree.LogicalExpression.Operator.AND;

public class TestPushdownFilterIntoRowNumber
        extends BaseRuleTest
{
    @Test
    public void testSourceRowNumber()
    {
        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol a = p.symbol("a");
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("100"), dataType("bigint"))),
                            p.rowNumber(
                                    ImmutableList.of(a),
                                    Optional.empty(),
                                    rowNumberSymbol,
                                    p.values(a)));
                })
                .matches(
                        rowNumber(rowNumber -> rowNumber
                                        .maxRowCountPerPartition(Optional.of(99))
                                        .partitionBy(ImmutableList.of("a")),
                                values("a")));

        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol a = p.symbol("a");
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("100"), dataType("bigint"))),
                            p.rowNumber(
                                    ImmutableList.of(a),
                                    Optional.of(10),
                                    rowNumberSymbol,
                                    p.values(a)));
                })
                .matches(
                        rowNumber(rowNumber -> rowNumber
                                        .maxRowCountPerPartition(Optional.of(10))
                                        .partitionBy(ImmutableList.of("a")),
                                values("a")));

        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol a = p.symbol("a");
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new LogicalExpression(AND, ImmutableList.of(new ComparisonExpression(LESS_THAN, new Cast(new LongLiteral("3"), dataType("bigint")), new SymbolReference("row_number_1")), new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("5"), dataType("bigint"))))),
                            p.rowNumber(
                                    ImmutableList.of(a),
                                    Optional.of(10),
                                    rowNumberSymbol,
                                    p.values(a)));
                })
                .matches(
                        filter(
                                new LogicalExpression(AND, ImmutableList.of(new ComparisonExpression(LESS_THAN, new Cast(new LongLiteral("3"), dataType("bigint")), new SymbolReference("row_number_1")), new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("5"), dataType("bigint"))))),
                                rowNumber(rowNumber -> rowNumber
                                                .maxRowCountPerPartition(Optional.of(4))
                                                .partitionBy(ImmutableList.of("a")),
                                        values("a"))
                                        .withAlias("row_number_1", new RowNumberSymbolMatcher())));

        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol a = p.symbol("a");
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new LogicalExpression(AND, ImmutableList.of(new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("5"), dataType("bigint"))), new ComparisonExpression(EQUAL, new SymbolReference("a"), new GenericLiteral("BIGINT", "1")))),
                            p.rowNumber(
                                    ImmutableList.of(a),
                                    Optional.of(10),
                                    rowNumberSymbol,
                                    p.values(a)));
                })
                .matches(
                        filter(
                                new ComparisonExpression(EQUAL, new SymbolReference("a"), new GenericLiteral("BIGINT", "1")),
                                rowNumber(rowNumber -> rowNumber
                                                .maxRowCountPerPartition(Optional.of(4))
                                                .partitionBy(ImmutableList.of("a")),
                                        values("a"))
                                        .withAlias("row_number_1", new RowNumberSymbolMatcher())));
    }

    @Test
    public void testNoOutputsThroughRowNumber()
    {
        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("-100"), dataType("bigint"))),
                            p.rowNumber(ImmutableList.of(p.symbol("a")), Optional.empty(), rowNumberSymbol,
                                    p.values(p.symbol("a"))));
                })
                .matches(values("a", "row_number_1"));
    }

    @Test
    public void testDoNotFire()
    {
        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new ComparisonExpression(LESS_THAN, new SymbolReference("not_row_number"), new Cast(new LongLiteral("100"), dataType("bigint"))),
                            p.rowNumber(ImmutableList.of(p.symbol("a")), Optional.empty(), rowNumberSymbol,
                                    p.values(p.symbol("a"), p.symbol("not_row_number"))));
                })
                .doesNotFire();

        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new ComparisonExpression(GREATER_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("100"), dataType("bigint"))),
                            p.rowNumber(ImmutableList.of(p.symbol("a")), Optional.empty(), rowNumberSymbol,
                                    p.values(p.symbol("a"))));
                })
                .doesNotFire();

        tester().assertThat(new PushdownFilterIntoRowNumber(tester().getPlannerContext()))
                .on(p -> {
                    Symbol a = p.symbol("a");
                    Symbol rowNumberSymbol = p.symbol("row_number_1");
                    return p.filter(
                            new LogicalExpression(AND, ImmutableList.of(new ComparisonExpression(LESS_THAN, new Cast(new LongLiteral("3"), dataType("bigint")), new SymbolReference("row_number_1")), new ComparisonExpression(LESS_THAN, new SymbolReference("row_number_1"), new Cast(new LongLiteral("5"), dataType("bigint"))))),
                            p.rowNumber(
                                    ImmutableList.of(a),
                                    Optional.of(4),
                                    rowNumberSymbol,
                                    p.values(a)));
                })
                .doesNotFire();
    }
}
