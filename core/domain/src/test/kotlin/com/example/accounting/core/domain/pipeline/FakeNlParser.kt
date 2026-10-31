package com.example.accounting.core.domain.pipeline

/** Scripted [NlParser] for tests. Returns queued results in order and records every text it was given. */
class FakeNlParser(vararg responses: ParserResult) : NlParser {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<String>()

    override suspend fun parse(text: String): ParserResult {
        requests += text
        return checkNotNull(queue.removeFirstOrNull()) { "FakeNlParser: no scripted response left" }
    }
}
