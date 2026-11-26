package com.landfall.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI landfallOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Landfall API").version("v1").description("Landfall（见屿）多媒体社区后端接口契约"));
    }
}
