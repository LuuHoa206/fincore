package com.luuhoa.fincore.splitbill;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SplitBillParticipantRepository extends JpaRepository<SplitBillParticipant, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select participant from SplitBillParticipant participant join fetch participant.bill bill where participant.id = :participantId and bill.id = :billId and bill.user.id = :userId")
    Optional<SplitBillParticipant> findOwnedForUpdate(
            @Param("userId") UUID userId,
            @Param("billId") UUID billId,
            @Param("participantId") UUID participantId);
}
