package io.selenium.logger.example;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.webdriver.junitextension.cdplogger.DevToolsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;

import java.time.Duration;

@ExtendWith(DevToolsExtension.class)
class ChromeDevToolsTest {

    //final static String responseURLFilter = "your.url.part.filter";
    private ChromiumDriver driver;

    @BeforeAll
    static void initDriver() {
        WebDriverManager.chromedriver().setup();

    }

    @BeforeEach
    void setUp() {
        //noinspection deprecation
        driver = new ChromeDriver(new ChromeOptions()
                .setAcceptInsecureCerts(true)
                .setHeadless(false));
    }

    @AfterEach
    void tearDown() {
        driver.quit();
    }

    @Test
    void test1() throws InterruptedException {

        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
        driver.get("https://duckduckgo.com");
        WebElement searchEdit = driver.findElement(By.name("q"));
        searchEdit.sendKeys("Selenium 4");
        searchEdit.submit();
        driver.findElement(By.partialLinkText("Selenium 4")).click();

        Thread.sleep(6000);

    }

}