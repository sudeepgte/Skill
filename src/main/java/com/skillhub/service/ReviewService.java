package com.skillhub.service;

import com.skillhub.model.Review;
import com.skillhub.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    public Review saveReview(Review review) {
        return reviewRepository.save(review);
    }

    public Page<Review> getReviewsByCourse(com.skillhub.model.Course course, Pageable pageable) {
        return reviewRepository.findByCourse(course, pageable);
    }

    public Double getAverageRating(Long courseId) {
        return reviewRepository.getAverageRatingByCourseId(courseId).orElse(0.0);
    }

    public Integer getReviewCount(Long courseId) {
        return reviewRepository.countByCourseId(courseId);
    }
}
