package com.skillhub.repository;

import com.skillhub.model.ProfessionalExam;
import com.skillhub.model.enums.ExamDifficulty;
import com.skillhub.model.enums.ExamTrack;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public class ProfessionalExamRepositoryTest {

    @Autowired
    private ProfessionalExamRepository professionalExamRepository;

    @Test
    public void testCreateAndFindExam() {
        ProfessionalExam exam = new ProfessionalExam();
        exam.setExamCode("SH-DEV-001");
        exam.setTitle("Professional Developer Assessment");
        exam.setTrack(ExamTrack.DEVELOPMENT);
        exam.setLevel(ExamDifficulty.ADVANCED);
        exam.setDurationMinutes(180);
        exam.setTotalMarks(100);
        exam.setPassingMarks(65);
        exam.setNegativeMarking(false);
        exam.setMaximumAttempts(2);
        exam.setApplicationRequired(true);
        exam.setApplicationFee(new BigDecimal("1000.00"));
        exam.setActive(true);

        ProfessionalExam savedExam = professionalExamRepository.saveAndFlush(exam);

        assertThat(savedExam.getId()).isNotNull();
        assertThat(savedExam.getCreatedAt()).isNotNull();

        Optional<ProfessionalExam> foundExam = professionalExamRepository.findByExamCode("SH-DEV-001");
        assertThat(foundExam).isPresent();
        assertThat(foundExam.get().getTitle()).isEqualTo("Professional Developer Assessment");
    }

    @Test
    public void testDuplicateExamCodeRejected() {
        ProfessionalExam exam1 = new ProfessionalExam();
        exam1.setExamCode("SH-DEV-UNIQUE");
        exam1.setTitle("Test 1");
        exam1.setTrack(ExamTrack.DEVELOPMENT);
        exam1.setLevel(ExamDifficulty.ADVANCED);
        
        professionalExamRepository.saveAndFlush(exam1);

        ProfessionalExam exam2 = new ProfessionalExam();
        exam2.setExamCode("SH-DEV-UNIQUE"); // Duplicate code
        exam2.setTitle("Test 2");
        exam2.setTrack(ExamTrack.DEVELOPMENT);
        exam2.setLevel(ExamDifficulty.ADVANCED);
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            professionalExamRepository.saveAndFlush(exam2);
        });
    }
}
