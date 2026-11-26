package com.landfall.common.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.plugin.Interceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.landfall", annotationClass = Mapper.class)
public class PersistenceConfig {

    @Bean
    Interceptor uuidV7IdAssignInterceptor() {
        return new UuidV7IdAssignInterceptor();
    }
}
