package com.skillhub.repository;

import com.skillhub.model.AptitudeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AptitudeQuestionRepository extends JpaRepository<AptitudeQuestion, Long> {
    List<AptitudeQuestion> findByCategory(String category);
}
