package com.buscard.backend.domain;

import com.buscard.common.CardConstants.RouteMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** Bus route definition with fare policy and ordered stations. */
@Entity
@Table(name = "bus_routes")
public class BusRouteEntity {

    @Id
    @Column(name = "route_id")
    private int routeId;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RouteMode mode;

    @Column(name = "base_fare", nullable = false)
    private long baseFare;

    @Column(name = "fare_per_station", nullable = false)
    private long farePerStation;

    @Column(name = "max_fare", nullable = false)
    private long maxFare;

    @Column(name = "monthly_pass_price", nullable = false)
    private long monthlyPassPrice;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<RouteStationEntity> stations = new ArrayList<>();

    protected BusRouteEntity() {
    }

    public BusRouteEntity(int routeId, String code, String name, RouteMode mode,
                          long baseFare, long farePerStation, long maxFare, long monthlyPassPrice) {
        this.routeId = routeId;
        this.code = code;
        this.name = name;
        this.mode = mode;
        this.baseFare = baseFare;
        this.farePerStation = farePerStation;
        this.maxFare = maxFare;
        this.monthlyPassPrice = monthlyPassPrice;
    }

    public void addStation(int stationId, int sequenceOrder, String stationName) {
        stations.add(new RouteStationEntity(this, stationId, sequenceOrder, stationName));
    }

    public int getRouteId() {
        return routeId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public RouteMode getMode() {
        return mode;
    }

    public long getBaseFare() {
        return baseFare;
    }

    public long getFarePerStation() {
        return farePerStation;
    }

    public long getMaxFare() {
        return maxFare;
    }

    public long getMonthlyPassPrice() {
        return monthlyPassPrice;
    }

    public List<RouteStationEntity> getStations() {
        return stations;
    }
}
