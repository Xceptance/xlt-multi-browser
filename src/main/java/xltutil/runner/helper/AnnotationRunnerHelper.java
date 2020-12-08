package xltutil.runner.helper;

import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.opera.OperaDriver;
import org.openqa.selenium.opera.OperaOptions;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.CommandInfo;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import com.xceptance.xlt.api.util.XltProperties;
import com.xceptance.xlt.api.webdriver.XltChromeDriver;
import com.xceptance.xlt.api.webdriver.XltFirefoxDriver;
import com.xceptance.xlt.engine.SessionImpl;

import xltutil.annotation.TestTargets;
import xltutil.dto.BrowserConfigurationDto;
import xltutil.dto.ProxyConfigurationDto;
import xltutil.mapper.PropertiesToBrowserConfigurationMapper;
import xltutil.proxy.ProxyHttpClient;

public final class AnnotationRunnerHelper
{
    private static Set<String> chromeBrowsers = new HashSet<String>();

    private static Set<String> firefoxBrowsers = new HashSet<String>();

    private static Set<String> internetExplorerBrowsers = new HashSet<String>();

    private static Set<String> operaBrowsers = new HashSet<>();

    private static Set<String> safariBrowsers = new HashSet<>();

    static
    {
        initBrowserTypes();
    }

    /**
     * Initializes the sets of known browser types.
     */
    @SuppressWarnings("deprecation")
    private static void initBrowserTypes()
    {
        chromeBrowsers.add(BrowserType.ANDROID);
        chromeBrowsers.add(BrowserType.CHROME);
        chromeBrowsers.add(BrowserType.GOOGLECHROME);

        firefoxBrowsers.add(BrowserType.FIREFOX);
        firefoxBrowsers.add(BrowserType.FIREFOX_CHROME);
        firefoxBrowsers.add(BrowserType.FIREFOX_PROXY);

        operaBrowsers.add(BrowserType.OPERA);
        operaBrowsers.add(BrowserType.OPERA_BLINK);

        safariBrowsers.add(BrowserType.SAFARI);
        safariBrowsers.add(BrowserType.SAFARI_PROXY);

        internetExplorerBrowsers.add(BrowserType.IE);
        internetExplorerBrowsers.add(BrowserType.IE_HTA);
        internetExplorerBrowsers.add(BrowserType.IEXPLORE);
        internetExplorerBrowsers.add(BrowserType.IEXPLORE_PROXY);
    }

    /**
     * The prefix of all factory-related configuration settings.
     */
    private static final String PROP_PREFIX_WEB_DRIVER = "xlt.webDriver";

    /**
     * Returns an {@link URL} to a Selenium grid (e.g. SauceLabs) that contains basic authentication for access
     * 
     * @return {@link URL} to Selenium grid augmented with credentials
     * @throws MalformedURLException
     */
    public static HttpCommandExecutor createGridExecutor(final ProxyConfigurationDto proxyConfig, final URL gridUrl, final String gridUsername,
                                                         final String gridPassword)
        throws MalformedURLException
    {
        // create a configuration for accessing target site via proxy (if a proxy is defined)
        // the proxy and the destination site will have different or no credentials for accessing them
        // so we need to create different authentication scopes and link them with the credentials
        final BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();

        // create credentials for proxy access
        if (proxyConfig != null //
            && !StringUtils.isEmpty(proxyConfig.getUsername()) //
            && !StringUtils.isEmpty(proxyConfig.getPassword()))
        {
            final AuthScope proxyAuth = new AuthScope(proxyConfig.getHost(), Integer.valueOf(proxyConfig.getPort()));
            final Credentials proxyCredentials = new UsernamePasswordCredentials(proxyConfig.getUsername(), proxyConfig.getPassword());
            basicCredentialsProvider.setCredentials(proxyAuth, proxyCredentials);
        }

        // create credentials for target website
        final AuthScope gridAuth = new AuthScope(gridUrl.getHost(), gridUrl.getPort());

        if (!StringUtils.isEmpty(gridUsername))
        {
            final Credentials gridCredentials = new UsernamePasswordCredentials(gridUsername, gridPassword);
            basicCredentialsProvider.setCredentials(gridAuth, gridCredentials);
        }

        // now create a http client, set the custom proxy and inject the credentials
        final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
        if (proxyConfig != null)
            clientBuilder.setProxy(new HttpHost(proxyConfig.getHost(), Integer.valueOf(proxyConfig.getPort())));
        final CloseableHttpClient httpClient = clientBuilder.build();

        final Map<String, CommandInfo> additionalCommands = new HashMap<String, CommandInfo>(); // just a dummy

        // this command executor will do the credential magic for us. both proxy and target site credentials
        return new HttpCommandExecutor(additionalCommands, gridUrl, new ProxyHttpClient(httpClient));

    }

