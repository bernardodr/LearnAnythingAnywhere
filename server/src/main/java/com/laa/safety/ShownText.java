package com.laa.safety;

import java.util.function.UnaryOperator;

/**
 * A structured model result with text a learner will see. It says which text that is by rebuilding itself with
 * each shown string passed through {@code f}; ids, verdicts and anything else stay as they are.
 *
 * <p>The {@link SafetyLayer} uses it twice: to apply the links policy, and to collect the text it checks. So a result
 * type can't get the safety check without the links policy, or the other way round.
 */
public interface ShownText<T> {

    T mapShown(UnaryOperator<String> f);
}
