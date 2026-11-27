package com.buscard.backend.repo;

import com.buscard.backend.domain.CardEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<CardEntity, String> {

    List<CardEntity> findAllByOrderByIssuedAtDesc();
}
