package xltutil;

import org.junit.runner.RunWith;

import com.xceptance.xlt.api.engine.scripting.AbstractScriptTestCase;

import xltutil.annotation.TestTargets;
import xltutil.runner.AnnotationRunner;

/**
 * Specialization of {@link AbstractScriptTestCase} that adds the ability to run a test case multiple times using the
 * specified browser configurations.
 * <p>
 * To use this class you simply inherit from this and add a {@link TestTargets} annotation.
 */
@RunWith(AnnotationRunner.class)
public abstract class AbstractAnnotatedScriptTestCase extends AbstractScriptTestCase
{
}
