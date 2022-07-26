package org.codroid.textmate.grammar.tokenize

import org.codroid.textmate.*
import org.codroid.textmate.grammar.Grammar
import org.codroid.textmate.grammar.Injection
import org.codroid.textmate.grammar.LineTokens
import org.codroid.textmate.grammar.StateStack
import org.codroid.textmate.regex.FindOption
import org.codroid.textmate.regex.FindOptionConsts
import org.codroid.textmate.rule.*
import kotlin.experimental.or
import kotlin.math.min

open class MatchResult(open val captureIndices: Array<IntRange>, open val matchedRuleId: RuleId) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatchResult

        if (!captureIndices.contentEquals(other.captureIndices)) return false
        if (matchedRuleId != other.matchedRuleId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = captureIndices.contentHashCode()
        result = 31 * result + matchedRuleId.hashCode()
        return result
    }
}

class MatchInjectionsResult(
    val priorityMatch: Boolean,
    override val captureIndices: Array<IntRange>,
    override val matchedRuleId: RuleId
) : MatchResult(captureIndices, matchedRuleId) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatchInjectionsResult

        if (priorityMatch != other.priorityMatch) return false
        if (!captureIndices.contentEquals(other.captureIndices)) return false
        if (matchedRuleId != other.matchedRuleId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = priorityMatch.hashCode()
        result = 31 * result + captureIndices.contentHashCode()
        result = 31 * result + matchedRuleId.hashCode()
        return result
    }
}

data class PrepareRuleResult(val ruleScanner: CompiledRule, val findOptions: FindOption)

object RuleMatching {
    fun matchRuleOrInjections(
        grammar: Grammar,
        lineText: String,
        isFirstLine: Boolean,
        linePos: Int,
        stack: StateStack,
        anchorPosition: Int
    ): MatchResult? {
        // Look for normal grammar rule
        val matchResult = matchRule(grammar, lineText, isFirstLine, linePos, stack, anchorPosition)

        // Look for injected rules
        val injections = grammar.getInjections()
        if (injections.isEmpty()) {
            // No injections whatsover => early return
            return matchResult
        }

        val injectionResult =
            matchInjections(injections, grammar, lineText, isFirstLine, linePos, stack, anchorPosition)
                ?: return matchResult
        // No injections matched => early return

        if (matchResult == null) {
            // Only injections matched => early return
            return injectionResult
        }

        // Decide if `matchResult` or `injectionResult` should win
        val matchResultScore = matchResult.captureIndices[0].first
        val injectionResultScore = injectionResult.captureIndices[0].first

        if (injectionResultScore < matchResultScore || (injectionResult.priorityMatch && injectionResultScore == matchResultScore)) {
            // injection won!
            return injectionResult
        }
        return matchResult
    }

    private fun matchRule(
        grammar: Grammar,
        lineText: String,
        isFirstLine: Boolean,
        linePos: Int,
        stack: StateStack,
        anchorPosition: Int
    ): MatchResult? {
        val rule = stack.getRule(grammar)
        val (ruleScanner, findOption) = prepareRuleSearch(
            rule,
            grammar,
            stack.endRule,
            isFirstLine,
            linePos == anchorPosition
        ) ?: return null
        var perfStart = 0L
        if (DebugFlag) {
            perfStart = System.currentTimeMillis()
        }

        val result = ruleScanner.findNextMatchSync(lineText, linePos, findOption)
        if (DebugFlag) {
            val elapsedMillis = System.currentTimeMillis() - perfStart
            if (elapsedMillis > 5) {
                println("Rule ${rule?.debugName()} (${rule?.id} matching took $elapsedMillis against '$lineText'")
            }
            println("  scanning for (linePos: $linePos, anchorPosition: $anchorPosition")
            println(ruleScanner.toString())
            if (result != null) {
                println("matched rule id: ${result.ruleId} from ${result.captureIndices[0]}")
            }
        }

        if (result != null) {
            return MatchResult(result.captureIndices, result.ruleId)
        }
        return null
    }

    private fun matchInjections(
        injections: Array<Injection>,
        grammar: Grammar,
        lineText: String,
        isFirstLine: Boolean,
        linePos: Int,
        stack: StateStack,
        anchorPosition: Int
    ): MatchInjectionsResult? {
        // The lower, the better
        var bestMatchRating = Int.MAX_VALUE
        var bestMatchCaptureIndices: Array<IntRange>? = null
        var bestMatchRuleId: RuleId = RuleId.from(0)
        var bestMatchResultPriority: Priority = 0

        val scopes = stack.contentNameScopesList.getScopeNames()
        for (injection in injections) {
            if (!injection.matcher(scopes)) {
                // injection selector doesn't match stack
                continue
            }
            val rule = grammar.getRule(injection.ruleId)
            val (ruleScanner, findOptions) = prepareRuleSearch(
                rule,
                grammar,
                null,
                isFirstLine,
                linePos == anchorPosition
            ) ?: return null
            val matchResult = ruleScanner.findNextMatchSync(lineText, linePos, findOptions) ?: continue
            if (DebugFlag) {
                println("  matched injection: ${injection.debugSelector}")
            }
            val matchRating = matchResult.captureIndices[0].first
            if (matchRating >= bestMatchRating) {
                // Injections are sorted by priority, so the previous injection had a better or equal priority
                continue
            }
            bestMatchRating = matchRating
            bestMatchCaptureIndices = matchResult.captureIndices
            bestMatchRuleId = matchResult.ruleId
            bestMatchResultPriority = injection.priority
            if (bestMatchRating == linePos) {
                // No more need to look at the rest of the injections.
                break
            }
        }

        if (bestMatchCaptureIndices != null) {
            return MatchInjectionsResult(
                bestMatchResultPriority == (-1).toByte(),
                bestMatchCaptureIndices,
                bestMatchRuleId
            )
        }
        return null
    }

