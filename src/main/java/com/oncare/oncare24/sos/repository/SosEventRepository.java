package com.oncare.oncare24.sos.repository;

import com.oncare.oncare24.sos.entity.SosEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SosEventRepository extends JpaRepository<SosEvent, Long> {
}