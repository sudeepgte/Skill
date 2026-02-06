package com.skillhub.repository;

import com.skillhub.model.VideoResource;
import com.skillhub.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VideoResourceRepository extends JpaRepository<VideoResource, Long> {
    List<VideoResource> findByCategory(Category category);

    List<VideoResource> findByCategoryOrderByUploadDateDesc(Category category);

    List<VideoResource> findByOrderByUploadDateDesc();
}
