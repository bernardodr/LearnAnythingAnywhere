package com.laa.llm;

/** A call whose reply is text, streamed ({@link LlmGateway#stream}). Its task must be routed to streaming models. */
public record TextCall(Task task, String variant, String label) implements Call {}
