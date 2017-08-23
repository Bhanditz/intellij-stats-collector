package com.intellij.completion.enhancer

import com.intellij.codeInsight.completion.*
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.ReflectionUtil
import org.picocontainer.ComponentAdapter


object CompletionContributors {

    fun add(contributorEP: CompletionContributorEP) {
        val extensionPoint = extensionPoint()
        extensionPoint.registerExtension(contributorEP)
    }

    fun remove(contributorEP: CompletionContributorEP) {
        val extensionPoint = extensionPoint()
        extensionPoint.unregisterExtension(contributorEP)
    }

    fun first(): CompletionContributorEP {
        val extensionPoint = extensionPoint()
        return extensionPoint.extensions.first()
    }

    fun addFirst(contributorEP: CompletionContributorEP) {
        val extensionPoint = extensionPoint()
        val first = extensionPoint.extensions.first() as CompletionContributorEP
        val id = contributorOrderId(first)
        val order = LoadingOrder.readOrder("first, before $id")
        extensionPoint.registerExtension(contributorEP, order)
    }

    private fun extensionPoint(): ExtensionPoint<CompletionContributorEP> {
        return Extensions.getRootArea().getExtensionPoint<CompletionContributorEP>("com.intellij.completion.contributor")
    }

    fun removeFirst() {
        val point = extensionPoint()
        val first = point.extensions.first()
        point.unregisterExtension(first)
    }

    private fun contributorOrderId(contributorEP: CompletionContributorEP): String? {
        val className = contributorEP.implementationClass
        val picoContainer = Extensions.getRootArea().picoContainer
        val adapterForFirstContributor = (picoContainer.componentAdapters as Collection<ComponentAdapter>)
                .asSequence()
                .filter {
                    it is ExtensionComponentAdapter
                            && ReflectionUtil.isAssignable(CompletionContributorEP::class.java, it.componentImplementation)
                }
                .map { it to it.getComponentInstance(picoContainer) as CompletionContributorEP }
                .find { it.second.implementationClass == className }?.first as? ExtensionComponentAdapter

        return adapterForFirstContributor?.orderId
    }

}



class FirstContributorPreloader : PreloadingActivity() {

    override fun preload(indicator: ProgressIndicator) {
        CompletionContributorEP().apply {
            implementationClass = InvocationCountEnhancingContributor::class.java.name
            language = "any"
        }.let {
            CompletionContributors.addFirst(it)
        }
    }

}


/**
 * Runs all remaining contributors and then starts another completion round with max invocation count,
 * All lookup elements added would be sorted with another sorter and will appear at the bottom of completion lookup
 */
class InvocationCountEnhancingContributor : CompletionContributor() {
    companion object {
        private val MAX_INVOCATION_COUNT = 5
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        result.runRemainingContributors(parameters, {
            result.passResult(it)
        })

        if (parameters.invocationCount > MAX_INVOCATION_COUNT) return

        val updatedParams = parameters
                .withInvocationCount(MAX_INVOCATION_COUNT)
                .withType(parameters.completionType)

        val sorter = CompletionSorter.emptySorter()
        val newResultSet = result.withRelevanceSorter(sorter)

        CompletionService
                .getCompletionService()
                .getVariantsFromContributors(updatedParams, this, {
                    newResultSet.consume(it.lookupElement)
                })
    }

}