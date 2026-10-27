package com.skillhub.security;

import com.skillhub.model.Course;
import com.skillhub.model.CourseResource;
import com.skillhub.model.CourseStatus;
import com.skillhub.model.Enrollment;
import com.skillhub.model.User;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.CourseResourceRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FileUploadSecurityAndDataProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseResourceRepository courseResourceRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User adminUser;
    private User instructorA;
    private User instructorB;
    private User studentUser;
    private Course testCourse;
    private CourseResource testResource;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = new User();
        adminUser.setName("Security Admin");
        adminUser.setEmail("admin_" + suffix + "@skillhub.com");
        adminUser.setPassword("Password123!");
        adminUser.setRole("ADMIN");
        adminUser.setApproved(true);
        adminUser = userRepository.save(adminUser);

        instructorA = new User();
        instructorA.setName("Instructor A");
        instructorA.setEmail("instructorA_" + suffix + "@skillhub.com");
        instructorA.setPassword("Password123!");
        instructorA.setRole("INSTRUCTOR");
        instructorA.setApproved(true);

        // Store private document for Instructor A with valid PDF magic bytes
        byte[] validPdf = "%PDF-1.4 Mock Aadhaar Content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile aadharFile = new MockMultipartFile("file", "aadhar.pdf", "application/pdf", validPdf);
        String aadharPath = fileStorageService.storePrivateDocument(aadharFile, "aadhar");
        instructorA.setAadharCardUrl(aadharPath);
        instructorA = userRepository.save(instructorA);

        instructorB = new User();
        instructorB.setName("Instructor B");
        instructorB.setEmail("instructorB_" + suffix + "@skillhub.com");
        instructorB.setPassword("Password123!");
        instructorB.setRole("INSTRUCTOR");
        instructorB.setApproved(true);
        instructorB = userRepository.save(instructorB);

        studentUser = new User();
        studentUser.setName("Student Learner");
        studentUser.setEmail("student_" + suffix + "@skillhub.com");
        studentUser.setPassword("Password123!");
        studentUser.setRole("STUDENT");
        studentUser.setApproved(true);
        studentUser = userRepository.save(studentUser);

        testCourse = new Course();
        testCourse.setTitle("Protected Course " + suffix);
        testCourse.setPrice(999.0);
        testCourse.setInstructor(instructorA);
        testCourse.setPublished(true);
        testCourse.setStatus(CourseStatus.PUBLISHED);
        testCourse = courseRepository.save(testCourse);

        // Upload and create a course resource file
        byte[] resourceBytes = "%PDF-1.4 Protected Lecture Notes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile resFile = new MockMultipartFile("file", "lecture_notes.pdf", "application/pdf", resourceBytes);
        String resPath = fileStorageService.store(resFile);

        testResource = new CourseResource("Lecture Notes", "Detailed notes", resPath, "PDF", (long) resourceBytes.length, testCourse);
        testResource = courseResourceRepository.save(testResource);
    }

    @Test
    @DisplayName("Verify rejection of prohibited executable or script file uploads")
    void testProhibitedExecutableUploadRejection() {
        byte[] payload = "MZ... Mock Executable".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile exeFile = new MockMultipartFile("file", "malicious.exe", "application/octet-stream", payload);

        assertThrows(SecurityException.class, () -> {
            fileStorageService.store(exeFile);
        }, "Uploading .exe files must be rejected with SecurityException");

        MockMultipartFile jspFile = new MockMultipartFile("file", "webshell.jsp", "text/plain", "<% Runtime.getRuntime(); %>".getBytes(StandardCharsets.UTF_8));
        assertThrows(SecurityException.class, () -> {
            fileStorageService.store(jspFile);
        }, "Uploading .jsp files must be rejected with SecurityException");

        MockMultipartFile svgFile = new MockMultipartFile("file", "image.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));
        assertThrows(SecurityException.class, () -> {
            fileStorageService.store(svgFile);
        }, "Uploading .svg files must be rejected to prevent stored XSS");
    }

    @Test
    @DisplayName("Verify rejection of files with invalid signature/magic bytes")
    void testMismatchedFileSignatureRejection() {
        // Filename is .pdf but content does not start with %PDF-
        byte[] fakePdfBytes = "THIS IS NOT A VALID PDF FILE".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakePdf = new MockMultipartFile("file", "spoofed.pdf", "application/pdf", fakePdfBytes);

        assertThrows(SecurityException.class, () -> {
            fileStorageService.storePrivateDocument(fakePdf, "aadhar");
        }, "File with mismatched header must be rejected by magic byte validator");
    }

    @Test
    @DisplayName("Verify rejection of oversized files exceeding category limits")
    void testOversizedFileUploadRejection() {
        // Create an oversized 11MB file (limit is 10MB)
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        largeBytes[0] = '%'; largeBytes[1] = 'P'; largeBytes[2] = 'D'; largeBytes[3] = 'F';
        MockMultipartFile oversizedPdf = new MockMultipartFile("file", "giant.pdf", "application/pdf", largeBytes);

        assertThrows(IllegalArgumentException.class, () -> {
            fileStorageService.storePrivateDocument(oversizedPdf, "aadhar");
        }, "Files exceeding maximum size limit must be rejected");
    }

    @Test
    @DisplayName("Verify path traversal attempts in filename are neutralized")
    void testPathTraversalNeutralization() {
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        MockMultipartFile traversalFile = new MockMultipartFile("file", "../../etc/passwd.png", "image/png", validPng);

        String storedPath = fileStorageService.store(traversalFile);
        assertNotNull(storedPath);
        assertFalse(storedPath.contains(".."), "Stored path must never retain directory traversal sequences");
        assertTrue(storedPath.startsWith("/uploads/"), "Stored path must be safely confined to upload root");
    }

    @Test
    @DisplayName("Verify unauthenticated access to private document is blocked (401)")
    void testUnauthenticatedAccessToPrivateDocumentBlocked() throws Exception {
        mockMvc.perform(get("/api/documents/user/" + instructorA.getId() + "/aadhar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify horizontal privilege escalation: Instructor B cannot access Instructor A's private document (403)")
    void testHorizontalPrivilegeEscalationBlocked() throws Exception {
        mockMvc.perform(get("/api/documents/user/" + instructorA.getId() + "/aadhar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(instructorB.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verify Instructor A can access their own private document (200)")
    void testOwnerAccessToPrivateDocumentAllowed() throws Exception {
        mockMvc.perform(get("/api/documents/user/" + instructorA.getId() + "/aadhar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(instructorA.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
    }

    @Test
    @DisplayName("Verify Admin can access any Instructor's private verification document (200)")
    void testAdminAccessToPrivateDocumentAllowed() throws Exception {
        mockMvc.perform(get("/api/documents/user/" + instructorA.getId() + "/aadhar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(adminUser.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Verify direct URL access to private documents directory by unauthorized user returns 403 or 401")
    void testDirectPrivateUrlAccessControlled() throws Exception {
        mockMvc.perform(get(instructorA.getAadharCardUrl())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(instructorB.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verify unenrolled student cannot download paid course resource (403)")
    void testUnenrolledStudentDeniedCourseResourceDownload() throws Exception {
        mockMvc.perform(get("/api/courses/" + testCourse.getId() + "/resources/" + testResource.getId() + "/download")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verify enrolled student can download course resource (200)")
    void testEnrolledStudentAllowedCourseResourceDownload() throws Exception {
        Enrollment enrollment = new Enrollment(studentUser, testCourse);
        enrollmentRepository.save(enrollment);

        mockMvc.perform(get("/api/courses/" + testCourse.getId() + "/resources/" + testResource.getId() + "/download")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(studentUser.getEmail()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
