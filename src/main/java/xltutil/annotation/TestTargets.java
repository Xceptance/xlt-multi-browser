package xltutil.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import xltutil.AbstractAnnotatedScriptTestCase;

/**
 * This annotation is used in context of XLT test cases to add one or more browser configurations.
 * <p>
 * Annotate a class that extends {@link AbstractAnnotatedScriptTestCase} with {@link TestTargets} and add as annotation
 * value a list of test targets. These targets refer to the browser profiles (browser-tag) as configured in
 * &quot;browser.properties&quot; located in sub-directory &quot;config&quot; of your test suite.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestTargets
{
    String[] value();
}
