package com.laa;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.lesson.LessonWriter;
import com.laa.llm.LlmGateway;
import com.laa.tutor.ForgetfulFlow;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules that make forgetting a safety step fail a test (BRIEF section 8).
 *
 * <p>Learner flows never call the LLM gateway themselves: they go through the safety layer, which checks what the
 * learner typed and everything the model writes for them. Only the gateway's own package, the safety layer, and the
 * owner and dev tools (which write nothing for a learner) may depend on it, plus the exceptions below, each with its
 * reason. Adding an exception is a deliberate, reviewable change to this file.
 */
class ArchitectureTest {

    /** Classes outside the allowed packages that may still call the gateway, and why. */
    private static final Map<Class<?>, String> EXCEPTIONS = Map.of(
            LessonWriter.class, "plans and drafts lessons; a draft is saved only as a CheckedLesson, which only LessonChecks makes");

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.laa");

    static final ArchRule ONLY_THE_SAFETY_LAYER_CALLS_THE_GATEWAY = noClasses()
            .that().resideOutsideOfPackages("com.laa.llm..", "com.laa.safety..", "com.laa.owner..", "com.laa.dev..")
            .and(not(belongToAnyOf(EXCEPTIONS.keySet().toArray(Class<?>[]::new))))
            .should().dependOnClassesThat().areAssignableTo(LlmGateway.class)
            .because("learner-facing text must come from the SafetyLayer, which checks it against the reader's settings");

    static final ArchRule ONLY_THE_GATEWAY_TALKS_TO_PROVIDERS = noClasses()
            .that().resideOutsideOfPackage("com.laa.llm..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..")
            .because("every model call goes through the LLM gateway (routing, fallbacks, metering and safety)");

    @Test
    void learnerFlowsReachTheModelOnlyThroughTheSafetyLayer() {
        ONLY_THE_SAFETY_LAYER_CALLS_THE_GATEWAY.check(MAIN);
    }

    @Test
    void nothingBypassesTheGateway() {
        ONLY_THE_GATEWAY_TALKS_TO_PROVIDERS.check(MAIN);
    }

    @Test
    void theRuleCatchesAFlowThatCallsTheGatewayDirectly() {
        JavaClasses forgetful = new ClassFileImporter().importClasses(ForgetfulFlow.class);
        assertThatThrownBy(() -> ONLY_THE_SAFETY_LAYER_CALLS_THE_GATEWAY.check(forgetful))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ForgetfulFlow");
    }
}
