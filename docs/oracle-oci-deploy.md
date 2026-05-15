# Oracle OCI Deploy Guide

`happyzion_api`를 `Oracle Cloud Infrastructure` 단일 VM에 배포하고 운영할 때 사용하는 현재 기준 요약 문서다.

처음 서버를 세팅할 때부터 GHCR, GitHub Actions, 스키마 변경 배포, 점검/복구까지 포함한 전체 절차는 [oracle-oci-operations-manual.md](./oracle-oci-operations-manual.md)를 우선 참고한다.

게시판 업로드 기능 배포 시에는 DB 백업, 디스크 체크, 임시 업로드 정리, `deploy/scripts/*.sh` 운영 배치 여부를 [db-backup-restore-runbook.md](./db-backup-restore-runbook.md)의 `Production Deployment Checklist`와 함께 확인한다.

대상 구조:

- nginx
- Docker Compose
- Spring Boot app
- PostgreSQL 16

## 1. 운영 디렉토리 배치

운영 VM에는 애플리케이션 파일을 둘 배포 디렉토리가 필요하다.
이 문서에서는 예시로 `<deploy-path>`를 사용한다.

일반적인 예시:

```text
<deploy-path>=/opt/happyzion
```

필수 파일:

```text
<deploy-path>/.env
<deploy-path>/docker-compose.prod.yml
```

초기 작성은 아래 예시 파일을 기준으로 한다.

- `.env.production.example`
- `deploy/docker-compose.prod.yml`

## 2. 운영 환경 변수 작성

로컬에서 아래 파일을 기준으로 운영 값을 채운다.

```bash
cp .env.production.example /tmp/happyzion.env
```

핵심 값:

- `APP_IMAGE=ghcr.io/<github-owner>/<github-repository>:latest`
- `POSTGRES_DB=happyzion`
- `POSTGRES_USER=postgres`
- `POSTGRES_PASSWORD=<strong-password>`
- `DB_URL=jdbc:postgresql://db:5432/happyzion`
- `DB_USERNAME=postgres`
- `DB_PASSWORD=<same-as-postgres-password>`
- `YOUTUBE_*`
- `ADMIN_SYNC_KEY`
- `ADMIN_ACTOR_SIGNING_SECRET`
- `CORS_ALLOWED_ORIGINS=https://<web-domain>,https://www.<web-domain>`
- `HAPPYZION_UPLOAD_ROOT=/opt/happyzion/uploads`
- `HAPPYZION_UPLOAD_PUBLIC_BASE_URL=https://<api-domain>/upload`

완성한 파일을 VM에 업로드한다.

```bash
scp /tmp/happyzion.env <oci-user>@<oci-host>:<deploy-path>/.env
scp deploy/docker-compose.prod.yml <oci-user>@<oci-host>:<deploy-path>/docker-compose.prod.yml
```

주의:

- 이 `APP_IMAGE` 값은 수동 실행이나 초기 부트스트랩용 기본값이다.
- GitHub Actions 자동 배포는 이 값을 직접 수정하지 않고, 해당 배포에서 막 빌드한 `sha-<commit>` 이미지를 일회성 환경 변수로 주입해서 배포한다.
- 저장소가 개인 계정에서 Organization으로 이동되면 GHCR owner도 같이 바뀔 수 있으므로 `<github-owner>`는 현재 패키지 owner 기준으로 맞춰야 한다.

## 3. PostgreSQL + 앱 기동

VM에서 최초 기동:

```bash
cd <deploy-path>
sudo mkdir -p /opt/happyzion/uploads
sudo chown -R 1000:1000 /opt/happyzion/uploads
docker compose -f docker-compose.prod.yml up -d db
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml ps
curl http://127.0.0.1:8080/api/v1/health
```

DB 복원이나 점검이 필요하면 먼저 `db`만 올리고, 작업이 끝난 뒤 `app`을 기동해도 된다.

## 4. nginx 초기 설정

