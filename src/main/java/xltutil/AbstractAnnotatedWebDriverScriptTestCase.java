package xltutil;

import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;

import com.xceptance.xlt.api.engine.scripting.AbstractWebDriverScriptTestCase;

import xltutil.annotation.TestTargets;
import xltutil.runner.AnnotationRunner;

/**
 * Specialization of {@link AbstractWebDriverScriptTestCase} that adds the ability to run a test case multiple times
 * using the specified browser configurations.
 * <p>
 * To use this functionality, let your test classes inherit from this class instead of
 * {@link AbstractWebDriverScriptTestCase} and add a {@link TestTargets} annotation to your test classes.
 */
@RunWith(AnnotationRunner.class)
public abstract class AbstractAnnotatedWebDriverScriptTestCase extends AbstractWebDriverScriptTestCase
{
    /**
     * Creates a new test case instance and initializes it with a default {@link WebDriver} instance and an empty base
     * URL. What the default driver will be can be configured in the settings of your test suite.
     * <p>
     * Note: Since the driver will be created implicitly, it will also be quit implicitly.
     */
    public AbstractAnnotatedWebDriverScriptTestCase()
    {
        this(null);
    }

    /**
     * Creates a new test case instance and initializes it with a default {@link WebDriver} instance and the given base
     * URL. What the default driver will be can be configured in the settings of your test suite.
     * <p>
     * Note: Since the driver will be created implicitly, it will also be quit implicitly.
     *
     * @param baseUrl
     *            the base URL against which relative URLs will be resolved
     */
    public AbstractAnnotatedWebDriverScriptTestCase(final String baseUrl)
    {
        super(null, baseUrl);
    }
}
