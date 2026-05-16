# happyzion-api

Happy Zion 웹사이트용 Kotlin + Spring Boot API 서버입니다.

이 프로젝트는 기존 교회 API를 기준으로 분리한 독립 백엔드입니다. 현재 목표는 공통 CMS가 아니라 Happy Zion 전용 데이터베이스, 업로드 저장소, 관리자 JWT 인증, YouTube 설정을 가진 별도 API를 운영하는 것입니다.

## 현재 범위

- 헬스체크 API
- 관리자 인증/계정 관리 API
- 공개 메뉴 API
- YouTube 영상 동기화와 영상 메타 API
- 게시판/게시글/첨부 업로드 API
- 교인/출석 관리 API
- Flyway 마이그레이션

## 로컬 준비

권장 런타임:

- Java 21+
- PostgreSQL 16+

```bash
cd happyzion_api
cp .env.example .env
./gradlew bootRun
```

로컬 PostgreSQL을 Docker로 띄우려면:

```bash
docker compose -f docker-compose.local.yml up -d
```

## 환경 변수

```text
DB_URL=jdbc:postgresql://localhost:5432/happyzion
DB_USERNAME=postgres
DB_PASSWORD=postgres
ADMIN_JWT_SECRET=your-admin-jwt-secret-at-least-32-bytes
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
HAPPYZION_UPLOAD_ROOT=/opt/happyzion/uploads
HAPPYZION_UPLOAD_PUBLIC_BASE_URL=http://localhost:8080/upload
YOUTUBE_API_KEY=replace-me
YOUTUBE_CHANNEL_ID=replace-me
```

## 주요 엔드포인트

- `GET /api/v1/health`
- `POST /api/v1/admin/auth/login`
- `GET /api/v1/admin/auth/me`
- `GET /api/v1/admin/accounts`
- `POST /api/v1/admin/accounts`
- `GET /api/v1/public/menu`
- `POST /api/v1/admin/uploads/token`
- `POST /api/v1/admin/uploads`

## 운영 배포 메모

- 운영 기본 경로: `/opt/happyzion`
- 업로드 기본 경로: `/opt/happyzion/uploads`
- 운영 compose: `deploy/docker-compose.prod.yml`
- nginx 템플릿:
  - `deploy/nginx/api.happyzion.com.pre-ssl.conf`
  - `deploy/nginx/happyzion-upload-http-context.conf`
  - `deploy/nginx/api.happyzion.com.conf`

운영 도메인이 확정되면 `.env.production.example`의 `CORS_ALLOWED_ORIGINS`와 `HAPPYZION_UPLOAD_PUBLIC_BASE_URL`, nginx `server_name`을 실제 도메인으로 맞춰야 합니다.
