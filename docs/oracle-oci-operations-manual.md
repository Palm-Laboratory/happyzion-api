# Oracle OCI Operations Manual

`happyzion_api` 운영 서버를 처음 세팅할 때부터, GHCR 기반 자동 배포와 수동 복구까지 한 번에 따라갈 수 있도록 정리한 매뉴얼이다.

현재 기준 운영 구성:

- Oracle Cloud Infrastructure 단일 VM
- nginx
- Docker Compose
- Spring Boot app
- PostgreSQL 16
- GitHub Actions + GHCR

관련 보조 문서:

- 빠른 요약: [oracle-oci-deploy.md](./oracle-oci-deploy.md)

## 1. 전체 구조

운영 VM 내부 기본 배치:

```text
/opt/happyzion/.env
/opt/happyzion/docker-compose.prod.yml
```

GitHub Actions 배포 흐름:

1. `main` push 또는 `workflow_dispatch`
2. 테스트 실행
3. GHCR에 `latest`와 `sha-<commit>` 이미지 push
4. Oracle VM에 SSH 접속
5. 운영 VM에서 GHCR 로그인
6. 방금 생성한 `sha-<commit>` 이미지 pull
7. `docker compose up -d --remove-orphans`

중요:

- `.env`의 `APP_IMAGE`는 수동 기동/초기 세팅용 기본값이다.
- 자동 배포는 `.env`를 수정하지 않고, 매 배포마다 해당 커밋의 `sha-<commit>` 이미지를 직접 주입해 사용한다.

## 2. GitHub 준비

### 2.1 저장소와 패키지 owner 확인

GHCR 패키지 owner는 보통 현재 저장소 owner를 따른다.

예:

- 개인 저장소 시절: `ghcr.io/chohanwool/happyzion_api:...`
- Organization 이전 후: `ghcr.io/palm-laboratory/happyzion_api:...`

저장소를 개인 계정에서 Organization으로 옮겼다면 아래 셋이 같은 owner를 가리키는지 꼭 확인한다.

- GitHub Packages 실제 owner
- GitHub Actions가 push하는 태그 경로
- 운영 서버 `.env`의 `APP_IMAGE` 기본값

### 2.2 GHCR용 PAT 발급

GitHub에서:

1. 우측 상단 프로필
2. `Settings`
3. `Developer settings`
4. `Personal access tokens`
5. `Tokens (classic)`
6. `Generate new token (classic)`

권장 설정:

- `Note`: 예) `happyzion ghcr deploy`
- `Expiration`: 운영 정책에 맞게 선택
- 권한:
  - `write:packages`
  - private 저장소/패키지면 `repo`도 같이 부여 권장

주의:

- 토큰 값은 생성 직후 한 번만 보인다.
- 저장 후에는 GitHub UI에서 값을 다시 확인할 수 없다.
- 나중에 확인 가능한 것은 이름과 마지막 수정 시각뿐이다.

### 2.3 GitHub Actions secrets 등록

저장소 `happyzion_api` 기준:

1. `Settings`
2. `Secrets and variables`
3. `Actions`

필수 secrets:

- `OCI_HOST`
- `OCI_USERNAME`
- `OCI_SSH_PRIVATE_KEY`
- `OCI_DEPLOY_PATH`
- `GHCR_USERNAME`
- `GHCR_TOKEN`

예시:

- `OCI_HOST=<oci-host>`
- `OCI_USERNAME=<oci-user>`
- `OCI_DEPLOY_PATH=/opt/happyzion`
- `GHCR_USERNAME=Chohanwool`
- `GHCR_TOKEN=<classic-pat>`

중요:

- `GHCR_TOKEN`은 GitHub Actions runner에서 GHCR push에도 쓰고, 운영 VM에서 GHCR pull에도 쓴다.
- 그래서 최소 `write:packages` 권한이 필요하다.

## 3. Oracle VM 초기 세팅

