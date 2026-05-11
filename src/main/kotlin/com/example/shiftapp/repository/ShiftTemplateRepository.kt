package com.example.shiftapp.repository

import com.example.shiftapp.domain.ShiftTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Spring Data JPA repository for [ShiftTemplate].
 *
 * The list endpoint is the only one that needs custom shape — it has to merge
 * "templates owned by the caller" with "global (ownerId IS NULL) templates"
 * in a single query. Everything else is plain CRUD off [JpaRepository].
 */
@Repository
interface ShiftTemplateRepository : JpaRepository<ShiftTemplate, Long> {

    /**
     * Templates visible to the caller [ownerId]:
     *  - all templates they personally own
     *  - all global templates (`ownerId IS NULL`)
     *
     * For ADMIN callers, the controller layer may choose to call [findAll]
     * instead so they can see other staff's personal templates as well, but
     * the **default** view even for ADMIN is the same as STAFF: own + global.
     * That keeps the standard list endpoint predictable.
     */
    @Query(
        """
        SELECT t FROM ShiftTemplate t
        WHERE t.ownerId = :ownerId OR t.ownerId IS NULL
        ORDER BY t.name ASC
        """
    )
    fun findVisibleTo(@Param("ownerId") ownerId: Long): List<ShiftTemplate>
}
