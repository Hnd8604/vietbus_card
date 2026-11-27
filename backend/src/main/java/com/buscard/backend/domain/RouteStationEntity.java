package com.buscard.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Ordered bus stop along a route. */
@Entity
@Table(name = "route_stations")
public class RouteStationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private BusRouteEntity route;

    @Column(name = "station_id", nullable = false)
    private int stationId;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(nullable = false, length = 128)
    private String name;

    protected RouteStationEntity() {
    }

    public RouteStationEntity(BusRouteEntity route, int stationId, int sequenceOrder, String name) {
        this.route = route;
        this.stationId = stationId;
        this.sequenceOrder = sequenceOrder;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public BusRouteEntity getRoute() {
        return route;
    }

    public int getStationId() {
        return stationId;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public String getName() {
        return name;
    }
}
