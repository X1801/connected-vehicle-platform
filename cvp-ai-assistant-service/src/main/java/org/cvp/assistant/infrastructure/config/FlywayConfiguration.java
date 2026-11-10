package org.cvp.assistant.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("persistent")
public class FlywayConfiguration {
    @Bean(initMethod = "migrate")
    Flyway assistantFlyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    }

    @Bean
    static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
        return factory -> factory.getBeanDefinition("entityManagerFactory").setDependsOn("assistantFlyway");
    }
}