### 3.1 기본 패키지 설치

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx
```

### 3.2 Docker Engine 설치

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

그 뒤 SSH를 다시 접속하거나 `newgrp docker`로 그룹 반영 후 확인:

```bash
docker --version
docker compose version
```

### 3.3 운영 디렉토리 생성

```bash
sudo mkdir -p /opt/happyzion
sudo chown -R "$USER":"$USER" /opt/happyzion
cd /opt/happyzion
```

## 4. 운영 파일 배치

로컬 저장소에서:

```bash
cd happyzion_api
cp .env.production.example /tmp/happyzion.env
```

`/tmp/happyzion.env`를 운영값으로 채운다.

기본 예시:

```text
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Seoul
APP_IMAGE=ghcr.io/<github-owner>/<repo>:latest
POSTGRES_DB=happyzion
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<strong-password>
DB_URL=jdbc:postgresql://db:5432/happyzion
DB_USERNAME=postgres
DB_PASSWORD=<same-as-postgres-password>
YOUTUBE_API_KEY=replace-me
YOUTUBE_CHANNEL_ID=replace-me
ADMIN_SYNC_KEY=replace-me
CORS_ALLOWED_ORIGINS=https://www.happyzion.com,https://happyzion.com
HAPPYZION_UPLOAD_ROOT=/opt/happyzion/uploads
HAPPYZION_UPLOAD_PUBLIC_BASE_URL=https://api.happyzion.com/upload
```

설명:

- `APP_IMAGE`의 `latest`는 수동 초기 기동과 수동 복구용 기본값이다.
- 자동 배포는 이 값을 직접 바꾸지 않는다.
- 저장소 owner가 Organization이면 예: `ghcr.io/palm-laboratory/happyzion_api:latest`

운영 서버로 업로드:

```bash
scp /tmp/happyzion.env <oci-user>@<oci-host>:/opt/happyzion/.env
scp deploy/docker-compose.prod.yml <oci-user>@<oci-host>:/opt/happyzion/docker-compose.prod.yml
```

## 5. GHCR 로그인 확인

운영 서버에서:

```bash
docker logout ghcr.io
echo '<GHCR_PAT>' | docker login ghcr.io -u <github-username> --password-stdin
```

정상 예시:

```text
Login Succeeded
```

바로 pull 테스트:

```bash
docker pull ghcr.io/<github-owner>/<repo>:latest
```

자동 배포가 이미 한 번 실행된 뒤 특정 커밋 이미지를 확인하고 싶으면:

```bash
docker pull ghcr.io/<github-owner>/<repo>:sha-<commit-sha>
```

주의:

- `~/.docker/config.json`에 평문 경고가 나올 수 있다.
- 이 경고는 기본 동작이며, 지금 운영에는 치명적인 문제는 아니다.
- 나중에 필요하면 credential helper를 별도로 붙여 개선할 수 있다.

## 6. 최초 기동

운영 서버에서:

```bash
cd /opt/happyzion
sudo mkdir -p /opt/happyzion/uploads
sudo chown -R 1000:1000 /opt/happyzion/uploads
docker compose -f docker-compose.prod.yml up -d db
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml ps
curl http://127.0.0.1:8080/api/v1/health
```

Flyway는 앱 시작 시 자동 수행된다.

DB 복원이나 수동 점검이 필요하면 `db`만 먼저 올리고, 그 뒤 `app`을 기동해도 된다.

## 7. nginx 설정

인증서 발급 전 HTTP-only 설정:

```bash
sudo mkdir -p /var/www/certbot
sudo chown -R www-data:www-data /var/www/certbot

