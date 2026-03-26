package com.skillhub.service;

import com.skillhub.model.*;
import com.skillhub.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlacementService {

    @Autowired
    private InterviewQuestionRepository interviewRepo;

    @Autowired
    private AptitudeQuestionRepository aptitudeRepo;

    @Autowired
    private UserResumeRepository resumeRepo;

    // --- INTERVIEW QUESTIONS ---
    public List<InterviewQuestion> getAllInterviewQuestions() {
        return interviewRepo.findAll();
    }

    public List<InterviewQuestion> getInterviewQuestionsByCategory(String category) {
        return interviewRepo.findByCategory(category);
    }

    // --- APTITUDE TESTS ---
    public List<AptitudeQuestion> getAllAptitudeQuestions() {
        return aptitudeRepo.findAll();
    }

    public List<AptitudeQuestion> getAptitudeQuestionsByCategory(String category) {
        return aptitudeRepo.findByCategory(category);
    }

    public List<AptitudeQuestion> getRandomAptitudeQuestions(int count) {
        List<AptitudeQuestion> all = aptitudeRepo.findAll();
        Collections.shuffle(all);
        return all.subList(0, Math.min(all.size(), count));
    }

    // --- RESUME BUILDER ---
    public UserResume getResumeByUser(User user) {
        return resumeRepo.findByUser(user).orElseGet(() -> {
            UserResume resume = new UserResume(user);
            resume.setFullName(user.getName());
            resume.setEmail(user.getEmail());
            return resumeRepo.save(resume);
        });
    }

    public void saveResume(UserResume resume) {
        resumeRepo.save(resume);
    }
}
