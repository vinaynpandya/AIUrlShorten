package com.assignment.urlshortener.repository;

import com.assignment.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    @Query("SELECT c.browser AS name, COUNT(c) AS total FROM ClickEvent c "
            + "WHERE c.shortCode = :shortCode GROUP BY c.browser")
    List<GroupCount> countByBrowserForShortCode(@Param("shortCode") String shortCode);

    interface GroupCount {
        String getName();

        long getTotal();
    }
}
