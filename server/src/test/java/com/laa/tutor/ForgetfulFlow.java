package com.laa.tutor;

import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.PromptContext;

/**
 * Test fixture for {@link com.laa.ArchitectureTest}: a learner flow that forgets the safety layer and calls the
 * gateway itself. The architecture rule must reject it. Never used at runtime.
 */
public class ForgetfulFlow {

    private final LlmGateway gateway;

    ForgetfulFlow(LlmGateway gateway) {
        this.gateway = gateway;
    }

    Object reply(PromptContext ctx) {
        return gateway.stream(Calls.TUTOR_REPLY, ctx, GenerationProfile.ADULT_DEFAULT);
    }
}
