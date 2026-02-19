package com.blog.migration.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据源配置
 * 配置源数据库和目标数据库
 */
@Configuration
public class DataSourceConfig {

    /**
     * 目标数据库（新的微服务数据库）
     */
    @Primary
    @Bean(name = "targetDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource targetDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 源数据库（ASP.NET Core 原数据库）
     */
    @Bean(name = "sourceDataSource")
    public DataSource sourceDataSource(MigrationProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getSource().getUrl());
        dataSource.setUsername(properties.getSource().getUsername());
        dataSource.setPassword(properties.getSource().getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setPoolName("SourceHikariCP");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        return dataSource;
    }

    /**
     * 目标数据库 JdbcTemplate
     */
    @Primary
    @Bean(name = "targetJdbcTemplate")
    public JdbcTemplate targetJdbcTemplate(@Qualifier("targetDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 源数据库 JdbcTemplate
     */
    @Bean(name = "sourceJdbcTemplate")
    public JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
