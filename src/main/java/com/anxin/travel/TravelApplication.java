package com.anxin.travel;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import javax.annotation.PostConstruct;
import java.util.Iterator;

@Slf4j
@SpringBootApplication
@MapperScan("com.anxin.travel.module.*.mapper")
public class TravelApplication {

    private final ConfigurableEnvironment environment;

    public TravelApplication(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(TravelApplication.class, args);
    }

    @PostConstruct
    public void init() {
        log.info("============= DataSource Password Debug =============");
        String password = environment.getProperty("spring.datasource.password");
        log.info("Final password value: {}", password);

        // 检查命令行参数
        String cmdPassword = System.getProperty("spring.datasource.password");
        log.info("Command line password (-Dspring.datasource.password): {}", cmdPassword);

        // 检查所有可能的密码相关属性
        log.info("\n===== All Datasource Properties =====");
        log.info("spring.datasource.url: {}", environment.getProperty("spring.datasource.url"));
        log.info("spring.datasource.username: {}", environment.getProperty("spring.datasource.username"));
        log.info("spring.datasource.driver-class-name: {}", environment.getProperty("spring.datasource.driver-class-name"));

        // 遍历所有属性源，查找 password 来自哪里
        MutablePropertySources propertySources = environment.getPropertySources();
        Iterator<PropertySource<?>> iterator = propertySources.iterator();

        int index = 0;
        while (iterator.hasNext()) {
            PropertySource<?> source = iterator.next();
            Object value = source.getProperty("spring.datasource.password");
            if (value != null) {
                log.info("Found in source [{}]: {}", index, source.getName());
                log.info("  Value: {}", value);
                log.info("  Source type: {}", source.getClass().getName());
            }
            index++;
        }

        log.info("\n===== Environment Variables =====");
        log.info("SPRING_DATASOURCE_PASSWORD env: {}", System.getenv("SPRING_DATASOURCE_PASSWORD"));
        log.info("spring.datasource.password system property: {}", System.getProperty("spring.datasource.password"));

        // 打印 application.yml 的原始内容
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("application.yml");
            if (is != null) {
                java.util.Scanner scanner = new java.util.Scanner(is);
                log.info("\n===== application.yml content =====");
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("password")) {
                        log.info(line);
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            log.error("Error reading application.yml: {}", e.getMessage());
        }

        log.info("=====================================================");
    }
}