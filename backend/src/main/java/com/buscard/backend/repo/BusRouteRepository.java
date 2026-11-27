package com.buscard.backend.repo;

import com.buscard.backend.domain.BusRouteEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusRouteRepository extends JpaRepository<BusRouteEntity, Integer> {

    @EntityGraph(attributePaths = "stations")
    List<BusRouteEntity> findAllByOrderByRouteIdAsc();

    @EntityGraph(attributePaths = "stations")
    Optional<BusRouteEntity> findWithStationsByRouteId(int routeId);
}