sudo cp deploy/nginx/api.happyzion.com.pre-ssl.conf /etc/nginx/sites-available/<api-domain>
sudo ln -sf /etc/nginx/sites-available/<api-domain> /etc/nginx/sites-enabled/<api-domain>
sudo nginx -t
sudo systemctl reload nginx
```

주의:

- 저장소의 nginx 템플릿은 `api.happyzion.com` 기준이다.
- 실제 운영 도메인이 다르면 템플릿 내부 `server_name`과 파일명도 같이 바꿔야 한다.
- 이 단계 전에 `<api-domain>` DNS가 운영 VM을 가리켜야 한다.

인증서 발급:

```bash
sudo certbot --nginx -d <api-domain>
```

HTTPS 최종 설정:

```bash
sudo cp deploy/nginx/happyzion-upload-http-context.conf /etc/nginx/conf.d/happyzion-upload-http-context.conf
sudo cp deploy/nginx/api.happyzion.com.conf /etc/nginx/sites-available/<api-domain>
sudo nginx -t
sudo systemctl reload nginx
```

검증:

```bash
curl https://<api-domain>/api/v1/health
```

## 8. 자동 배포 방식

워크플로:

```text
happyzion_api/.github/workflows/deploy-oracle.yml
```

배포 시 실제 동작:

1. 테스트 실행
2. 이미지 `latest` push
3. 이미지 `sha-<commit>` push
4. 운영 VM에 `docker-compose.prod.yml` 업로드
5. 운영 VM에서 GHCR 로그인
6. 방금 배포할 `sha-<commit>` 이미지 직접 pull
7. `docker compose up -d --remove-orphans`

운영상 의미:

- 사람이 매 배포마다 `.env`의 `APP_IMAGE`를 바꿀 필요가 없다.
- 자동 배포는 항상 immutable `sha-<commit>` 이미지를 쓴다.
- `.env`의 `latest`는 수동 fallback 값으로 유지한다.

## 9. 스키마 변경 포함 배포 절차

DB 스키마가 바뀌는 배포는 아래 순서를 권장한다.

1. 운영 DB 백업
2. 기존 앱 중지
3. GHCR 이미지 pull 가능 여부 확인
4. `db` 기동 또는 유지
5. 새 `app` 기동
6. Flyway 자동 마이그레이션 확인
7. 헬스체크와 공개 API 검증

주의:

- `docker compose down -v`는 절대 사용하지 않는다.
- `-v`를 쓰면 PostgreSQL 볼륨이 같이 지워질 수 있다.

운영 서버 예시:

```bash
cd /opt/happyzion
mkdir -p backups
docker compose -f docker-compose.prod.yml exec -T db sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "backups/happyzion_$(date +%F_%H%M%S).dump"
docker compose -f docker-compose.prod.yml stop app
docker pull ghcr.io/<github-owner>/<repo>:sha-<commit-sha>
docker compose -f docker-compose.prod.yml up -d db
docker compose -f docker-compose.prod.yml up -d --force-recreate app
docker compose -f docker-compose.prod.yml logs -f app
```

로그에서 확인할 것:

- Flyway validate 성공
- migration 적용 성공
- `Started ApiApplicationKt`

## 10. 운영 검증 체크리스트

컨테이너 상태:

```bash
docker compose -f docker-compose.prod.yml ps
```

앱 로그:

```bash
docker compose -f docker-compose.prod.yml logs --tail=200 app
```

헬스체크:

```bash
curl -fsS http://127.0.0.1:8080/api/v1/health
```

Flyway 이력:

```bash
docker compose -f docker-compose.prod.yml exec -T db sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank desc limit 20;"'
```

공개 API 점검:

```bash
curl -fsS http://127.0.0.1:8080/api/v1/public/menu | head
curl -fsS 'http://127.0.0.1:8080/api/v1/public/menu/resolve?path=/about/greeting'
curl -fsS 'http://127.0.0.1:8080/api/v1/public/videos?path=/videos/worship/sunday-worship'
```

브라우저 점검:

- 일반 공개 페이지 1개
- 영상 목록/상세 1개
- 관리자 메뉴 관리 1개

## 11. 장애/실수 시 자주 보는 문제

### 11.1 `ghcr.io/...:latest not found`

보통 원인:

- 실제 패키지 owner가 바뀌었는데 `.env`가 예전 owner를 가리킴
- `latest` 태그가 없는 owner/패키지를 보고 있음

확인:

- GitHub Packages의 실제 owner
- GitHub Actions 로그의 push 경로
- 운영 `.env`의 `APP_IMAGE`

### 11.2 `docker login ghcr.io`가 `denied`

보통 원인:

- `GHCR_TOKEN` 권한 부족
- 잘못된 사용자/owner 조합
- 오래된 PAT 사용

조치:

1. 새 classic PAT 발급
2. `write:packages` 권한 부여
3. 저장소가 private면 `repo`도 포함 검토
4. 운영 서버 `docker login` 재실행
5. GitHub Actions `GHCR_TOKEN` secret 교체

### 11.3 secret 값 확인이 안 됨

정상이다.

- GitHub Actions secret은 저장 후 실제 값을 다시 보여주지 않는다.
- 확인 가능한 건 이름과 마지막 수정 시각뿐이다.
- 유효성은 workflow 실행으로 검증한다.

### 11.4 배포는 됐는데 GHCR 로그인은 실패했던 것 같음

가능한 이유:

- 그 이미지가 운영 서버에 이미 로컬로 남아 있었음
- 이전에 pull 성공한 이미지로 컨테이너만 다시 올라간 것

즉:

- 컨테이너 실행 성공이 현재 GHCR 인증 정상이라는 뜻은 아니다.
- 다음 배포 전에 `docker pull` 단독 확인이 필요하다.

## 12. 수동 복구와 롤백

앱만 이전 이미지로 되돌리는 경우:

1. `.env`의 `APP_IMAGE`를 이전 정상 이미지로 수정
2. 앱 재기동

예:

```bash
cd /opt/happyzion
vi .env
docker compose -f docker-compose.prod.yml up -d --force-recreate app
```

주의:

- DB 마이그레이션이 비가역이면 앱 롤백만으로는 부족할 수 있다.
- 그런 경우는 DB 백업 복원까지 같이 검토해야 한다.

## 13. 운영자 메모

- `APP_IMAGE`를 매번 손으로 바꿔야 하는 구조가 아니다.
- 자동 배포는 `sha-<commit>`를 직접 주입한다.
- `.env`의 `latest`는 초기 세팅과 수동 fallback 용도다.
- 토큰을 교체한 뒤에는:
  - 운영 서버 `docker login`
  - GitHub Actions `GHCR_TOKEN` secret 업데이트
  - `docker pull ghcr.io/<owner>/<repo>:latest`
  이 세 가지를 한 번 확인하면 된다.