    private fun prepareRuleSearch(
        rule: Rule?,
        grammar: Grammar,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): PrepareRuleResult? {
        val runScanner = rule?.compileAG(grammar, endRegexSource ?: "", allowA, allowG) ?: return null
        return PrepareRuleResult(runScanner, FindOptionConsts.None)
    }

    fun prepareRuleWhileSearch(
        rule: BeginWhileRule,
        grammar: Grammar,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): PrepareRuleResult {
        if (UseOnigurumaFindOptions) {
            val ruleScanner = rule.compileWhile(grammar, endRegexSource ?: "")
            val findOptions = getFindOptions(allowA, allowG)
            return PrepareRuleResult(ruleScanner, findOptions)
        }
        val ruleScanner = rule.compileWhileAG(grammar, endRegexSource ?: "", allowA, allowG)
        return PrepareRuleResult(ruleScanner, FindOptionConsts.None)
    }

    private fun getFindOptions(allowA: Boolean, allowG: Boolean): FindOption {
        var options = FindOptionConsts.None
        if (!allowA) {
            options = options or FindOptionConsts.NotBeginString
        }
        if (!allowG) {
            options = options or FindOptionConsts.NotBeginPosition
        }
        return options
    }

    fun handleCaptures(
        grammar: Grammar,
        lineText: String,
        isFirstLine: Boolean,
        stack: StateStack,
        lineTokens: LineTokens,
        captures: Array<CaptureRule?>,
        captureIndices: Array<IntRange>
    ) {
        if (captures.isEmpty()) return

        val len = min(captures.size, captureIndices.size)
        val localStack = mutableListOf<LocalStackElement>()
        val maxEnd = captureIndices.first().endExclusive()

        for (i in 0 until len) {
            val captureRule = captures[i] ?: continue

            val captureIndex = captureIndices[i]
            if (captureIndex.distance() == 0) {
                // Nothing really captured
                continue
            }

            if (captureIndex.first > maxEnd) {
                // Capture going beyond consumed string
                break
            }
            // pop captures while needed
            while (localStack.isNotEmpty() && localStack.last().endPos <= captureIndex.first) {
                // pop!
                lineTokens.produceFromScopes(localStack.last().scopes, localStack.last().endPos)
                localStack.removeLast()
            }

            if (localStack.isNotEmpty()) {
                lineTokens.produceFromScopes(localStack.last().scopes, captureIndex.first)
            } else {
                lineTokens.produce(stack, captureIndex.first)
            }

            if (captureRule.retokenizeCapturedWithRuleId != RuleId.from(0)) {
                // the capture requires additional matching
                val scopeName = captureRule.getName(lineText, captureIndices)
                val nameScopesList = stack.contentNameScopesList.pushAttributed(scopeName, grammar)
                val contentName = captureRule.getContentName(lineText, captureIndices)
                val contentNameScopesList = nameScopesList.pushAttributed(contentName, grammar)

                val stackClone = stack.push(
                    captureRule.retokenizeCapturedWithRuleId, captureIndex.first,
                    -1, false, null, nameScopesList, contentNameScopesList
                )
                val onigSubStr =lineText.substring(0, captureIndex.endExclusive())
                tokenizeString(
                    grammar, onigSubStr, isFirstLine && captureIndex.first == 0, captureIndex.first,
                    stackClone, lineTokens, false, 0
                )
                continue
            }
            val captureRuleScopeName = captureRule.getName(lineText, captureIndices)
            if (captureRuleScopeName != null) {
                // push
                val base = if (localStack.isNotEmpty()) localStack.last().scopes else stack.contentNameScopesList
                val captureRuleScopesList = base.pushAttributed(captureRuleScopeName, grammar)
                localStack.add(LocalStackElement(captureRuleScopesList, captureIndex.endExclusive()))
            }
        }

        while (localStack.isNotEmpty()) {
            // pop !
            localStack.last().let {
                lineTokens.produceFromScopes(it.scopes, it.endPos)
            }
            localStack.removeLast()
        }
    }
}

