package com.skillhub.controller;

import com.skillhub.model.Category;
import com.skillhub.model.Course;
import com.skillhub.model.ExamQuestion;
import com.skillhub.model.User;
import com.skillhub.model.enums.CourseQuestionType;
import com.skillhub.repository.CategoryRepository;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.skillhub.repository.ExamQuestionRepository;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:question_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@Transactional
@DisplayName("Course Question Management - Multiple Question Types Integration Tests")
public class CourseQuestionManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ExamQuestionRepository examQuestionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ExamService examService;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "initData")
    private org.springframework.boot.CommandLineRunner initData;

    private Course testCourse;
    private User adminUser;
    private User studentUser;

    @BeforeEach
    public void setup() {
        Category category = categoryRepository.findByName("Development");
        if (category == null) {
            category = new Category("Development", "fas fa-code");
            category = categoryRepository.save(category);
        }

        adminUser = userRepository.findByEmail("admin_qtest@skillhub.com");
        if (adminUser == null) {
            adminUser = new User("Admin", "admin_qtest@skillhub.com", "password", "ADMIN");
            adminUser = userRepository.save(adminUser);
        }

        studentUser = userRepository.findByEmail("student_qtest@skillhub.com");
        if (studentUser == null) {
            studentUser = new User("Student", "student_qtest@skillhub.com", "password", "STUDENT");
            studentUser = userRepository.save(studentUser);
        }

        testCourse = new Course();
        testCourse.setTitle("Enterprise Java Masterclass " + System.currentTimeMillis());
        testCourse.setDescription("Learn modern Java & Spring Boot");
        testCourse.setCategory(category);
        testCourse.setInstructor(adminUser);
        testCourse.setPrice(49.99);
        testCourse = courseRepository.save(testCourse);
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("1. Admin can view course question management page with empty or existing questions")
    public void testViewCourseQuestionsPage() throws Exception {
        mockMvc.perform(get("/admin/courses/" + testCourse.getId() + "/questions"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/manage_questions"))
                .andExpect(model().attributeExists("course"))
                .andExpect(model().attributeExists("questions"))
                .andExpect(model().attributeExists("questionTypes"))
                .andExpect(model().attribute("questionTypes", CourseQuestionType.values()));
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("2. Backward compatibility: Create legacy Multiple Choice question with optionA..D")
    public void testCreateLegacyMultipleChoiceQuestion() throws Exception {
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "MULTIPLE_CHOICE")
                .param("questionText", "What is the return type of a Java constructor?")
                .param("marks", "2")
                .param("optionA", "void")
                .param("optionB", "int")
                .param("optionC", "No return type")
                .param("optionD", "Object")
                .param("correctOption", "C")
                .param("explanation", "Constructors do not have a return type in Java."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses/" + testCourse.getId() + "/questions"));

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        assertEquals(1, updated.getExamQuestions().size());
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.MULTIPLE_CHOICE, q.getQuestionType());
        assertEquals("What is the return type of a Java constructor?", q.getQuestionText());
        assertEquals("No return type", q.getOptionC());
        assertEquals("C", q.getCorrectAnswer());
        assertEquals(2, q.getMarks());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("3. Create Multiple Select question with multiple correct answers")
    public void testCreateMultipleSelectQuestion() throws Exception {
        String typeData = "{\"options\":[\"List\",\"Set\",\"Map\",\"Queue\"],\"correctIndices\":[0,1,3]}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "MULTIPLE_SELECT")
                .param("questionText", "Which interfaces inherit from java.util.Collection?")
                .param("marks", "3")
                .param("typeData", typeData)
                .param("correctAnswer", "0,1,3"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.MULTIPLE_SELECT, q.getQuestionType());
        assertEquals("0,1,3", q.getCorrectAnswer());
        assertEquals(3, q.getMarks());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("4. Create True / False question")
    public void testCreateTrueFalseQuestion() throws Exception {
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "TRUE_FALSE")
                .param("questionText", "Java is a statically typed language.")
                .param("marks", "1")
                .param("correctAnswer", "True"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.TRUE_FALSE, q.getQuestionType());
        assertEquals("True", q.getCorrectAnswer());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("5. Create Short Answer question with case-sensitive flag")
    public void testCreateShortAnswerQuestion() throws Exception {
        String typeData = "{\"expected\":\"Polymorphism\",\"alternatives\":\"Dynamic dispatch, Subtyping\"}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "SHORT_ANSWER")
                .param("questionText", "What OOP principle enables one interface to have multiple implementations?")
                .param("marks", "2")
                .param("typeData", typeData)
                .param("correctAnswer", "Polymorphism")
                .param("caseSensitive", "false"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.SHORT_ANSWER, q.getQuestionType());
        assertFalse(q.getCaseSensitive());
        assertEquals("Polymorphism", q.getCorrectAnswer());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("6. Create Long Answer / Essay question with manual grading flag")
    public void testCreateEssayQuestion() throws Exception {
        String typeData = "{\"instructions\":\"Detail 3 reasons for immutability.\",\"rubric\":\"Look for thread safety, caching, security.\"}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "ESSAY")
                .param("questionText", "Explain why the String class is designed to be immutable in Java.")
                .param("marks", "5")
                .param("typeData", typeData)
                .param("manualGrading", "true"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.ESSAY, q.getQuestionType());
        assertTrue(q.getManualGrading());
        assertEquals(5, q.getMarks());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("7. Create Fill in the Blanks question with multiple blanks")
    public void testCreateFillBlanksQuestion() throws Exception {
        String typeData = "{\"blanks\":[\"WORA\",\"JVM\"]}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "FILL_BLANKS")
                .param("questionText", "Java's philosophy is ____, enabled by bytecode running on the ____.")
                .param("marks", "2")
                .param("typeData", typeData)
                .param("correctAnswer", "WORA, JVM"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.FILL_BLANKS, q.getQuestionType());
        assertTrue(q.getTypeData().contains("JVM"));
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("8. Create Matching question with premise-target pairs")
    public void testCreateMatchingQuestion() throws Exception {
        String typeData = "{\"pairs\":[{\"left\":\"@Service\",\"right\":\"Business Layer\"},{\"left\":\"@Repository\",\"right\":\"Data Access\"}]}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "MATCHING")
                .param("questionText", "Match each Spring stereotyping annotation with its respective layer.")
                .param("marks", "2")
                .param("typeData", typeData))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.MATCHING, q.getQuestionType());
        assertTrue(q.getTypeData().contains("@Service"));
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("9. Create Ordering and Numerical questions")
    public void testCreateOrderingAndNumericalQuestions() throws Exception {
        // Ordering
        String orderData = "{\"items\":[\"Compilation to Bytecode\",\"Class Loading\",\"Bytecode Verification\",\"JIT Execution\"]}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "ORDERING")
                .param("questionText", "Order the JVM execution lifecycle steps in chronological sequence.")
                .param("marks", "4")
                .param("typeData", orderData))
                .andExpect(status().is3xxRedirection());

        // Numerical
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "NUMERICAL")
                .param("questionText", "What is the time complexity coefficient in milliseconds for 1000 items with O(log n)?")
                .param("marks", "2")
                .param("correctAnswer", "9.96")
                .param("tolerance", "0.05"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        assertEquals(2, updated.getExamQuestions().size());
        assertEquals(CourseQuestionType.ORDERING, updated.getExamQuestions().get(0).getQuestionType());
        assertEquals(CourseQuestionType.NUMERICAL, updated.getExamQuestions().get(1).getQuestionType());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("10. Create Code Writing question with language and starter template")
    public void testCreateCodeWritingQuestion() throws Exception {
        String codeData = "{\"starterTemplate\":\"public int sum(int a, int b) {\\n    // write code\\n}\",\"requiredKeywords\":\"return,+\"}";
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/add")
                .with(csrf())
                .param("questionType", "CODE")
                .param("codeLanguage", "Java")
                .param("questionText", "Write a Java method to sum two integers.")
                .param("marks", "5")
                .param("typeData", codeData))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion q = updated.getExamQuestions().get(0);
        assertEquals(CourseQuestionType.CODE, q.getQuestionType());
        assertEquals("Java", q.getCodeLanguage());
        assertTrue(q.getTypeData().contains("sum"));
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("11. Retrieve Question JSON for edit pre-population and update question")
    public void testGetQuestionJsonAndUpdate() throws Exception {
        ExamQuestion q = new ExamQuestion();
        q.setCourse(testCourse);
        q.setQuestionType(CourseQuestionType.SHORT_ANSWER);
        q.setQuestionText("Initial question text");
        q.setMarks(1);
        q.setCorrectAnswer("Answer1");
        testCourse.getExamQuestions().add(q);
        courseRepository.save(testCourse);

        Long qid = testCourse.getExamQuestions().get(0).getId();
        assertNotNull(qid);

        // Fetch JSON
        mockMvc.perform(get("/admin/courses/" + testCourse.getId() + "/questions/" + qid + "/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(qid))
                .andExpect(jsonPath("$.questionType").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.questionText").value("Initial question text"));

        // Update Question
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/update/" + qid)
                .with(csrf())
                .param("questionType", "SHORT_ANSWER")
                .param("questionText", "Updated question text")
                .param("marks", "3")
                .param("correctAnswer", "UpdatedAnswer"))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        ExamQuestion updatedQ = updated.getExamQuestions().stream().filter(item -> item.getId().equals(qid)).findFirst().orElseThrow();
        assertEquals("Updated question text", updatedQ.getQuestionText());
        assertEquals(3, updatedQ.getMarks());
        assertEquals("UpdatedAnswer", updatedQ.getCorrectAnswer());
    }

    @Test
    @WithMockUser(username = "admin_qtest@skillhub.com", authorities = {"ADMIN", "ROLE_ADMIN"})
    @DisplayName("12. Delete Question and Question Type filtering")
    public void testDeleteAndFilterQuestions() throws Exception {
        ExamQuestion q1 = new ExamQuestion(testCourse, "MCQ Question", "A", "B", "C", "D", "A");
        ExamQuestion q2 = new ExamQuestion();
        q2.setCourse(testCourse);
        q2.setQuestionType(CourseQuestionType.TRUE_FALSE);
        q2.setQuestionText("TF Question");
        q2.setCorrectAnswer("True");

        testCourse.getExamQuestions().addAll(List.of(q1, q2));
        courseRepository.save(testCourse);

        // Filter by TRUE_FALSE
        mockMvc.perform(get("/admin/courses/" + testCourse.getId() + "/questions?typeFilter=TRUE_FALSE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("questions", hasSize(1)));

        // Delete q1
        Long q1Id = testCourse.getExamQuestions().get(0).getId();
        mockMvc.perform(post("/admin/courses/" + testCourse.getId() + "/questions/delete/" + q1Id)
                .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Course updated = courseRepository.findById(testCourse.getId()).orElseThrow();
        assertEquals(1, updated.getExamQuestions().size());
        assertEquals(CourseQuestionType.TRUE_FALSE, updated.getExamQuestions().get(0).getQuestionType());
    }

    @Test
    @DisplayName("13. Student Assessment: Multi-type evaluation & grading works accurately")
    public void testStudentMultiTypeGrading() {
        ExamQuestion qMcq = new ExamQuestion(testCourse, "MCQ Test", "A", "B", "C", "D", "B");
        qMcq.setMarks(2);

        ExamQuestion qTf = new ExamQuestion();
        qTf.setCourse(testCourse);
        qTf.setQuestionType(CourseQuestionType.TRUE_FALSE);
        qTf.setQuestionText("Java is OOP");
        qTf.setCorrectAnswer("True");
        qTf.setMarks(2);

        ExamQuestion qNum = new ExamQuestion();
        qNum.setCourse(testCourse);
        qNum.setQuestionType(CourseQuestionType.NUMERICAL);
        qNum.setQuestionText("2 + 2 = ?");
        qNum.setCorrectAnswer("4.0");
        qNum.setTolerance(0.1);
        qNum.setMarks(2);

        testCourse.getExamQuestions().addAll(List.of(qMcq, qTf, qNum));
        testCourse = courseRepository.save(testCourse);

        // Student generates exam
        List<Map<String, Object>> questions = examService.generateQuestions(testCourse, studentUser);
        assertNotNull(questions);
        assertEquals(3, questions.size());

        // Verify correct answers are NOT leaked in student payload
        for (Map<String, Object> q : questions) {
            assertNull(q.get("correctAnswer"), "Security requirement: correctAnswer must never be exposed to students");
        }

        // Student submits answers: answer q_1 correctly (b), q_2 correctly (True), q_3 correctly (4.0)
        Map<String, String> answers = new HashMap<>();
        answers.put("q_1", "b");
        answers.put("q_2", "True");
        answers.put("q_3", "4.0");

        var attempt = examService.gradeExam(studentUser, testCourse, answers);
        assertNotNull(attempt);
        assertTrue(attempt.getScore() > 0, "Score should be calculated");
        assertNotNull(attempt.getSubmissionDetails());
        assertTrue(attempt.getSubmissionDetails().contains("isCorrect"));
    }
}
