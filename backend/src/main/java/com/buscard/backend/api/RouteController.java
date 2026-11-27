package com.buscard.backend.api;

import com.buscard.backend.domain.BusRouteEntity;
import com.buscard.backend.domain.RouteStationEntity;
import com.buscard.backend.repo.BusRouteRepository;
import com.buscard.backend.service.FareService;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.RouteMode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final BusRouteRepository routeRepository;
    private final FareService fareService;

    public RouteController(BusRouteRepository routeRepository, FareService fareService) {
        this.routeRepository = routeRepository;
        this.fareService = fareService;
    }

    public record StationDto(int stationId, int sequenceOrder, String name) {
        static StationDto from(RouteStationEntity s) {
            return new StationDto(s.getStationId(), s.getSequenceOrder(), s.getName());
        }
    }

    public record RouteDto(int routeId, String code, String name, RouteMode mode,
                           long baseFare, long farePerStation, long maxFare,
                           long monthlyPassPrice, List<StationDto> stations) {
        static RouteDto from(BusRouteEntity r) {
            return new RouteDto(
                    r.getRouteId(),
                    r.getCode(),
                    r.getName(),
                    r.getMode(),
                    r.getBaseFare(),
                    r.getFarePerStation(),
                    r.getMaxFare(),
                    r.getMonthlyPassPrice(),
                    r.getStations().stream().map(StationDto::from).toList()
            );
        }
    }

    public record PassPriceDto(PassType type, Integer routeId, Category category, long price) {
    }

    @GetMapping("/routes")
    public List<RouteDto> listRoutes() {
        return routeRepository.findAllByOrderByRouteIdAsc().stream().map(RouteDto::from).toList();
    }

    @GetMapping("/fares/quote")
    public FareService.FareQuote quoteTrip(
            @RequestParam int routeId,
            @RequestParam(defaultValue = "NORMAL") Category category,
            @RequestParam(required = false) Integer fromStationId,
            @RequestParam(required = false) Integer toStationId) {
        return fareService.quoteTrip(routeId, category, fromStationId, toStationId);
    }

    @GetMapping("/passes/pricing")
    public PassPriceDto quotePass(
            @RequestParam PassType type,
            @RequestParam(required = false) Integer routeId,
            @RequestParam(defaultValue = "NORMAL") Category category) {
        long price = fareService.quoteMonthlyPass(type, routeId, category);
        return new PassPriceDto(type, routeId, category, price);
    }
}
