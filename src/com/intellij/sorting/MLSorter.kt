package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionFinalSorter
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Pair
import com.intellij.plugin.isMlSortingEnabledByForce
import com.intellij.psi.util.PsiUtilCore
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.jetbrains.completion.ranker.features.FeatureUtils
import com.jetbrains.completion.ranker.features.LookupElementInfo


@Suppress("DEPRECATION")
class MLSorterFactory : CompletionFinalSorter.Factory {
    override fun newSorter() = MLSorter()
}


class MLSorter : CompletionFinalSorter() {

    private val webServiceStatus = WebServiceStatus.getInstance()
    private val ranker = Ranker.getInstance()
    private val cachedScore = mutableMapOf<LookupElement, ItemRankInfo>()

    override fun getRelevanceObjects(items: MutableIterable<LookupElement>): Map<LookupElement, List<Pair<String, Any>>> {
        if (cachedScore.isEmpty()) {
            return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.NONE as Any)) }
        }

        if (hasUnknownFeatures(items)) {
            return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.UNDEFINED as Any)) }
        }
        
        if (!isCacheValid(items)) {
            return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.INVALID_CACHE as Any)) }
        }
        
        return items.associate {
            val result = mutableListOf<Pair<String, Any>>()
            val cached = cachedScore[it]
            if (cached != null) {
                result.add(Pair.create(FeatureUtils.ML_RANK, cached.mlRank))
                result.add(Pair.create(FeatureUtils.BEFORE_ORDER, cached.positionBefore))
            }
            it to result
        }
    }
    
    private fun isCacheValid(items: Iterable<LookupElement>): Boolean {
        return items.map { cachedScore[it]?.prefixLength }.toSet().size == 1
    }
    
    private fun hasUnknownFeatures(items: Iterable<LookupElement>) = items.any {
        val score = cachedScore[it]
        score == null || score.mlRank == null
    }

    override fun sort(items: MutableIterable<LookupElement>, parameters: CompletionParameters): Iterable<LookupElement> {
        if (!shouldSortByMlRank(parameters)) return items

        val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl ?: return items
        val relevanceObjects = lookup.getRelevanceObjects(items, false)

        val startTime = System.currentTimeMillis()
        val sorted = sortByMLRanking(items, lookup, relevanceObjects) ?: return items
        val timeSpent = System.currentTimeMillis() - startTime
        
        val elementsSorted = items.count()
        SortingTimeStatistics.registerSortTiming(elementsSorted, timeSpent)
        
        return sorted
    }

    private fun shouldSortByMlRank(parameters: CompletionParameters): Boolean {
        if (isMlSortingEnabledByForce()) return true

        val language = parameters.language() ?: return false
        val buildNumber = PluginManager.BUILD_NUMBER

        if (is171BranchOrInUnitTestMode(buildNumber) && isJava(language)) {
            return webServiceStatus.isExperimentOnCurrentIDE()
        }

        return false
    }

    private fun isJava(language: Language) = "Java".equals(language.displayName, ignoreCase = true)

    private fun is171BranchOrInUnitTestMode(buildNumber: String): Boolean {
        return buildNumber.contains("-171.") || ApplicationManager.getApplication().isUnitTestMode
    }

    /**
     * Null means we encountered unknown features and are unable to sort them
     */
    private fun sortByMLRanking(items: MutableIterable<LookupElement>,
                                lookup: LookupImpl,
                                relevanceObjects: Map<LookupElement, List<Pair<String, Any?>>>): Iterable<LookupElement>?
    {
        return items
                .mapIndexed { index, lookupElement ->
                    val relevance = relevanceObjects[lookupElement] ?: emptyList()
                    val rank: Double = calculateElementRank(lookup, lookupElement, index, relevance) ?: return null
                    lookupElement to rank
                }
                .sortedByDescending { it.second }
                .map { it.first }
    }


    private fun getCachedRankInfo(lookup: LookupImpl, element: LookupElement, position: Int): ItemRankInfo? {
        val currentPrefixLength = lookup.getPrefixLength(element)

        val cached = cachedScore[element]
        if (cached != null && currentPrefixLength == cached.prefixLength && cached.positionBefore == position) {
            return cached
        }

        return null
    }


    private fun calculateElementRank(lookup: LookupImpl, 
                                     element: LookupElement, 
                                     position: Int, 
                                     relevance: List<Pair<String, Any?>>): Double?
    {
        val cachedWeight = getCachedRankInfo(lookup, element, position)
        if (cachedWeight != null) {
            return cachedWeight.mlRank
        }

        val prefixLength = lookup.getPrefixLength(element)
        val elementLength = element.lookupString.length

        val state = LookupElementInfo(position, query_length = prefixLength, result_length = elementLength)

        val relevanceMap = relevance.associate { it.first to it.second }
        val mlRank: Double? = ranker.rank(state, relevanceMap)
        val info = ItemRankInfo(position, mlRank, prefixLength)
        cachedScore[element] = info

        return info.mlRank
    }


}


private data class ItemRankInfo(val positionBefore: Int, val mlRank: Double?, val prefixLength: Int)


typealias WeightedElement = Pair<LookupElement, Double>

private fun CompletionParameters.language(): Language? {
    val offset = editor.caretModel.offset
    return  PsiUtilCore.getLanguageAtOffset(originalFile, offset)
}