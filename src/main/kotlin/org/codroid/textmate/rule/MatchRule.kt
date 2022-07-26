package org.codroid.textmate.rule

import org.codroid.textmate.RegexSource

class MatchRule(
    override val id: RuleId,
    override val name: String?,
    override val contentName: String? = null,
    match: String,
    val captures: Array<CaptureRule?>
) : Rule() {
    override val nameIsCapturing: Boolean = RegexSource.hasCaptures(name)
    override val contentNameIsCapturing: Boolean = RegexSource.hasCaptures(contentName)
    private var cachedCompiledPatterns: RegExpSourceList? = null
    private val match = RegExpSource(match, this.id)
    override fun dispose() {
        if (this.cachedCompiledPatterns != null) {
            this.cachedCompiledPatterns!!.dispose()
            this.cachedCompiledPatterns = null
        }
    }

    fun debugMatchRegExp(): String = this.match.source

    override fun collectPatterns(grammar: RuleRegistry, out: RegExpSourceList) {
        out.push(match)
    }

    override fun compile(grammar: RuleRegistryRegexLib, endRegexSource: String): CompiledRule =
        this.getCachedCompiledPatterns(grammar).compile(grammar)

    override fun compileAG(
        grammar: RuleRegistryRegexLib,
        endRegexSource: String,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule = this.getCachedCompiledPatterns(grammar).compileAG(grammar, allowA, allowG)

    private fun getCachedCompiledPatterns(grammar: RuleRegistryRegexLib): RegExpSourceList {
        if (this.cachedCompiledPatterns == null) {
            this.cachedCompiledPatterns = RegExpSourceList()
            this.collectPatterns(grammar, this.cachedCompiledPatterns!!)
        }
        return this.cachedCompiledPatterns!!
    }

}