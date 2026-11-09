package com.laa.llm;

/** Provider-neutral chat turn. */
public record ChatMessage(Role role, String content) {
    public enum Role { USER, ASSISTANT }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
