package com.luuhoa.fincore.moneyjar;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JarMovementRepository extends JpaRepository<JarMovement, UUID> {
}
