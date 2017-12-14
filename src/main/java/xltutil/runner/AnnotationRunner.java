package xltutil.runner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.ie.InternetExplorerDriverService;
import org.openqa.selenium.opera.OperaDriverService;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;

import com.xceptance.xlt.api.data.DataSetProvider;
import com.xceptance.xlt.api.data.DataSetProviderException;
import com.xceptance.xlt.api.engine.Session;
import com.xceptance.xlt.api.tests.AbstractWebDriverTestCase;
import com.xceptance.xlt.api.util.XltLogger;
import com.xceptance.xlt.api.util.XltProperties;
import com.xceptance.xlt.engine.data.DataSetProviderFactory;
import com.xceptance.xlt.engine.scripting.XlteniumScriptInterpreter;
import com.xceptance.xlt.engine.util.ScriptingUtils;
import com.xceptance.xlt.engine.util.XltTestRunner;

import xltutil.AbstractAnnotatedScriptTestCase;
import xltutil.annotation.TestTargets;
import xltutil.dto.BrowserConfigurationDto;
import xltutil.dto.ProxyConfigurationDto;
import xltutil.mapper.PropertiesToProxyConfigurationMapper;
import xltutil.runner.helper.AnnotationRunnerHelper;
import xltutil.runner.helper.XltPropertyKey;

/**
 * JUnit runner used to run tests that inherit from {@link AbstractAnnotatedScriptTestCase}. This class reads the
 * annotation based configuration of {@link RunWithBrowser} and executes the annotated test class multiple-times with
 * different configurations.
 * 
 * @see {@link AbstractAnnotatedScriptTestCase}, {@link RunWithBrowser}
 */
public class AnnotationRunner extends XltTestRunner
{

    /**
     * The list of directories to be searched for data set files.
     */
    private static final List<File> dataSetFileDirs = new ArrayList<File>();

    /**
     * An empty data set.
     */
    private static final Map<String, String> EMPTY_DATA_SET = Collections.emptyMap();

    /**
     * The current directory.
     */
    protected static final File CURRENT_DIR = new File(".");

    /**
     * The data sets directory as specified in the XLT configuration. Maybe <code>null</code> if not configured.
     */
    protected static final File DATA_SETS_DIR;

    private static final String SYSTEM_PROPERTY_BROWSERDEFINITION = "browserdefinition";

    private final ProxyConfigurationDto proxyConfig;

    static
    {
        final String dataSetFileDirectoryName = XltProperties.getInstance().getProperty("com.xceptance.xlt.data.dataSets.dir", "");
        if (dataSetFileDirectoryName.length() > 0)
        {
            final File dir = new File(dataSetFileDirectoryName);

            DATA_SETS_DIR = dir.isDirectory() ? dir : null;
        }
        else
        {
            DATA_SETS_DIR = null;
        }

        // 1. the current directory
        dataSetFileDirs.add(CURRENT_DIR);

        // 2. the data sets directory if available
        if (DATA_SETS_DIR != null)
        {
            dataSetFileDirs.add(DATA_SETS_DIR);
        }

        // 3. the scripts directory
        dataSetFileDirs.add(XlteniumScriptInterpreter.SCRIPTS_DIRECTORY);
    }

    /**
     * The JUnit children of this runner.
     */
    private final List<FrameworkMethod> methods = new ArrayList<FrameworkMethod>();

    /**
     * The instances of the test case mapped by test method.
     * <p>
     * N.B.: Mapping is necessary as multiple threads might access this runner instance, e.g. when running JUnit tests
     * in parallel.
     */
    private final Map<FrameworkMethod, Object> _testInstances = new ConcurrentHashMap<>();

    /**
     * Sets the test instance up.
     *
     * @param method
     *            the method
     * @param test
     *            the test instance
     */
    protected void setUpTest(final FrameworkMethod method, final Object test)
    {
        if (test instanceof AbstractWebDriverTestCase && method instanceof AnnotatedFrameworkMethod)
        {
            // set the test data set at the test instance
            final AnnotatedFrameworkMethod frameworkMethod = (AnnotatedFrameworkMethod) method;
            final AbstractWebDriverTestCase testInstance = (AbstractWebDriverTestCase) test;

            // get the browser configuration for this testcase
            final BrowserConfigurationDto config = frameworkMethod.getBrowserConfiguration();

            // instantiate webdriver according to browser configuration
            WebDriver driver = null;
            try
            {
                driver = AnnotationRunnerHelper.createWebdriver(config, proxyConfig);
            }
            catch (final MalformedURLException e)
            {
                throw new RuntimeException("An error occured during URL creation. See nested exception.", e);
            }

            if (driver != null)
            {
                // set browser window size
                AnnotationRunnerHelper.setBrowserWindowSize(config, driver);
                testInstance.setWebDriver(driver);
                testInstance.setTestDataSet(frameworkMethod.getDataSet());

                _testInstances.put(frameworkMethod, testInstance);

            }
            else
            {
                throw new RuntimeException("Could not create driver for browsertag: " + config.getConfigTag()
                                           + ". Please check your browserconfigurations.");
            }
        }
    }

