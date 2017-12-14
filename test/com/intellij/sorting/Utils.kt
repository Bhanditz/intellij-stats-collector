/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.sorting

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.util.Pair
import com.jetbrains.completion.ranker.features.impl.FeatureUtils
import org.assertj.core.api.Assertions


internal fun LookupImpl.checkMlRanking(ranker: Ranker, prefix_length: Int) {
    val items = this.items.toList()
    val lookupElements = getRelevanceObjects(items, false)

    lookupElements.forEach { element, relevance ->
        val weights: MutableMap<String, Any?> = relevance.associate { it.first to it.second }.toMutableMap()
        val mlRank = weights["ml_rank"]?.toString()
        if (mlRank == "UNDEFINED" || weights["before_rerank_order"] == null) {
            throw UnsupportedOperationException("Ranking failed")
        }

        val oldOrder = weights["before_rerank_order"].toString().toInt()

        weights.put("position", oldOrder)
        weights.put("query_length", prefix_length)
        weights.put("result_length", element.lookupString.length)

        val calculatedMlRank = ranker.rank(weights, emptyMap())
        Assertions.assertThat(calculatedMlRank).isEqualTo(mlRank?.toDouble())
                .withFailMessage("Calculated: $calculatedMlRank Regular: ${mlRank?.toDouble()}")
    }
}


internal fun LookupImpl.assertEachItemHasMlValue(value: String) {
    val objects: Map<LookupElement, List<Pair<String, Any>>> = getRelevanceObjects(items, false)
    val ranks = objects
            .mapNotNull { it.value.find { it.first == FeatureUtils.ML_RANK } }
            .map { it.second }
            .toSet()

    Assertions.assertThat(ranks.size).withFailMessage("Ranks size: ${ranks.size} expected: 1\nRanks $ranks").isEqualTo(1)
    Assertions.assertThat(ranks.first()).isEqualTo(value)
}


internal object Samples {

    val callCompletionOnClass = """
    public class Test {
        public void test(int a, int b) {}
        public void runq(int c) {}
        public void qqqq() {}
        public void qwrt(int a, int b, int c) {}
    }
    """

    val methodCompletion = """
    class X {
        public void t() {
            Test test = new Test();
            test.<caret>
        }
    }
    """

    val classNameCompletion = """
    class Test {
      public void run() {
        F<caret>
      }
    }
    """

    val classText = """
public class Test {
    public void test() {}
    public void run() {}
    public void testMore() {}
    public void check() {}
}
"""

}