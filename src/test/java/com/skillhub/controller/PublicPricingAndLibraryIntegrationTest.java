package com.skillhub.controller;

import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.VideoResourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:publicpricingdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Unauthenticated Public Access to Pricing and Library Integration Test (BUG-013)")
public class PublicPricingAndLibraryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VideoResourceRepository videoResourceRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    @Test
    @DisplayName("BUG-013: Anonymous visitor can view /pricing without logging in (200 OK)")
    public void testAnonymousVisitorCanAccessPricing() throws Exception {
        mockMvc.perform(get("/pricing"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/pricing"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    @DisplayName("BUG-013: Anonymous visitor can view /library without logging in (200 OK)")
    public void testAnonymousVisitorCanAccessVideoLibrary() throws Exception {
        mockMvc.perform(get("/library"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/video_library"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attributeExists("videos"));
    }
}
