package com.jetbrains.completion.ranker.features


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


typealias DoubleFeatureInfo = Map<String, Double>
typealias CategoricalFeatureInfo = Map<String, Set<String>>
typealias BinaryFeatureInfo = Map<String, Map<String, Double>>
typealias IgnoredFeatureInfo = Set<String>


val gson = Gson()


object FeatureUtils {
    val UNDEFINED = "UNDEFINED"
    val OTHER = "OTHER"
    val NONE = "NONE"
    
    val ML_RANK = "ml_rank"
    val BEFORE_ORDER = "before_rerank_order"
    
    val DEFAULT = "default"
    
    fun getUndefinedFeatureName(name: String) = "$name=$UNDEFINED"
}


fun completionFactors(): CompletionFactors {
    val text = fileContent("features/all_features.json")
    val typeToken = object : TypeToken<Map<String, Set<String>>>() {}
    val map = gson.fromJson<Map<String, Set<String>>>(text, typeToken.type)
 
    val relevance: Set<String> = map["javaRelevance"] ?: emptySet()
    val proximity: Set<String> = map["javaProximity"] ?: emptySet()
    
    return CompletionFactors(relevance, proximity)
}


fun binaryFactors(): BinaryFeatureInfo {
    val text = fileContent("features/binary.json")
    val typeToken = object : TypeToken<BinaryFeatureInfo>() {}
    return gson.fromJson<BinaryFeatureInfo>(text, typeToken.type)
}

fun categoricalFactors(): CategoricalFeatureInfo {
    val text = fileContent("features/categorical.json")
    val typeToken = object : TypeToken<CategoricalFeatureInfo>() {}
    return gson.fromJson<CategoricalFeatureInfo>(text, typeToken.type)
}


fun ignoredFactors(): IgnoredFeatureInfo {
    val text = fileContent("features/ignored.json")
    val typeToken = object : TypeToken<IgnoredFeatureInfo>() {}
    return gson.fromJson<IgnoredFeatureInfo>(text, typeToken.type)
}

fun doubleFactors(): DoubleFeatureInfo {
    val text = fileContent("features/float.json")
    val typeToken = object : TypeToken<DoubleFeatureInfo>() {}
    return gson.fromJson<DoubleFeatureInfo>(text, typeToken.type)
}

fun featuresOrder(): Map<String, Int> {
    val text = fileContent("features/final_features_order.txt")
    
    var index = 0
    val map = mutableMapOf<String, Int>()
    text.split("\n").forEach { 
        val featureName = it.trim()
        map[featureName] = index++
    }
    
    return map
}

private fun fileContent(fileName: String): String {
    val fileStream = gson.javaClass.classLoader.getResourceAsStream(fileName)
    return fileStream.reader().readText()
}


typealias CompletionData = List<Map<String, Any>>

typealias CompletionItem = Map<String, Any>

fun CompletionData.findWithSessionUid(sessionUid: String): List<CompletionItem> = filter { it["sessionUid"] == sessionUid }

fun readJsonMap(fileName: String): CompletionData {
    val text = fileContent(fileName)
    val typeToken = object : TypeToken<CompletionData>() {}
    return gson.fromJson<CompletionData>(text, typeToken.type)
}