패키지 설치:

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
sudo mkdir -p /var/www/certbot
sudo chown -R www-data:www-data /var/www/certbot
```

인증서 발급 전에는 HTTP-only 설정을 사용한다.

```bash
sudo cp deploy/nginx/api.happyzion.com.pre-ssl.conf /etc/nginx/sites-available/<api-domain>
sudo ln -sf /etc/nginx/sites-available/<api-domain> /etc/nginx/sites-enabled/<api-domain>
sudo nginx -t
sudo systemctl reload nginx
```

주의:

- 현재 저장소의 nginx 템플릿 파일명은 `api.happyzion.com` 기준이다.
- 실제 운영 도메인이 다르면 템플릿 내부 `server_name`과 대상 파일명을 같이 맞춰야 한다.
- 이 단계에서는 `<api-domain>` DNS가 `<oci-host>`를 가리켜야 한다.

## 5. certbot 발급 후 HTTPS 설정

DNS 전파가 끝났으면 인증서를 발급한다.

```bash
sudo certbot --nginx -d <api-domain>
```

그 다음 최종 nginx 설정으로 교체한다.

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

## 6. GitHub Actions 배포

워크플로 파일:

```text
.github/workflows/deploy-oracle.yml
```

필수 GitHub Secrets:

- `OCI_HOST`
- `OCI_USERNAME`
- `OCI_SSH_PRIVATE_KEY`
- `OCI_DEPLOY_PATH`
- `GHCR_USERNAME`
- `GHCR_TOKEN`

예시:

- `OCI_HOST=<oci-host>`
- `OCI_USERNAME=<oci-user>`
- `OCI_DEPLOY_PATH=<deploy-path>`

`GHCR_TOKEN`은 이 워크플로에서 두 용도로 쓰인다.

- GitHub Actions runner에서 GHCR push
- 운영 VM에서 GHCR pull

따라서 최소 권한은 `write:packages`다. private 저장소/패키지 환경이면 보통 `repo`도 같이 준다.

배포 워크플로는 다음 순서로 동작한다.

1. 테스트 실행
2. GHCR 이미지 `latest`와 `sha-<commit>`를 같이 빌드 및 push
3. 운영 VM에 `docker-compose.prod.yml` 업로드
4. SSH 접속 후 해당 배포의 `sha-<commit>` 이미지를 직접 `docker pull`
5. 같은 `sha-<commit>` 이미지를 사용해 `docker compose up -d --remove-orphans`

운영상 의미:

- `.env`의 `APP_IMAGE`를 매 배포마다 사람이 바꿀 필요는 없다.
- 자동 배포는 항상 그 배포에서 생성한 immutable `sha-<commit>` 태그를 사용한다.
- `.env`의 `latest`는 수동 복구/초기 기동 시 fallback 값으로 유지하면 된다.

GHCR owner가 바뀌는 대표 상황:

- 저장소를 개인 계정에서 Organization으로 이전한 경우
- 패키지를 다른 owner namespace로 다시 push한 경우

이 경우에는 `<deploy-path>/.env`의 `APP_IMAGE` 기본값, GitHub Packages의 실제 package owner, GitHub Actions가 push한 태그 경로가 서로 같은 owner를 가리키는지 같이 확인해야 한다.

## 7. 운영 검증 체크리스트

- `<api-domain>` DNS가 운영 VM을 가리킴
- `https://<api-domain>/api/v1/health` 응답 정상
- `https://<api-domain>/api/v1/public/menu` 응답 정상
- `https://<api-domain>/upload/<known-uploaded-file>` 응답 정상
- `happyzion-web`의 API base URL이 `https://<api-domain>`을 가리킴
- 운영 VM의 `<deploy-path>/.env`와 GitHub Actions secrets가 서로 일관됨
- 운영 VM의 `<deploy-path>/.env`에 `HAPPYZION_UPLOAD_ROOT=/opt/happyzion/uploads`, `HAPPYZION_UPLOAD_PUBLIC_BASE_URL=https://<api-domain>/upload`이 설정됨
- 게시판 업로드 운영 항목은 [db-backup-restore-runbook.md](./db-backup-restore-runbook.md)의 `Production Deployment Checklist`를 같이 완료함
