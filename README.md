# XrayGuard

> Paper 1.21.4 기반 엑스레이 감지 플러그인 — 광물 채굴 비율 통계를 이용한 확률적 탐지

## 작동 원리

4가지 지표를 가중합산하여 **의심 점수(0~100)** 를 계산합니다.

| 지표 | 기본 가중치 | 설명 |
|------|------------|------|
| OreRate | 40% | 총 채굴 대비 광물 비율 |
| 이동 직선성 | 20% | 광물까지 직선으로 접근하는지 여부 |
| Y레벨 집중도 | 20% | 다이아 최적 Y레벨(-58) 집중 여부 |
| 광물 간격 | 20% | 광물 발견 간 거리 패턴 |

## 점수 등급

| 점수 | 등급 | 기본 처리 |
|------|------|-----------|
| 0 ~ 29 | 🟢 정상 | 기록만 유지 |
| 30 ~ 59 | 🟡 CAUTION | 운영자 알림 |
| 60 ~ 79 | 🟠 ALERT | 알림 + 증거 저장 |
| 80 ~ 100 | 🔴 CRITICAL | 알림 + 증거 저장 + Discord |

## 빌드

```bash
# Java 21+ 필요
./gradlew shadowJar
# → build/libs/XrayGuard-1.0.0-all.jar
```

## 주요 명령어

| 명령어 | 권한 | 설명 |
|--------|------|------|
| `/xg status <player>` | xrayguard.check | 점수 조회 |
| `/xg top` | xrayguard.check | 의심 TOP 10 |
| `/xg debug <player>` | xrayguard.admin | 서브 점수 상세 |
| `/xg evidence <player>` | xrayguard.admin | 증거 목록 |
| `/xg reset <player>` | xrayguard.admin | 데이터 초기화 |
| `/xg whitelist <player>` | xrayguard.admin | 탐지 예외 토글 |
| `/xg reload` | xrayguard.admin | 설정 리로드 |

## 주의사항

- **자동 처벌 없음** — 점수는 참고용이며 최종 판단은 운영자가 직접 해야 합니다.
- SQLite 기반 로컬 DB (`plugins/XrayGuard/xrayguard.db`)
- Discord Webhook은 `config.yml`에서 활성화 가능
