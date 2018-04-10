package com.intellij.stats.ngram

import com.intellij.lang.ASTNode
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileTypes.FileType

interface NGramElementProvider {

    /**
     * @return file types which should be indexed
     */
    fun getSupportedFileTypes() : Set<FileType>

    /**
     * @return a language-specific representation for the element
     * if the element is not supported by a provider it should return empty string
     */
    fun getElementRepresentation(element: ASTNode) : String

    /**
     * @return true if the element could appear in a completion list
     */
    fun shouldIndex(element: ASTNode, content: CharSequence): Boolean

    companion object {

        fun getSupportedFileTypes() : Set<FileType>  {
            return Extensions.getExtensions(EP_NAME).flatMap { it.getSupportedFileTypes() }.toSet()
        }

        fun getElementRepresentation(element: ASTNode): String {
            Extensions.getExtensions(EP_NAME).forEach {
                val representation = it.getElementRepresentation(element)
                if (representation.isNotEmpty()) {
                    return@getElementRepresentation representation
                }
            }
            throw IllegalStateException("no suitable representation found")
        }

        fun shouldIndex(element: ASTNode, content: CharSequence): Boolean {
            return Extensions.getExtensions(EP_NAME).all { it.shouldIndex(element, content) }
        }

        val EP_NAME = ExtensionPointName.create<NGramElementProvider>("com.intellij.stats.ngram.ngramElementProvider")
    }
}

abstract class AbstractNGramElementProvider: NGramElementProvider {
    override fun getElementRepresentation(element: ASTNode): String {
        return element.elementType.toString()
    }
}
