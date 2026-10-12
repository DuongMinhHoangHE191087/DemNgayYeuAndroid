#!/usr/bin/env bash
# In một cổng TCP trống (1024..65535) trên 127.0.0.1 (python3 bind cổng 0); bỏ 8097/18089/18090.
# Portable bash 3.2, GNU/BSD: chỉ dùng python3, không stat/date/sed -i/ps.
n=0
while [ "$n" -lt 20 ]; do
  n=$((n + 1))
  p=$(python3 -c 'import socket
s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()') || exit 1
  case "$p" in ''|*[!0-9]*|8097|18089|18090) continue;; esac
  if [ "$p" -ge 1024 ] && [ "$p" -le 65535 ]; then echo "$p"; exit 0; fi
done
echo "free_port: không tìm được cổng trống" >&2; exit 1
