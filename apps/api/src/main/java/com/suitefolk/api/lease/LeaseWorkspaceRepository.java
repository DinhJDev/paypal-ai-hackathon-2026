package com.suitefolk.api.lease;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LeaseWorkspaceRepository extends JpaRepository<LeaseWorkspace,UUID> {
    List<LeaseWorkspace> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
    Optional<LeaseWorkspace> findByIdAndOwnerId(UUID id,UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LeaseWorkspace l where l.id=:id and l.ownerId=:owner")
    Optional<LeaseWorkspace> lockOwned(@Param("id") UUID id,@Param("owner") UUID owner);
    @Modifying
    @Query(value="INSERT INTO lease_workspace(id,owner_id) VALUES (:id,:owner) ON CONFLICT(id) DO NOTHING",nativeQuery=true)
    int createOnce(@Param("id") UUID id,@Param("owner") UUID owner);
}
