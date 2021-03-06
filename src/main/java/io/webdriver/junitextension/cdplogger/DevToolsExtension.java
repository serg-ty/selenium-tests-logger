package io.webdriver.junitextension.cdplogger;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v96.log.Log;
import org.openqa.selenium.devtools.v96.log.model.LogEntry;
import org.openqa.selenium.devtools.v96.network.Network;
import org.openqa.selenium.devtools.v96.network.model.Request;
import org.openqa.selenium.devtools.v96.network.model.ResourceType;
import org.openqa.selenium.devtools.v96.network.model.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * JUnit Jupiter class extension to work with Dev Tools Protocol logging.
 * Its purpose is to log Network activity (sent requests and received responses) and other activity in Log (either
 * network or JavaScript nature).
 *
 * <p>This class can be registered by {@code @ExtendWith} annotation on class or method level.
 *
 * <p>Each test class extensible with this class must have a {@code 'driver'} field of type {@code ChromiumDriver}
 * to be accessible via Java reflection call.
 * This field must be instantiated in {@code @BeforeEach} method in test class with either {@code ChromeDriver} or
 * {@code EdgeDriver} types (only these drivers support Dev Tools Protocol).
 *
 * <p>By default received Network responses may come from any source (e.g. Google Apis and others) and are not
 * restricted by tested application calls only.
 * To filter responses by some URL match pattern there is need to add a {@code 'responseURLFilter'}
 * field in a test class. This can be done with static field, e.g.
 * {@code final static String responseURLFilter = "your.application.url.part";}.
 */
public class DevToolsExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    protected final static Logger logger = LoggerFactory.getLogger(DevToolsExtension.class);
    protected static final String ANSI_GREEN = "\u001B[32m";
    protected static final String ANSI_RESET = "\u001B[0m";
    protected DevTools devTools;
    protected String testMethodName;
    protected String responseURLFilter;

    protected DevToolsExtension() {

    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        TestInstances testInstances = context.getRequiredTestInstances();
        this.testMethodName = context.getRequiredTestMethod().getName();
        try {
            Field driverField = testInstances.getInnermostInstance().getClass().getDeclaredField("driver");
            driverField.setAccessible(true);

            if (driverField.getType().isAssignableFrom(ChromiumDriver.class)) {
                ChromiumDriver driver = (ChromiumDriver) driverField.get(testInstances.getInnermostInstance());
                this.devTools = driver.getDevTools();

            } else {
                throw new IllegalArgumentException("Unsupported type of WebDriver is provided. "
                        + "\nChrome Dev Tools supports either ChromeDriver or EdgeDriver.");
            }
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldException(String.format("There is no 'driver' field in test class '%s'. "
                            + "\nThis field is required to work with Dev Tools extension class.",
                    testInstances.getInnermostInstance().getClass().getName()));
        }

        try {
            Field responseURLFilterField = testInstances.getInnermostInstance()
                    .getClass()
                    .getDeclaredField("responseURLFilter");
            responseURLFilterField.setAccessible(true);
            this.responseURLFilter = (String) responseURLFilterField.get(testInstances.getInnermostInstance());

        } catch (NoSuchFieldException e) {
            //no-op
        }

        devTools.createSession();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        registerNetworkRequestListener();
        registerNetworkResponseListener(Optional.ofNullable(responseURLFilter));
        devTools.send(Log.enable());
        registerLogListener();
        logJavaScriptExceptions();

    }

    protected void logJavaScriptExceptions() {

        devTools.getDomains().events().addJavascriptExceptionListener(e ->
        {
            logger.error(ANSI_GREEN + "[{}] : Java script exception occurred : {}" + ANSI_RESET,
                    testMethodName, e.getMessage());
            e.printStackTrace();
        });
    }

    protected void registerNetworkRequestListener() {
        devTools.addListener(Network.requestWillBeSent(),
                entry ->
                {
                    Request request = entry.getRequest();
                    if (entry.getType().equals(Optional.of(ResourceType.FETCH))) {
                        if (request.getPostData().isPresent()) {
                            logger.info("[{}] : [{}] Request with URL : {} : With body : {}",
                                    testMethodName,
                                    request.getMethod(),
                                    request.getUrl(),
                                    request.getPostData().get());
                        } else {
                            logger.info("[{}] : [{}] Request with URL : {}",
                                    testMethodName,
                                    request.getMethod(),
                                    request.getUrl());
                        }
                    }
                });
    }

    protected void registerNetworkResponseListener(Optional<String> responseURLFilter) {
        devTools.addListener(Network.responseReceived(),
                entry ->
                {
                    Response response = entry.getResponse();
                    if (entry.getType().equals(ResourceType.FETCH)
                            || entry.getType().equals(ResourceType.XHR)) {
                        if (response.getStatus() >= 400) {
                            logResponse(response, responseURLFilter,
                                    log -> logger.error(ANSI_GREEN + "[{}] : Response with URL : {} : With status code : {}" + ANSI_RESET,
                                            testMethodName,
                                            response.getUrl(),
                                            response.getStatus()));
                        } else {
                            logResponse(response, responseURLFilter,
                                    log -> logger.info("[{}] : Response with URL : {} : With status code : {}",
                                            testMethodName,
                                            response.getUrl(),
                                            response.getStatus()));
                        }
                    }
                });
    }

    private void logResponse(Response resp, Optional<String> responseURLFilter, Consumer<Response> handler) {
        if (responseURLFilter.isPresent()) {
            if (resp.getUrl().contains(responseURLFilter.get())) {
                handler.accept(resp);
            }
        } else {
            handler.accept(resp);
        }
    }

    protected void registerLogListener() {
        devTools.addListener(Log.entryAdded(),
                entry ->
                {
                    if (entry.getLevel().equals(LogEntry.Level.ERROR)) {

                        logger.error(ANSI_GREEN + "[{} : LOG.ERROR] Entry added with text: {}" + ANSI_RESET,
                                testMethodName,
                                entry.getText());
                        if (entry.getStackTrace().isPresent()) {
                            logger.error(ANSI_GREEN + "[{} : LOG.ERROR]\tWith stack trace : {}" + ANSI_RESET,
                                    testMethodName,
                                    entry.getStackTrace().get());
                        }
                    }
                });
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        if (devTools != null) {
            devTools.clearListeners();
            devTools.close();
        }
    }
}