    /**
     * Sets the browser window size
     * <p>
     * Reads the default size from xlt properties and applies them to the browser window as long as its no
     * device-emulation test. In case of device-emulation the emulated device specifies the size of the browser window.
     *
     * @param config
     * @param driver
     */
    public static void setBrowserWindowSize(final BrowserConfigurationDto config, final WebDriver driver)
    {
        final XltProperties props = XltProperties.getInstance();

        // get the configured window size and set it if defined
        final int windowWidth = props.getProperty(getEffectiveKey(PROP_PREFIX_WEB_DRIVER + ".window.width"), -1);
        final int windowHeight = props.getProperty(getEffectiveKey(PROP_PREFIX_WEB_DRIVER + ".window.height"), -1);

        final int configuredBrowserWidth = config.getBrowserWidth();
        final int configuredBrowserHeight = config.getBrowserHeight();

        Dimension browserSize = null;
        // first check if the configured browser profile has a defined size
        if (configuredBrowserWidth > 0 && configuredBrowserHeight > 0)
        {
            browserSize = new Dimension(configuredBrowserWidth, configuredBrowserHeight);
        }
        // fall back to XLT default browser size if defined
        else if (windowWidth > 0 && windowHeight > 0)
        {
            browserSize = new Dimension(windowWidth, windowHeight);
        }

        if (browserSize != null)
        {
            try
            {
                driver.manage().window().setSize(browserSize);
            }
            catch (final UnsupportedCommandException e)
            {
                // same as the exception handling below
                if (!e.getMessage().contains("not yet supported"))
                    throw e;
            }
            catch (final WebDriverException e)
            {
                // On SauceLabs in some cases like iPhone emulation you can't resize the browser but throw an unchecked
                // WebDriverException with the message "Not yet implemented".
                // Thus, we need to catch any WebDriverException and check its message.
                if (!e.getMessage().contains("Not yet implemented"))
                    throw e;
            }
        }
    }

