package kr.co.marketry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableRetry
@EnableCaching
@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketryApplication.class, args);
    }
}
