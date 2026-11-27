package com.buscard.backend.repo;

import com.buscard.backend.domain.IssuerKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuerKeyRepository extends JpaRepository<IssuerKeyEntity, String> {
}