    /**
     * Instantiate the {@link WebDriver} according to the configuration read from {@link TestTargets} annotations.
     *
     * @param config
     * @param proxyConfig
     * @return
     * @throws MalformedURLException
     */
    public static WebDriver createWebdriver(final BrowserConfigurationDto config, final ProxyConfigurationDto proxyConfig) throws MalformedURLException
    {
        final DesiredCapabilities capabilities = config.getCapabilities();

        final String testEnvironment = config.getTestEnvironment();

        if (StringUtils.isEmpty(testEnvironment) || "local".equalsIgnoreCase(testEnvironment))
        {
            if (proxyConfig != null)
            {
                final String proxyHost = proxyConfig.getHost() + ":" + proxyConfig.getPort();

                final Proxy webdriverProxy = new Proxy();
                webdriverProxy.setHttpProxy(proxyHost);
                webdriverProxy.setSslProxy(proxyHost);
                webdriverProxy.setFtpProxy(proxyHost);
                if (!StringUtils.isEmpty(proxyConfig.getUsername()) && !StringUtils.isEmpty(proxyConfig.getPassword()))
                {
                    webdriverProxy.setSocksUsername(proxyConfig.getUsername());
                    webdriverProxy.setSocksPassword(proxyConfig.getPassword());
                }

                capabilities.setCapability(CapabilityType.PROXY, webdriverProxy);
            }

            final String browserName = config.getCapabilities().getBrowserName();
            if (chromeBrowsers.contains(browserName))
            {
                // do we have a custom path?
                final String pathToBrowser = XltProperties.getInstance().getProperty(XltPropertyKey.CHROME_PATH);
                final ChromeOptions options = new ChromeOptions();

                // This is a workaround for a changed Selenium behavior
                // Since device emulation is not part of the "standard" it now has to be considered as experimental
                // option.
                // The capability class already sorts the different configurations in different maps (one for
                // capabilities and one for
                // experimental capabilities). The experimental options are held internal within a map of the capability
                // map and
                // are accessible with key "goog:chromeOptions" (constant ChromeOptions.CAPABILITY). So all we have to
                // do is to copy the
                // keys and values of that special map and set it as experimental option inside ChromeOptions.
                Map<String, String> experimentalOptions = null;
                try
                {
                    experimentalOptions = (Map<String, String>) capabilities.getCapability(ChromeOptions.CAPABILITY);
                    if (experimentalOptions != null)
                    {
                        for (Entry<String, String> entry : experimentalOptions.entrySet())
                        {
                            options.setExperimentalOption(entry.getKey(), entry.getValue());
                        }
                    }
                }
                catch (Exception e)
                {
                    // unsure which case this can cover since only the type conversion can fail
                    // lets throw it as unchecked exception
                    // in case that makes no sense at all then just suppress it
                    throw new RuntimeException(e);
                }

                options.merge(capabilities);
                if (StringUtils.isNotBlank(pathToBrowser))
                {
                    options.setBinary(pathToBrowser);
                }

                if (config.isClientperformanceEnabled())
                {
                    return new XltChromeDriver(options);
                }
                else
                {
                    return new ChromeDriver(options);
                }
            }
            else if (firefoxBrowsers.contains(browserName))
            {
                final FirefoxOptions options = new FirefoxOptions(capabilities);
                final String pathToBrowser = XltProperties.getInstance().getProperty(XltPropertyKey.FIREFOX_PATH);
                if (StringUtils.isNotBlank(pathToBrowser))
                {
                    options.setBinary(pathToBrowser);
                }

                if (config.isClientperformanceEnabled())
                {
                    return new XltFirefoxDriver(options);
                }
                else
                {
                    return new FirefoxDriver(options);
                }
            }
            else if (operaBrowsers.contains(browserName))
            {
                final OperaOptions options = new OperaOptions();
                options.merge(capabilities);
                final String pathToBrowser = XltProperties.getInstance().getProperty(XltPropertyKey.OPERA_PATH);
                if (StringUtils.isNotBlank(pathToBrowser))
                {
                    options.setBinary(pathToBrowser);
                }
                return new OperaDriver(options);
            }
            else if (safariBrowsers.contains(browserName))
            {
                return new SafariDriver(new SafariOptions(capabilities));
            }
            else if (internetExplorerBrowsers.contains(browserName))
            {
                return new InternetExplorerDriver(new InternetExplorerOptions(capabilities));
            }
            else if (BrowserType.EDGE.equals(browserName))
            {
                final EdgeOptions options = new EdgeOptions();
                options.merge(capabilities);

                return new EdgeDriver(options);
            }
        }
        else
        {
            final XltProperties xltProperties = XltProperties.getInstance();

            final Map<String, String> propertiesForEnvironment = xltProperties.getPropertiesForKey(XltPropertyKey.BROWSERPROFILE_TEST_ENVIRONMENT +
                                                                                                   testEnvironment);

            final String gridUsername = propertiesForEnvironment.get("username");
            final String gridPassword = propertiesForEnvironment.get("password");
            final String gridUrlString = propertiesForEnvironment.get("url");
            final URL gridUrl = new URL(gridUrlString);

            // establish connection to target website
            return new RemoteWebDriver(createGridExecutor(proxyConfig, gridUrl, gridUsername, gridPassword), capabilities);
        }

        return null;
    }

