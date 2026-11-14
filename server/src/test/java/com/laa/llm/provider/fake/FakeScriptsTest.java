package com.laa.llm.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.laa.llm.Calls;
import com.laa.llm.StructuredCall;

/** Practice mode has a scripted reply for every structured call the app makes, matched on the call, not its shape. */
class FakeScriptsTest {

    @Test
    void everyStructuredCallHasAPracticeReply() {
        Calls.all().stream().filter(c -> c instanceof StructuredCall<?>)
                .forEach(c -> assertThat(FakeScripts.scripted(c)).as(c.id()).isTrue());
    }
}
