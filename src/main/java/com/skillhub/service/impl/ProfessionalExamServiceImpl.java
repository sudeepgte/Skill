package com.skillhub.service.impl;

import com.skillhub.model.enums.ExamDifficulty;
import com.skillhub.model.enums.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Map;


import com.skillhub.exception.BusinessException;
import com.skillhub.exception.ResourceNotFoundException;
import com.skillhub.model.AdminLog;
import com.skillhub.model.ProfessionalExam;
import com.skillhub.model.ProfessionalExamQuestion;
import com.skillhub.model.ProfessionalExamSection;
import com.skillhub.model.ProfessionalExamVersion;
import com.skillhub.model.User;
import com.skillhub.repository.AdminLogRepository;
import com.skillhub.repository.ProfessionalExamQuestionRepository;
import com.skillhub.repository.ProfessionalExamRepository;
import com.skillhub.repository.ProfessionalExamSectionRepository;
import com.skillhub.repository.ProfessionalExamVersionRepository;
import com.skillhub.service.ProfessionalExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProfessionalExamServiceImpl implements ProfessionalExamService {

    @Autowired private ProfessionalExamRepository examRepository;
    @Autowired private ProfessionalExamVersionRepository versionRepository;
    @Autowired private ProfessionalExamSectionRepository sectionRepository;
    @Autowired private ProfessionalExamQuestionRepository questionRepository;
    @Autowired private AdminLogRepository adminLogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalExam> getAllActiveExams() {
        return examRepository.findByActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalExam> getAllExams(Pageable pageable) {
        return examRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfessionalExam> getExamById(Long id) {
        return examRepository.findById(id);
    }

    @Override
    @Transactional
    public ProfessionalExam createExam(ProfessionalExam exam, User admin) {
        if (exam.getPassingMarks() <= 0 || exam.getTotalMarks() <= 0) {
            throw new BusinessException("INVALID_MARKS_CONFIGURATION");
        }
        if (exam.getPassingMarks() > exam.getTotalMarks()) {
            throw new BusinessException("PASSING_MARKS_CANNOT_EXCEED_TOTAL_MARKS");
        }
        exam.setActive(true);
        ProfessionalExam saved = examRepository.save(exam);
        adminLogRepository.save(new AdminLog(admin, "CREATE_EXAM", "Created exam: " + saved.getTitle()));
        return saved;
    }

    @Override
    @Transactional
    public ProfessionalExam updateExam(Long id, ProfessionalExam updated, User admin) {
        ProfessionalExam exam = examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        exam.setTitle(updated.getTitle());
        exam.setDescription(updated.getDescription());
        exam.setPassingMarks(updated.getPassingMarks());
        exam.setTotalMarks(updated.getTotalMarks());
        exam.setDurationMinutes(updated.getDurationMinutes());
        exam.setActive(updated.isActive());
        ProfessionalExam saved = examRepository.save(exam);
        adminLogRepository.save(new AdminLog(admin, "UPDATE_EXAM", "Updated exam: " + saved.getTitle()));
        return saved;
    }

    @Override
    @Transactional
    public void publishExamVersion(Long versionId, User admin) {
        ProfessionalExamVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        List<ProfessionalExamSection> sections = sectionRepository.findByExamVersionId(versionId);
        if (sections.isEmpty()) {
            throw new BusinessException("CANNOT_PUBLISH_VERSION_WITHOUT_SECTIONS");
        }

        List<ProfessionalExamQuestion> questions = questionRepository.findByExamSectionExamVersionId(versionId);
        if (questions.isEmpty()) {
            throw new BusinessException("CANNOT_PUBLISH_VERSION_WITHOUT_QUESTIONS");
        }

        int calculatedMarks = questions.stream().mapToInt(ProfessionalExamQuestion::getMarks).sum();
        version.setTotalMarks(calculatedMarks);
        version.setPublished(true);
        version.setActive(true);
        versionRepository.save(version);

        adminLogRepository.save(new AdminLog(admin, "PUBLISH_EXAM_VERSION",
                "Published version: " + version.getVersionNumber() + " for exam ID: " + version.getProfessionalExam().getId()));
    }

    private static final Logger log = LoggerFactory.getLogger(ProfessionalExamServiceImpl.class);

    @Override
    @Transactional
    public ProfessionalExamVersion ensurePublishedVersionWithQuestions(ProfessionalExam exam) {
        if (exam == null) return null;

        // 1. Check if an active published version already exists with questions
        List<ProfessionalExamVersion> existingVersions = versionRepository.findByProfessionalExamId(exam.getId());
        for (ProfessionalExamVersion v : existingVersions) {
            if (v.isPublished()) {
                List<ProfessionalExamQuestion> qs = questionRepository.findByExamSectionExamVersionId(v.getId());
                if (!qs.isEmpty()) {
                    return v;
                }
            }
        }

        // 2. Create or reuse version
        ProfessionalExamVersion version;
        if (!existingVersions.isEmpty()) {
            version = existingVersions.get(0);
        } else {
            version = new ProfessionalExamVersion();
            version.setProfessionalExam(exam);
            version.setVersionNumber("v1.0");
            version.setVersionName("Standard Release v1.0");
            version.setTotalMarks(exam.getTotalMarks() > 0 ? exam.getTotalMarks() : 100);
            version.setPassingMarks(exam.getPassingMarks() > 0 ? exam.getPassingMarks() : 70);
            version.setDurationMinutes(exam.getDurationMinutes() > 0 ? exam.getDurationMinutes() : 60);
        }
        version.setActive(true);
        version.setPublished(true);
        version = versionRepository.save(version);

        // 3. Ensure Section exists
        List<ProfessionalExamSection> sections = sectionRepository.findByExamVersionId(version.getId());
        ProfessionalExamSection section;
        if (!sections.isEmpty()) {
            section = sections.get(0);
        } else {
            section = new ProfessionalExamSection();
            section.setExamVersion(version);
            section.setName("Core Knowledge & Practical Proficiency");
            section.setDescription("Standardized technical and conceptual assessment");
            section.setDisplayOrder(1);
            section.setTotalMarks(version.getTotalMarks());
            section.setActive(true);
            section = sectionRepository.save(section);
        }

        // 4. Populate questions if empty
        List<ProfessionalExamQuestion> existingQuestions = questionRepository.findByExamSectionExamVersionId(version.getId());
        if (existingQuestions.isEmpty()) {
            List<ProfessionalExamQuestion> parsedQuestions = parseQuestionsFromDescription(exam.getDescription(), section, exam);
            if (!parsedQuestions.isEmpty()) {
                for (ProfessionalExamQuestion q : parsedQuestions) {
                    questionRepository.save(q);
                }
            } else {
                List<ProfessionalExamQuestion> defaultQuestions = generateDefaultQuestionsForExam(section, exam);
                for (ProfessionalExamQuestion q : defaultQuestions) {
                    questionRepository.save(q);
                }
            }
        }

        // Recalculate marks
        List<ProfessionalExamQuestion> allQ = questionRepository.findByExamSectionExamVersionId(version.getId());
        int sumMarks = allQ.stream().mapToInt(ProfessionalExamQuestion::getMarks).sum();
        if (sumMarks > 0) {
            version.setTotalMarks(sumMarks);
            section.setTotalMarks(sumMarks);
            versionRepository.save(version);
            sectionRepository.save(section);
        }

        // 5. Clean exam description if it contains raw JSON or bracket tags
        if (exam.getDescription() != null && (exam.getDescription().contains("[{") || exam.getDescription().contains("[Category:"))) {
            String cleanDesc = exam.getDescription();
            int jsonIdx = cleanDesc.indexOf("[{");
            if (jsonIdx >= 0) {
                cleanDesc = cleanDesc.substring(0, jsonIdx).trim();
            }
            cleanDesc = cleanDesc.replaceAll("\\[Category:[^\\]]+\\]", "").replaceAll("\\[Track:[^\\]]+\\]", "").trim();
            if (cleanDesc.isEmpty()) {
                cleanDesc = "Official SkillHub Professional Examination for " + exam.getTitle() + ". Evaluates theoretical depth, problem-solving, and practical competencies.";
            }
            exam.setDescription(cleanDesc);
            examRepository.save(exam);
        }

        return version;
    }

    private List<ProfessionalExamQuestion> parseQuestionsFromDescription(String desc, ProfessionalExamSection section, ProfessionalExam exam) {
        List<ProfessionalExamQuestion> result = new ArrayList<>();
        if (desc == null || !desc.contains("[")) return result;

        try {
            int start = desc.indexOf("[{");
            int end = desc.lastIndexOf("}]");
            if (start >= 0 && end > start) {
                String json = desc.substring(start, end + 2);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> list = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});

                int totalMarks = exam.getTotalMarks() > 0 ? exam.getTotalMarks() : 100;
                int marksPerQ = Math.max(1, list.isEmpty() ? 1 : totalMarks / list.size());

                for (Map<String, Object> item : list) {
                    String qText = (String) item.getOrDefault("question", item.get("questionText"));
                    if (qText == null || qText.trim().isEmpty()) continue;

                    ProfessionalExamQuestion q = new ProfessionalExamQuestion();
                    q.setExamSection(section);
                    q.setQuestionText(qText.trim());
                    q.setTopic(exam.getTrack() != null ? exam.getTrack().name() : "General");
                    q.setDifficulty(exam.getLevel() != null ? exam.getLevel() : ExamDifficulty.INTERMEDIATE);
                    q.setMarks(marksPerQ);
                    q.setActive(true);

                    // Options
                    Object optObj = item.get("options");
                    List<String> options = new ArrayList<>();
                    if (optObj instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) optObj;
                        for (Map.Entry<?, ?> e : map.entrySet()) {
                            options.add("(" + String.valueOf(e.getKey()).toUpperCase() + ") " + String.valueOf(e.getValue()));
                        }
                    } else if (optObj instanceof List) {
                        for (Object o : (List<?>) optObj) {
                            options.add(String.valueOf(o));
                        }
                    }

                    String ans = String.valueOf(item.getOrDefault("answer", item.get("correctAnswer")));
                    q.setCorrectAnswer(ans != null ? ans.trim() : "a");
                    q.setExplanation(item.containsKey("explanation") ? String.valueOf(item.get("explanation")) : "");

                    String code = item.containsKey("code") ? String.valueOf(item.get("code")) : "";
                    if (code != null && !code.trim().isEmpty()) {
                        q.setQuestionType(QuestionType.CODING);
                        q.setCodeStarterTemplate(code);
                        q.setCodeLanguage("java");
                    } else if (options.size() == 2 && (options.get(0).toLowerCase().contains("true") || options.get(1).toLowerCase().contains("false"))) {
                        q.setQuestionType(QuestionType.TRUE_FALSE);
                        q.setOptions(options);
                    } else if (!options.isEmpty()) {
                        q.setQuestionType(QuestionType.MCQ);
                        q.setOptions(options);
                    } else {
                        q.setQuestionType(QuestionType.SHORT_ANSWER);
                    }

                    result.add(q);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse questions JSON from exam description: {}", e.getMessage());
        }
        return result;
    }

    private List<ProfessionalExamQuestion> generateDefaultQuestionsForExam(ProfessionalExamSection section, ProfessionalExam exam) {
        List<ProfessionalExamQuestion> list = new ArrayList<>();
        String title = exam.getTitle() != null ? exam.getTitle() : "Professional Certification";
        int totalMarks = exam.getTotalMarks() > 0 ? exam.getTotalMarks() : 100;
        int marksPerQ = Math.max(1, totalMarks / 10);

        String[][] mcqData = {
            {"Which of the following best defines the primary architectural goal of " + title + "?",
             "(A) Separation of concerns and modular component reusability",
             "(B) Tightly coupled monolithic memory allocation",
             "(C) Unchecked global state variables",
             "(D) Direct hardware instruction execution",
             "(A)"},
            {"In a high-throughput enterprise environment, what is the best practice for managing database connectivity in " + title + "?",
             "(A) Opening a new unpooled connection for each client request",
             "(B) Implementing a tuned connection pool with idle timeouts and max lifetimes",
             "(C) Keeping a single global connection open indefinitely without timeouts",
             "(D) Disabling transactions to eliminate locking overhead",
             "(B)"},
            {"How does asynchronous execution contribute to high performance in " + title + " services?",
             "(A) It blocks the CPU thread until network I/O finishes",
             "(B) It eliminates the need for unit testing",
             "(C) It frees caller threads to process concurrent requests while waiting for I/O",
             "(D) It forces synchronous serialization across all callers",
             "(C)"},
            {"Which of the following security mechanisms is essential for protecting " + title + " REST APIs from unauthorized access?",
             "(A) Short-lived cryptographic tokens (e.g. JWT) with role-based authorization",
             "(B) Embedding plaintext credentials in URL query parameters",
             "(C) Trusting all client-supplied headers without verification",
             "(D) Disabling HTTPS encryption in staging environments",
             "(A)"},
            {"What is the primary benefit of the Idempotency pattern in " + title + " distributed transactions?",
             "(A) It ensures each request increments counters multiple times",
             "(B) Retrying a request produces the exact same outcome without duplicate side effects",
             "(C) It doubles the memory footprint of incoming payloads",
             "(D) It bypasses input sanitization for performance",
             "(B)"},
            {"When debugging performance bottlenecks in " + title + ", which metric should be prioritized to ensure optimal user experience?",
             "(A) Maximum theoretical clock speed",
             "(B) p99 latency percentiles and error rates under peak load",
             "(C) Total number of git commits per release",
             "(D) File size of the compiled application package",
             "(B)"},
            {"What role does the Single Responsibility Principle (SRP) play in maintainable " + title + " codebases?",
             "(A) Combining data storage, UI rendering, and business rules in a single class",
             "(B) Ensuring a class has only one reason to change, keeping behavior cohesive and decoupled",
             "(C) Restricting software to only run on single-core processors",
             "(D) Eliminating modular interfaces across packages",
             "(B)"},
            {"In a cloud-native deployment of " + title + ", why are stateless services preferred?",
             "(A) They allow seamless horizontal auto-scaling and zero-downtime rolling upgrades",
             "(B) They store session data directly in server local disk memory",
             "(C) They prevent containers from using network interfaces",
             "(D) They require manual server restarts on each user session",
             "(A)"},
            {"Which strategy is most effective for mitigating SQL Injection vulnerabilities in " + title + "?",
             "(A) Concatenating raw user inputs into dynamic SQL strings",
             "(B) Using Parameterized Queries and Prepared Statements with strict input validation",
             "(C) Storing database passwords in public client-side scripts",
             "(D) Granting root DBA privileges to the web application connection",
             "(B)"},
            {"What is the fundamental purpose of Continuous Integration and Automated Testing in " + title + "?",
             "(A) Eliminating the need for code reviews",
             "(B) Detecting integration defects, broken contracts, and regressions early in the lifecycle",
             "(C) Delaying deployments until manual smoke testing in production",
             "(D) Generating synthetic documentation without compiling code",
             "(B)"}
        };

        for (int i = 0; i < mcqData.length; i++) {
            String[] row = mcqData[i];
            ProfessionalExamQuestion q = new ProfessionalExamQuestion();
            q.setExamSection(section);
            q.setQuestionText(row[0]);
            q.setQuestionType(QuestionType.MCQ);
            q.setDifficulty(exam.getLevel() != null ? exam.getLevel() : ExamDifficulty.INTERMEDIATE);
            q.setTopic(exam.getTrack() != null ? exam.getTrack().name() : "General");
            q.setMarks(marksPerQ);
            q.setActive(true);

            List<String> opts = new ArrayList<>();
            opts.add(row[1]);
            opts.add(row[2]);
            opts.add(row[3]);
            opts.add(row[4]);
            q.setOptions(opts);

            q.setCorrectAnswer(row[5]);
            q.setExplanation("Verified core competency for " + title);
            list.add(q);
        }
        return list;
    }
}
