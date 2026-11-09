package com.laa.llm;

/** FAKE and FAKE_B are deterministic offline providers (tests, demos without keys). */
public enum Provider {
    OPENAI("OpenAI"),
    GOOGLE("Google Gemini"),
    ANTHROPIC("Anthropic Claude"),
    OLLAMA("Ollama (local)"),
    FAKE("Fake"),
    FAKE_B("Fake B");

    private final String label;

    Provider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isFake() {
        return this == FAKE || this == FAKE_B;
    }
}