    /**
     * Sets the test instance up.
     *
     * @param test
     *            the test instance
     */
    protected void tearDownTest(final Object test)
    {
        if (test instanceof AbstractWebDriverTestCase)
        {
            final WebDriver webDriver = ((AbstractWebDriverTestCase) test).getWebDriver();
            if (webDriver != null)
            {
                try
                {
                    webDriver.getWindowHandle();
                }
                catch (final WebDriverException e)
                {
                    // WebDriver might already be closed
                    // eat exception and return
                    return;
                }

                webDriver.quit();
            }
        }
    }

    public AnnotationRunner(final Class<?> testCaseClass) throws Throwable
    {
        this(testCaseClass, ScriptingUtils.getScriptName(testCaseClass),
             ScriptingUtils.getScriptBaseName(ScriptingUtils.getScriptName(testCaseClass)), dataSetFileDirs);
    }

    public AnnotationRunner(final Class<?> testCaseClass, final String testCaseName, final String defaultTestMethodName,
        final List<File> dataSetFileDirs)
        throws Throwable
    {
        super(testCaseClass);

        // get the short (package-less) test case name
        final String shortTestCaseName = StringUtils.contains(testCaseName, '.') ? StringUtils.substringAfterLast(testCaseName, ".")
                                                                                 : testCaseName;
        // get the data sets
        final List<Map<String, String>> dataSets = getDataSets(testCaseClass, testCaseName, shortTestCaseName, dataSetFileDirs);

        final XltProperties xltProperties = XltProperties.getInstance();

        // parse proxy settings
        proxyConfig = new PropertiesToProxyConfigurationMapper().toDto(xltProperties);

        // parse browser properties
        final Map<String, BrowserConfigurationDto> parsedBrowserProperties = AnnotationRunnerHelper.parseBrowserProperties(xltProperties);

        final String ieDriverPath = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_PATH_IE);
        final String chromeDriverPath = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_PATH_CHROME);
        final String geckoDriverPath = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_PATH_FIREFOX);
        final String edgeDriverPath = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_PATH_EDGE);
        final String operaDriverPath = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_PATH_OPERA);
        final String phantomJSDriverPath = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_PATH_PHANTOMJS);

        // shall we run Firefox in legacy mode?
        final boolean firefoxLegacy = xltProperties.getProperty(XltPropertyKey.WEBDRIVER_FIREFOX_LEGACY, false);
        System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE, Boolean.toString(!firefoxLegacy));

        if (!StringUtils.isEmpty(ieDriverPath))
        {
            System.setProperty(InternetExplorerDriverService.IE_DRIVER_EXE_PROPERTY, ieDriverPath);
        }
        if (!StringUtils.isEmpty(chromeDriverPath))
        {
            System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, chromeDriverPath);
        }
        if (!StringUtils.isEmpty(geckoDriverPath))
        {
            System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, geckoDriverPath);
        }
        if (!StringUtils.isEmpty(edgeDriverPath))
        {
            System.setProperty(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY, edgeDriverPath);
        }
        if (!StringUtils.isEmpty(operaDriverPath))
        {
            System.setProperty(OperaDriverService.OPERA_DRIVER_EXE_PROPERTY, operaDriverPath);
        }
        if (!StringUtils.isEmpty(phantomJSDriverPath))
        {
            System.setProperty(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, phantomJSDriverPath);
        }
        boolean foundTargetsAnnotation = false;

        // get test specific browser definitions (aka browser tag see browser.properties)
        // could be one value or comma separated list of values
        String browserDefinitionsProperty = XltProperties.getInstance().getProperty(SYSTEM_PROPERTY_BROWSERDEFINITION, "");
        if (browserDefinitionsProperty != null)
            browserDefinitionsProperty = browserDefinitionsProperty.replaceAll("\\s", "");

        List<String> browserDefinitions = null;

        // parse test specific browser definitions
        if (!StringUtils.isEmpty(browserDefinitionsProperty))
        {
            browserDefinitions = Arrays.asList(browserDefinitionsProperty.split(","));
        }

        // Get annotations of test class.
        final Annotation[] annotations = testCaseClass.getAnnotations();
        for (final Annotation annotation : annotations)
        {
            // only check TestTargets annotation with a list of nested TestTarget annotations
            if (annotation instanceof TestTargets)
            {
                foundTargetsAnnotation = true;

                final String[] targets = ((TestTargets) annotation).value();

                for (final String target : targets)
                {
                    // check if the annotated target is in the list of targets specified via system property
                    if (browserDefinitions != null && !browserDefinitions.contains(target))
                    {
                        continue;
                    }

                    final BrowserConfigurationDto foundBrowserConfiguration = parsedBrowserProperties.get(target);
                    if (foundBrowserConfiguration == null)
                    {
                        throw new IllegalArgumentException("Can not find browser configuration with tag: " + target);
                    }

                    for (final FrameworkMethod frameworkMethod : getTestClass().getAnnotatedMethods(Test.class))
                    {
                        // get the test method to run
                        final Method testMethod = frameworkMethod.getMethod();

                        // check whether to override the test method name
                        final String testMethodName = (defaultTestMethodName == null) ? testMethod.getName() : defaultTestMethodName;

                        // create the JUnit children
                        if (dataSets == null || dataSets.isEmpty())
                        {
                            methods.add(new AnnotatedFrameworkMethod(frameworkMethod.getMethod(), testMethodName, foundBrowserConfiguration, -1, EMPTY_DATA_SET));
                        }
                        else
                        {
                            // run the method once for each data set
                            int i = 0;
                            for (final Map<String, String> dataSet : dataSets)
                            {
                                methods.add(new AnnotatedFrameworkMethod(frameworkMethod.getMethod(), testMethodName, foundBrowserConfiguration, i++, dataSet));
                            }
                        }
                    }
                }
            }
        }

        if (!foundTargetsAnnotation)
            throw new IllegalArgumentException("The class (" + testCaseClass.getSimpleName()
                                               + ") does not have a required TestTargets annotation.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<FrameworkMethod> getChildren()
    {
        return methods;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Description getDescription()
    {
        final Description description = Description.createSuiteDescription(getTestClass().getJavaClass());

        for (final FrameworkMethod frameworkMethod : getChildren())
        {
            description.addChild(Description.createTestDescription(getTestClass().getJavaClass(), frameworkMethod.getName()));
        }

        return description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Statement methodInvoker(final FrameworkMethod method, final Object test)
    {
        // prepare the test instance before executing it
        setUpTest(method, test);

        // the real job is done here
        return super.methodInvoker(method, test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Statement methodBlock(FrameworkMethod method)
    {
        final Statement originalStatement = super.methodBlock(method);

        // return an intermediate statement that wraps the original statement
        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                try
                {
                    // the real job is done here
                    originalStatement.evaluate();
                }
                finally
                {
                    // quit browser
                    tearDownTest(_testInstances.remove(method)); // get test instance and remove it
                }
            }
        };
    }

    /**
     * Returns the test data sets associated with the given test case class.
     *
     * @param testClass
     *            the test case class
     * @param fullTestCaseName
     *            the full test case name
     * @param shortTestCaseName
     *            the short test case name
     * @param dataSetFileDirs
     *            the list of directories to search for data set files
     * @return the data sets, or <code>null</code> if there are no associated test data sets
     * @throws DataSetProviderException
     *             if the responsible data set provider cannot be created
     * @throws FileNotFoundException
     *             if an explicitly configured data set file cannot be found
     * @throws IOException
     */
    private List<Map<String, String>> getDataSets(final Class<?> testClass, final String fullTestCaseName, final String shortTestCaseName,
                                                  final List<File> dataSetFileDirs)
        throws DataSetProviderException, FileNotFoundException, IOException
    {
        // check whether we are in load test mode
        if (Session.getCurrent().isLoadTest())
        {
            // data set providers are not supported in load test mode
            return null;
        }

        // check whether data-driven tests are enabled
        final boolean enabled = XltProperties.getInstance().getProperty("com.xceptance.xlt.data.dataDrivenTests.enabled", true);
        if (!enabled)
        {
            return null;
        }

        // check whether a specific file has been configured
        final String specificFileNameKey1 = testClass.getName() + ".dataSetsFile";
        String specificFileName = XltProperties.getInstance().getProperty(specificFileNameKey1, "");
        if (specificFileName.length() == 0)
        {
            final String specificFileNameKey2 = fullTestCaseName + ".dataSetsFile";
            specificFileName = XltProperties.getInstance().getProperty(specificFileNameKey2, "");
        }

        if (specificFileName.length() != 0)
        {
            // there is a specific file
            File batchDataFile = new File(specificFileName);
            if (batchDataFile.isAbsolute())
            {
                // absolute -> try it as is
                return readDataSets(batchDataFile);
            }
            else
            {
                // relative -> search for it in the usual directories
                for (final File directory : dataSetFileDirs)
                {
                    batchDataFile = new File(directory, specificFileName);
                    if (batchDataFile.isFile())
                    {
                        return readDataSets(batchDataFile);
                    }
                }

                throw new FileNotFoundException("Specific test data set file name configured, but file could not be found: "
                                                + specificFileName);
            }
        }
        else
        {
            // no specific file -> try the usual suspects
            final Set<String> fileNames = new LinkedHashSet<String>();

            final String dottedName = fullTestCaseName;
            final String slashedName = dottedName.replace('.', '/');

            final DataSetProviderFactory dataSetProviderFactory = DataSetProviderFactory.getInstance();
            for (final String fileExtension : dataSetProviderFactory.getRegisteredFileExtensions())
            {
                final String suffix = "_datasets." + fileExtension;

                fileNames.add(slashedName + suffix);
                fileNames.add(dottedName + suffix);
            }

            // look for such a file in the usual directories
            return getDataSets(dataSetFileDirs, fileNames, testClass);
        }
    }

    /**
     * Looks for a data set file and, if found, returns its the data sets. Tries all the specified file names in all the
     * passed directories and finally in the class path.
     *
     * @param dataSetFileDirs
     *            the directories to search
     * @param fileNames
     *            the file names to try
     * @param testClass
     *            the test case class as the class path context
     * @return the data sets, or <code>null</code> if no data sets file was found
     * @throws IOException
     *             if an I/O error occurred
     * @throws DataSetProviderException
     *             if there is no responsible data set provider
     */
    private List<Map<String, String>> getDataSets(final List<File> dataSetFileDirs, final Set<String> fileNames, final Class<?> testClass)
        throws IOException
    {
        // look for a data set file in the passed directories
        for (final File directory : dataSetFileDirs)
        {
            for (final String fileName : fileNames)
            {
                final File batchDataFile = new File(directory, fileName);
                if (batchDataFile.isFile())
                {
                    return readDataSets(batchDataFile);
                }
            }
        }

        // look for a data set file in the class path
        for (final String fileName : fileNames)
        {
            try (final InputStream input = testClass.getResourceAsStream("/" + fileName))
            {
                if (input != null)
                {
                    File batchDataFile = null;

                    try
                    {
                        // copy the stream to a temporary file
                        final String extension = "." + FilenameUtils.getExtension(fileName);
                        batchDataFile = File.createTempFile("dataSets_", extension);
                        try (final OutputStream output = new FileOutputStream(batchDataFile))
                        {

                            IOUtils.copy(input, output);
                            output.flush();

                            // read the data sets from the temporary file
                            return readDataSets(batchDataFile);
                        }
                    }
                    finally
                    {
                        // clean up
                        FileUtils.deleteQuietly(batchDataFile);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns the test data sets contained in the given test data file. The data set provider used to read the file is
     * determined from the data file's extension.
     *
     * @param dataSetsFile
     *            the test data set file
     * @return the data sets
     * @throws DataSetProviderException
     *             if there is no responsible data set provider
     */
    private List<Map<String, String>> readDataSets(final File dataSetsFile) throws DataSetProviderException
    {
        XltLogger.runTimeLogger.debug("Test data set file used: " + dataSetsFile.getAbsolutePath());

        final DataSetProviderFactory dataSetProviderFactory = DataSetProviderFactory.getInstance();
        final String fileExtension = FilenameUtils.getExtension(dataSetsFile.getName());
        final DataSetProvider dataSetProvider = dataSetProviderFactory.createDataSetProvider(fileExtension);

        return dataSetProvider.getAllDataSets(dataSetsFile);
    }
}
