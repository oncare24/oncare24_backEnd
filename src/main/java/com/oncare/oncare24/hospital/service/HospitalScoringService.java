package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.dto.ScoredHospital;
import com.oncare.oncare24.location.util.Haversine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * 병원 리스트에 거리/영업중/응급등급 가중치를 적용하여 점수를 매기고 정렬한다.
 * <p>
 * <b>점수 공식</b> (높을수록 추천):
 * <ul>
 *     <li>기본 점수 100점에서 거리당 차감</li>
 *     <li>현재 영업 중이면 +20점</li>
 *     <li>응급실인 경우 등급별 가산 (1=권역 +30, 2=지역센터 +20, 3=지역기관 +10)</li>
 * </ul>
 *
 * <b>왜 외부에서 거리 계산을 따로?</b>
 * 클라이언트(앱)에 거리값을 함께 보여주려면 응답 DTO에 distanceMeters 필드가 필요해서
 * 점수와 함께 미리 계산해 둔다.
 */
@Slf4j
@Service
public class HospitalScoringService {

    /**
     * 거리 차감 계수: 1km당 -10점.
     */
    private static final double DISTANCE_PENALTY_PER_KM = 10.0;

    /**
     * 영업 중 가산점.
     */
    private static final double OPEN_NOW_BONUS = 20.0;

    public List<ScoredHospital> score(
            List<HospitalInfo> hospitals, double userLat, double userLon, LocalTime now) {

        return hospitals.stream()
                .map(h -> scoreOne(h, userLat, userLon, now))
                .sorted(Comparator.comparingDouble(ScoredHospital::score).reversed())
                .toList();
    }

    private ScoredHospital scoreOne(HospitalInfo h, double userLat, double userLon, LocalTime now) {
        int distanceMeters = (int) Haversine.distance(userLat, userLon, h.latitude(), h.longitude());
        Boolean openNow = isOpenAt(h, now);

        double score = 100.0;
        score -= (distanceMeters / 1000.0) * DISTANCE_PENALTY_PER_KM;
        if (Boolean.TRUE.equals(openNow)) {
            score += OPEN_NOW_BONUS;
        }
        if (h.isEmergency() && h.emergencyClass() != null) {
            score += emergencyBonus(h.emergencyClass());
        }

        return new ScoredHospital(
                h.name(),
                h.address(),
                h.tel(),
                h.latitude(),
                h.longitude(),
                distanceMeters,
                openNow,
                h.isEmergency(),
                h.emergencyClass(),
                score
        );
    }

    /**
     * NMC 시간 형식: "HHmm" 4자리 문자열 (예: 0900, 1830).
     * 정보가 없거나 파싱 실패 시 null 반환 (UI에서 "영업 정보 없음"으로 표시).
     */
    private Boolean isOpenAt(HospitalInfo h, LocalTime now) {
        if (h.weekdayOpenTime() == null || h.weekdayCloseTime() == null) return null;
        if (h.weekdayOpenTime().isBlank() || h.weekdayCloseTime().isBlank()) return null;
        try {
            LocalTime open = parseHHmm(h.weekdayOpenTime());
            LocalTime close = parseHHmm(h.weekdayCloseTime());
            if (open == null || close == null) return null;
            return !now.isBefore(open) && now.isBefore(close);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseHHmm(String hhmm) {
        if (hhmm.length() != 4) return null;
        int h = Integer.parseInt(hhmm.substring(0, 2));
        int m = Integer.parseInt(hhmm.substring(2, 4));
        if (h < 0 || h > 23 || m < 0 || m > 59) return null;
        return LocalTime.of(h, m);
    }

    /**
     * 응급실 등급별 가산점. 권역 > 지역센터 > 지역기관 순.
     */
    private double emergencyBonus(int erclass) {
        return switch (erclass) {
            case 1 -> 30.0;
            case 2 -> 20.0;
            case 3 -> 10.0;
            default -> 0.0;
        };
    }
}
