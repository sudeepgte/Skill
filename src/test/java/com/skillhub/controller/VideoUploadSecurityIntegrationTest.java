package com.skillhub.controller;

import com.skillhub.model.User;
import com.skillhub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:videouploadtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@DisplayName("Video Upload Security Integration Test (BUG-018)")
public class VideoUploadSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean(name = "initData")
    private CommandLineRunner initData;

    private User student;
    private User instructor;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();

        student = new User();
        student.setEmail("neha@123");
        student.setName("Neha Student");
        student.setPassword("neha@123");
        student.setRole("STUDENT");
        userRepository.save(student);

        instructor = new User();
        instructor.setEmail("instructor@skillhub.com");
        instructor.setName("Instructor Tester");
        instructor.setPassword("password");
        instructor.setRole("INSTRUCTOR");
        userRepository.save(instructor);
    }

    @Test
    @DisplayName("BUG-018: Regular Student uploading non-video file (test.txt) MUST be forbidden (HTTP 403)")
    @WithMockUser(username = "neha@123", roles = "STUDENT")
    public void testStudentUploadingNonVideoFileIsForbidden() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Sample malicious payload".getBytes()
        );

        mockMvc.perform(multipart("/api/upload-video")
                .file(textFile)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BUG-018: Instructor uploading arbitrary non-video file (script.html) MUST be rejected with HTTP 400 Bad Request")
    @WithMockUser(username = "instructor@skillhub.com", roles = "INSTRUCTOR")
    public void testInstructorUploadingHtmlFileIsRejected() throws Exception {
        MockMultipartFile htmlFile = new MockMultipartFile(
                "file", "script.html", "text/html", "<script>alert('xss')</script>".getBytes()
        );

        mockMvc.perform(multipart("/api/upload-video")
                .file(htmlFile)
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("BUG-018: Instructor uploading verified video file (.mp4) succeeds with HTTP 200")
    @WithMockUser(username = "instructor@skillhub.com", roles = "INSTRUCTOR")
    public void testInstructorUploadingValidMp4FileSucceeds() throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "file", "intro_lecture.mp4", "video/mp4", new byte[]{0x00, 0x00, 0x00, 0x18}
        );

        mockMvc.perform(multipart("/api/upload-video")
                .file(videoFile)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").exists());
    }

    @Test
    @DisplayName("BUG-018: Unauthenticated upload request MUST be rejected (HTTP 401 or 403)")
    public void testUnauthenticatedUploadIsRejected() throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "file", "lecture.mp4", "video/mp4", new byte[]{0x00, 0x00}
        );

        mockMvc.perform(multipart("/api/upload-video")
                .file(videoFile)
                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
