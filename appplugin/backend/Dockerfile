FROM python:3.12-slim

WORKDIR /srv

# Cài dependency trước, copy code sau: đổi code không làm mất cache lớp cài đặt.
COPY requirements.txt .
# Có nháy: không nháy thì shell hiểu `>`/`=` là chuyển hướng. Phiên bản ghim = bản đã chạy thật
# với bigquery-emulator (plan 1.7.0 P5).
RUN pip install --no-cache-dir -r requirements.txt "google-cloud-bigquery==3.45.2"

COPY app ./app

# `/health` trả `version` = sha này (docker build --build-arg GIT_SHA=$(git rev-parse --short HEAD)).
ARG GIT_SHA=unknown
ENV BRAIN_GIT_SHA=$GIT_SHA

# Cloud Run cấp cổng qua $PORT; mặc định 8080 để chạy được cả ngoài Cloud Run.
ENV PORT=8080
EXPOSE 8080

# MỘT worker là chủ đích ở mốc này. `JsonlSink` khoá theo tiến trình, nên nhiều worker
# ghi cùng file sẽ xen kẽ bản ghi. Khi chuyển hẳn sang BigQuery thì tăng worker được —
# nhưng lúc đó phải đo lại, đừng tăng vì "nghe có vẻ nhanh hơn".
CMD exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT} --workers 1
