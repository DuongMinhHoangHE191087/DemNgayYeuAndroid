package com.example.shared.ai.fakes

import com.example.shared.ai.AiGiftAdviceBundle
import com.example.shared.ai.AiGiftAdvisorGateway
import com.example.shared.ai.AiGiftRequest
import com.example.shared.ai.SmartRuleGiftAdvisor

/**
 * In-memory fake implementation of [AiGiftAdvisorGateway] for tests and offline previews.
 */
class FakeAiGiftAdvisorGateway(
    var cannedResponse: AiGiftAdviceBundle? = null,
    var failureToThrow: Throwable? = null,
) : AiGiftAdvisorGateway {

    private val _recordedRequests = mutableListOf<AiGiftRequest>()
    val recordedRequests: List<AiGiftRequest> get() = _recordedRequests.toList()

    override suspend fun advise(request: AiGiftRequest): AiGiftAdviceBundle {
        _recordedRequests += request
        failureToThrow?.let { throw it }
        return cannedResponse ?: SmartRuleGiftAdvisor.generate(request)
    }
}
