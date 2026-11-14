package com.laa.llm;

/**
 * One kind of model call the app makes: the task (which decides the route), the prompt variant (null for the task's
 * main prompt) and what comes back. It's the only thing a caller passes to say what it's calling, so the three can't
 * disagree. The registered ones are in {@link Calls}; a {@link TextCall} is streamed, a {@link StructuredCall} returns
 * a validated object.
 */
public sealed interface Call permits TextCall, StructuredCall {

    Task task();

    /** {@code prompts/<task>.<variant>.<version>.md}; null means the task's main prompt. */
    String variant();

    /** How the owner console names it, e.g. "Leak check". */
    String label();

    /** {@code TASK} or {@code TASK.variant}, matching the prompt file: {@code SAFETY_CHECK.content}. */
    default String id() {
        return variant() == null ? task().name() : task().name() + "." + variant();
    }
}
