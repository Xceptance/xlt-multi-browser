package xltutil;

import org.junit.runner.RunWith;

import com.xceptance.xlt.api.tests.AbstractWebDriverTestCase;

import xltutil.annotation.TestTargets;
import xltutil.runner.AnnotationRunner;

/**
 * Specialization of {@link AbstractWebDriverTestCase} that adds the ability to run a test case multiple times using the
 * specified browser configurations.
 * <p>
 * To use this functionality, let your test classes inherit from this class instead of {@link AbstractWebDriverTestCase}
 * and add a {@link TestTargets} annotation to your test classes.
 */
@RunWith(AnnotationRunner.class)
public abstract class AbstractAnnotatedWebDriverTestCase extends AbstractWebDriverTestCase
{
}