    public static Map<String, BrowserConfigurationDto> parseBrowserProperties(final XltProperties properties)
    {
        // Structur browserprofile.<nametag>.*

        // property prefix for browser configurations
        final String propertyKeyBrowsers = "browserprofile";

        // get all properties with prefix browserprofile. they are then truncated to <nametag>.*
        // holds all found browser configurations
        Map<String, String> browserProperties = properties.getPropertiesForKey(propertyKeyBrowsers);

        // a temporary list to hold all found browser tags
        final List<String> browserTags = new ArrayList<String>();

        // create a list of tags referencing browser configurations
        for (final String key : browserProperties.keySet())
        {
            final String[] parts = key.split("\\.");
            final String browserTag = parts[0];
            if (!StringUtils.isEmpty(browserTag) && !browserTags.contains(browserTag))
                browserTags.add(browserTag);
        }

        // map to hold all browser configurations. lookup via browser tag
        final Map<String, BrowserConfigurationDto> browserConfigurations = new HashMap<String, BrowserConfigurationDto>();

        // parse all browser properties and add them to the map
        final PropertiesToBrowserConfigurationMapper mapper = new PropertiesToBrowserConfigurationMapper();
        for (final String browserTag : browserTags)
        {
            browserProperties = properties.getPropertiesForKey(propertyKeyBrowsers + "." + browserTag);
            browserProperties.put("browserTag", browserTag);

            if (browserConfigurations.get(browserTag) == null)
                browserConfigurations.put(browserTag, mapper.toDto(browserProperties));
        }

        return browserConfigurations;
    }

    /**
     * Returns the effective key to be used for property lookup via one of the getProperty(...) methods.
     * <p>
     * When looking up a key, "password" for example, the following effective keys are tried, in this order:
     * <ol>
     * <li>the test user name plus simple key, e.g. "TAuthor.password"</li>
     * <li>the test class name plus simple key, e.g. "com.xceptance.xlt.samples.tests.TAuthor.password"</li>
     * <li>the simple key, e.g. "password"</li>
     * </ol>
     * 
     * @param bareKey
     *            the bare property key, i.e. without any prefixes
     * @return the first key that produces a result
     */
    private static String getEffectiveKey(final String bareKey)
    {
        final SessionImpl session = SessionImpl.getCurrent();
        if (session == null)
        {
            return bareKey;
        }

        final String effectiveKey;
        final XltProperties properties = XltProperties.getInstance();

        // 1. use the current user name as prefix
        final String userNameQualifiedKey = session.getUserName() + "." + bareKey;
        if (properties.containsKey(userNameQualifiedKey))
        {
            effectiveKey = userNameQualifiedKey;
        }
        else
        {
            // 2. use the current class name as prefix
            final String classNameQualifiedKey = session.getTestCaseClassName() + "." + bareKey;
            if (properties.containsKey(classNameQualifiedKey))
            {
                effectiveKey = classNameQualifiedKey;
            }
            else
            {
                // 3. use the bare key
                effectiveKey = bareKey;
            }
        }

        return effectiveKey;
    }

    /**
     * Returns a list of found {@link TestTargets} annotations for the first annotated class in class hierarchy starting
     * with the given class.
     * 
     * @param clazz
     *            the class to start inspection at
     * @return list of found {@link TestTargets} annotations for the 1st found class that has such an annotation
     */
    public static List<TestTargets> getTestTargets(Class<?> clazz)
    {
        final ArrayList<TestTargets> foundAnnotations = new ArrayList<>();

        while (clazz != null && foundAnnotations.isEmpty())
        {
            Annotation[] annos = clazz.getDeclaredAnnotations();
            if (annos != null && annos.length > 0)
            {
                for (final Annotation anno : annos)
                {
                    if (anno instanceof TestTargets)
                    {
                        foundAnnotations.add((TestTargets) anno);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return foundAnnotations;
    }

}
