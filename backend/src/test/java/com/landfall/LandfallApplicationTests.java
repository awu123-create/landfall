package com.landfall;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LandfallApplicationTests {

    private final MockMvc mockMvc;

    @Autowired
    LandfallApplicationTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void openApiDocs_exposesLandfallContractMetadata() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Landfall API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.info.description").value("Landfall（见屿）多媒体社区后端接口契约"))
                .andExpect(jsonPath("$.paths['/api/v1/public/health'].get").exists());
    }
}
