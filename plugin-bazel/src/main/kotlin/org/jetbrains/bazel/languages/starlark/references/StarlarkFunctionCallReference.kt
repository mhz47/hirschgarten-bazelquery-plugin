package org.jetbrains.bazel.languages.starlark.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.bazel.languages.starlark.completion.lookups.StarlarkLookupElement
import org.jetbrains.bazel.languages.starlark.psi.expressions.StarlarkCallExpression
import org.jetbrains.bazel.languages.starlark.rename.RenameUtils

class StarlarkFunctionCallReference(element: StarlarkCallExpression, rangeInElement: TextRange) :
  PsiReferenceBase<StarlarkCallExpression>(element, rangeInElement, true) {
  override fun resolve(): PsiElement? =
    myElement?.let {
      val processor = StarlarkResolveProcessor(mutableListOf(), it)
      SearchUtils.searchInFile(it, processor)
      processor.result.firstOrNull()
    }

  override fun getVariants(): Array<StarlarkLookupElement> = emptyArray()

  override fun handleElementRename(name: String): PsiElement {
    val namePsi = myElement.getNamePsi() ?: return myElement
    val oldNode = namePsi.getNameNode() ?: return myElement
    val newNode = RenameUtils.createNewName(myElement.project, name)
    namePsi.node.replaceChild(oldNode, newNode)
    return myElement
  }
}
