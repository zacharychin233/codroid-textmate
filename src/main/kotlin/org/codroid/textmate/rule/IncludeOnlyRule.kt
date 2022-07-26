package org.codroid.textmate.rule

import org.codroid.textmate.RegexSource

class IncludeOnlyRule(
    override val id: RuleId,
    override val name: String?,
    override val contentName: String?,

    patterns: CompilePatternsResult
) : Rule(), WithPatternRule {
    override val nameIsCapturing: Boolean = RegexSource.hasCaptures(name)
    override val contentNameIsCapturing: Boolean = RegexSource.hasCaptures(contentName)


    override val patterns: Array<RuleId>
    override val hasMissingPatterns: Boolean
    private var cachedCompiledPatterns: RegExpSourceList?

    init {
        this.patterns = patterns.patterns
        this.hasMissingPatterns = patterns.hasMissingPatterns
        this.cachedCompiledPatterns = null
    }

    override fun dispose() {
        if (this.cachedCompiledPatterns != null) {
            this.cachedCompiledPatterns!!.dispose()
            this.cachedCompiledPatterns = null
        }
    }

    override fun collectPatterns(grammar: RuleRegistry, out: RegExpSourceList) {
        for (pattern in this.patterns) {
            grammar.getRule(pattern)?.collectPatterns(grammar, out)
        }
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