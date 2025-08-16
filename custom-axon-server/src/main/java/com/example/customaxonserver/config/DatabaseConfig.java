package com.example.customaxonserver.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database configuration for Custom Axon Server.
 * Configures connection pooling, transaction management, and JPA settings.
 */
@Configuration
@EnableTransactionManagement
@Profile("!test")
public class DatabaseConfig {

    /**
     * Primary DataSource with HikariCP connection pooling.
     * Optimized for high-performance event store operations.
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        return new HikariDataSource();
    }

    /**
     * EntityManagerFactory configuration for JPA.
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.example.customaxonserver.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "validate");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.jdbc.batch_size", "25");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");
        
        em.setJpaProperties(properties);
        
        return em;
    }

    /**
     * Transaction manager for JPA operations.
     * Configured for distributed transaction support.
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        
        // Configure for distributed transactions
        transactionManager.setDefaultTimeout(30); // 30 seconds timeout
        transactionManager.setRollbackOnCommitFailure(true);
        transactionManager.setValidateExistingTransaction(true);
        transactionManager.setGlobalRollbackOnParticipationFailure(true);
        
        return transactionManager;
    }

    /**
     * Transaction template for programmatic transaction management.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setTimeout(30);
        template.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        return template;
    }
}