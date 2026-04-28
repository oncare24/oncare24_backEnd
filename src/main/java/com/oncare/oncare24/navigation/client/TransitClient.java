package com.oncare.oncare24.navigation.client;

import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;

/**
 * 대중교통 길안내 클라이언트 인터페이스.
 * <p>
 * <b>구현체</b>:
 * <ul>
 *     <li>{@link RealOdsayClient} - ODsay API (한국 전국 버스/지하철/환승)</li>
 *     <li>{@link MockOdsayClient} - 발급 전 개발용 Mock</li>
 * </ul>
 *
 * <p><b>왜 TmapClient에서 분리했나</b>:
 * TMAP의 transit API는 한국 도시 외곽 지역에서 대중교통 응답이 잘 나오지 않는 한계 존재.
 * ODsay는 한국 대중교통 특화 API로, 시골/외곽 지역에서도 풍부한 노선 정보 제공.
 * 도보는 TMAP, 대중교통은 ODsay로 분리한 멀티 프로바이더 아키텍처.
 */
public interface TransitClient {

    /**
     * 대중교통 길안내 요청.
     *
     * @param request 출발/도착 좌표
     * @return 대중교통 경로 응답 (도보 + 버스/지하철 카드 리스트)
     */
    TransitRouteResponse getTransitRoute(RouteRequest request);
}
