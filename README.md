# Private Stock Monitoring Service

개인용 주식 모니터링 대시보드
키움증권 REST API 활용

## 서버(스펙)

Oracle Free Tier AMD 서버 1 OCPU, 1GB RAM Ubuntu 22.04 Minimal
- 디스크: 49GB / 메모리: 956MB RAM + Swap 4GB

## Stack
### Bacnend
- Spring Boot 4.0 : 서버
- JPA : ORM
- Querydsl

### Database
- mariadb : Main DB
- Flyway : DB Migration

### Frontend
- React
- Vite
- TypeScript

### Infra
- Oracle : 클라우드
- Docker/Docker Compose
- GHCR : Image Repository
- Github Actions : CI/CD
- Nginx : Reverse Proxy
- Duck DNS : Domain
- Let's Encrypt : SSL

### Monitoring
# TODO

## 구현 계획
### 대시보드 구성
| 표 | 설명 |
|---|---|
| 시장종합 | 코스피/코스닥 지수, 등락 종목수, 거래대금 요약 |
