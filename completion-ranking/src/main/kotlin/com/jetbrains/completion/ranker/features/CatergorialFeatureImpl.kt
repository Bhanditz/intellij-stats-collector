package com.jetbrains.completion.ranker.features

class CatergorialFeatureImpl(override val name: String,
                             override val undefinedIndex: Int,
                             override val otherCatergoryIndex: Int,
                             private val categoryToIndex: Map<String, Int>)
    : CatergorialFeature {
    override fun indexByCategory(category: String): Int = categoryToIndex[category] ?: otherCatergoryIndex

    override val categories: Set<String> = categoryToIndex.keys

    override fun process(value: Any?, featureArray: DoubleArray) {
        setDefaults(featureArray)
        if (value != null) {
            featureArray[indexByCategory(value.toString())] = 1.0
        }
    }

    override fun setDefaults(featureArray: DoubleArray) {
        categories.forEach { featureArray[indexByCategory(it)] = 0.0 }
        featureArray[undefinedIndex] = 1.0
        featureArray[otherCatergoryIndex] = 0.0
    }
}