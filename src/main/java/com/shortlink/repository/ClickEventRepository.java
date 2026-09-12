package com.shortlink.repository;

import com.shortlink.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query("""
        SELECT FUNCTION('DATE', c.clickedAt) AS day, COUNT(c)
        FROM ClickEvent c
        WHERE c.shortUrl.shortCode = :shortCode
        GROUP BY FUNCTION('DATE', c.clickedAt)
        ORDER BY day DESC
        """)
    List<Object[]> countClicksByDay(@Param("shortCode") String shortCode);

    @Query("""
        SELECT COALESCE(c.referrer, 'direct') AS referrer, COUNT(c)
        FROM ClickEvent c
        WHERE c.shortUrl.shortCode = :shortCode
        GROUP BY COALESCE(c.referrer, 'direct')
        ORDER BY COUNT(c) DESC
        """)
    List<Object[]> countClicksByReferrer(@Param("shortCode") String shortCode);
}
