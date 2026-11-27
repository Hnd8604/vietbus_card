package com.buscard.backend.service;

import com.buscard.backend.domain.BusRouteEntity;
import com.buscard.backend.domain.RouteStationEntity;
import com.buscard.backend.repo.BusRouteRepository;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.RouteMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes single-ride fares, penalties, and monthly pass prices by route and passenger category. */
@Service
@Transactional(readOnly = true)
public class FareService {

    public static final long INTERLINE_MONTHLY_PRICE = 280_000L;

    private final BusRouteRepository routeRepository;

    public FareService(BusRouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public record FareQuote(int routeId, RouteMode mode, Category category,
                            long fare, long minBoardBalance, long penalty) {
    }

    public FareQuote quoteTrip(int routeId, Category category, Integer fromStationId, Integer toStationId) {
        BusRouteEntity route = routeRepository.findWithStationsByRouteId(routeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown route: " + routeId));
        int percent = singleRidePercent(category);
        long penalty = applyPercent(route.getMaxFare(), percent);

        if (route.getMode() == RouteMode.FLAT) {
            long fare = applyPercent(route.getBaseFare(), percent);
            return new FareQuote(routeId, RouteMode.FLAT, category, fare, fare, penalty);
        }

        long minBoard = applyPercent(route.getBaseFare(), percent);
        if (fromStationId == null || toStationId == null) {
            return new FareQuote(routeId, RouteMode.DISTANCE, category, minBoard, minBoard, penalty);
        }

        int fromSeq = findSequence(route, fromStationId);
        int toSeq = findSequence(route, toStationId);
        int stops = Math.abs(toSeq - fromSeq);
        long rawFare = Math.min(route.getMaxFare(), route.getBaseFare() + stops * route.getFarePerStation());
        long fare = applyPercent(rawFare, percent);
        return new FareQuote(routeId, RouteMode.DISTANCE, category, fare, minBoard, penalty);
    }

    public long quoteMonthlyPass(PassType type, Integer routeId, Category category) {
        if (type == PassType.NONE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pass type must be SINGLE_ROUTE or INTERLINE");
        }
        long base;
        if (type == PassType.INTERLINE) {
            base = INTERLINE_MONTHLY_PRICE;
        } else {
            if (routeId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "routeId is required for SINGLE_ROUTE pass");
            }
            BusRouteEntity route = routeRepository.findById(routeId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown route: " + routeId));
            base = route.getMonthlyPassPrice();
        }
        int percent = category == Category.NORMAL ? 100 : 50;
        return applyPercent(base, percent);
    }

    private static int singleRidePercent(Category category) {
        return switch (category) {
            case NORMAL -> 100;
            case STUDENT -> 50;
            case ELDERLY, DISABLED -> 0;
        };
    }

    private static long applyPercent(long amount, int percent) {
        return (amount * percent) / 100;
    }

    private static int findSequence(BusRouteEntity route, int stationId) {
        for (RouteStationEntity s : route.getStations()) {
            if (s.getStationId() == stationId) {
                return s.getSequenceOrder();
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "Station " + stationId + " does not belong to route " + route.getRouteId());
    }
}
