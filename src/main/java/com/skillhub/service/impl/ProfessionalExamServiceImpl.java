package com.skillhub.service.impl;

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
}
