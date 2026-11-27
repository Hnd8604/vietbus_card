package com.buscard.backend.repo;

import com.buscard.backend.domain.BusRouteEntity;
import com.buscard.common.CardConstants.RouteMode;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds default bus routes and stations when the database is empty. */
@Component
public class RouteSeeder implements ApplicationRunner {

    private final BusRouteRepository routeRepository;

    public RouteSeeder(BusRouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (routeRepository.count() > 0) {
            return;
        }

        BusRouteEntity r1 = new BusRouteEntity(1, "01", "Tuyến 01: Bến xe Gia Lâm - Bến xe Yên Nghĩa",
                RouteMode.FLAT, 7_000, 0, 7_000, 140_000);
        r1.addStation(101, 1, "Bến xe Gia Lâm");
        r1.addStation(102, 2, "Cầu Chương Dương");
        r1.addStation(103, 3, "Bờ Hồ");
        r1.addStation(104, 4, "Ngã Tư Sở");
        r1.addStation(105, 5, "Bến xe Yên Nghĩa");

        BusRouteEntity r32 = new BusRouteEntity(32, "32", "Tuyến 32: Bến xe Giáp Bát - Nhổn",
                RouteMode.FLAT, 8_000, 0, 8_000, 160_000);
        r32.addStation(3201, 1, "Bến xe Giáp Bát");
        r32.addStation(3202, 2, "Đại học Bách Khoa");
        r32.addStation(3203, 3, "Ga Hà Nội");
        r32.addStation(3204, 4, "Cầu Giấy");
        r32.addStation(3205, 5, "Đại học Công nghiệp (Nhổn)");

        BusRouteEntity r86 = new BusRouteEntity(86, "86", "Tuyến 86: Ga Hà Nội - Sân bay Nội Bài (theo chặng)",
                RouteMode.DISTANCE, 5_000, 2_500, 20_000, 250_000);
        r86.addStation(8601, 1, "Ga Hà Nội");
        r86.addStation(8602, 2, "Bờ Hồ");
        r86.addStation(8603, 3, "Long Biên");
        r86.addStation(8604, 4, "Âu Cơ");
        r86.addStation(8605, 5, "Cầu Nhật Tân");
        r86.addStation(8606, 6, "Nhà ga T1 Nội Bài");
        r86.addStation(8607, 7, "Nhà ga T2 Nội Bài");

        routeRepository.saveAll(List.of(r1, r32, r86));
    }
}
