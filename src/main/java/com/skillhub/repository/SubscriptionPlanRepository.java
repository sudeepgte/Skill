package com.skillhub.repository;

import com.skillhub.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findByIsActiveTrueOrderBySortOrderAsc();

    Optional<SubscriptionPlan> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
