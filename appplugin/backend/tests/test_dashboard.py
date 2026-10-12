"""Test dashboard.

Hai thứ đáng test ở một trang HTML tĩnh, và chỉ hai thứ đó:

1. **Ràng buộc doc 26 mục C** — không được ẩn bớt chỉ số. Đây là ràng buộc kiến trúc, phải
   có test canh, nếu không nó chỉ là một câu trong tài liệu.
2. **Không phụ thuộc tài nguyên ngoài** — dashboard ops mà cần Internet là dashboard sẽ
   trắng đúng lúc cần nó nhất (sự cố mạng, firewall của studio).
"""

from __future__ import annotations

import importlib
import re
from pathlib import Path

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "e.jsonl"))
    monkeypatch.delenv("BRAIN_API_KEY", raising=False)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app)


def html(client) -> str:
    r = client.get("/")
    assert r.status_code == 200
    assert "text/html" in r.headers["content-type"]
    return r.text


def test_tra_ve_html(client):
    assert "<!doctype html>" in html(client).lower()


def test_render_du_bon_chi_so(client):
    # Ràng buộc trung tâm của doc 26 mục C. Xoá một chỉ số khỏi mảng NORTH_STAR sẽ làm đỏ.
    body = html(client)
    m = re.search(r"const NORTH_STAR\s*=\s*\[(.*?)\]", body, re.S)
    assert m, "khong tim thay mang NORTH_STAR"
    keys = set(re.findall(r'"(\w+)"', m.group(1)))
    assert keys == {"arpdau", "revenue", "subscription", "comfort"}


def test_moi_chi_so_deu_co_ham_render(client):
    body = html(client)
    for key in ("arpdau", "revenue", "subscription", "comfort"):
        assert re.search(rf"\n  {key}:", body), f"thieu ham render cho {key}"


def test_khong_co_nut_an_hay_bo_loc_chi_so(client):
    # Một bộ lọc chỉ số sẽ vô hiệu hoá ràng buộc "hiển thị cả bốn cùng lúc" mà không ai
    # phải sửa mảng NORTH_STAR.
    body = html(client).lower()
    for banned in ("<select", 'type="checkbox"', "display:none", "hidden="):
        assert banned not in body, f"dashboard khong duoc co {banned}"


def test_khong_phu_thuoc_tai_nguyen_ngoai(client):
    body = html(client)
    # Kiểm tra thứ THẬT SỰ tạo phụ thuộc — một URL tuyệt đối ở bất cứ đâu trong trang.
    # Bản đầu của test này còn bắt cả chuỗi "cdn", và nó khớp vào một dòng comment giải
    # thích "không dùng cdn": test bắt văn xuôi thay vì bắt hành vi.
    assert not re.search(r"""(src|href|url\()\s*=?\s*["']?https?://""", body)
    # Chỉ được fetch tương đối, tới chính backend này.
    assert 'fetch("metrics/northstar' in body


def test_canh_bao_do_phu_thap_nam_trong_trang(client):
    # ComfortIndex hiện có độ phủ 0.4 và sẽ còn như vậy cho tới khi có causal model.
    # Cảnh báo phải hiện ngay trên thẻ, không phải một dòng chú thích cuối trang.
    body = html(client)
    assert "Chưa đủ tin cậy" in body
    assert "c.warning" in body


def test_dashboard_duoc_phuc_vu_nguyen_van_khong_qua_template(client, monkeypatch):
    """Nhúng khoá vào HTML để trang "chạy được" là phát khoá cho ai mở được URL.

    Bản đầu của test này bắt chuỗi "x-api-key" và đỏ vì trang có câu thông báo lỗi
    *"thiếu hoặc sai X-API-KEY"* — một câu hữu ích cho người vận hành, không phải bí mật.
    Bắt tên của bí mật là bắt nhầm; thứ cần khoá là **không có bước thay thế nào** giữa
    file trên đĩa và nội dung trả về, vì chỉ khi có template thì mới nhúng được gì vào.
    """
    monkeypatch.setenv("BRAIN_API_KEY", "sieu-bi-mat")
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    c = TestClient(app.main.app)

    served = c.get("/").text
    on_disk = (Path(app.main.__file__).parent / "static" / "dashboard.html").read_text(encoding="utf-8")
    assert served == on_disk, "dashboard phai duoc phuc vu nguyen van, khong qua template"
    assert "sieu-bi-mat" not in served


def test_co_the_holdout_comparison(client):
    # Đây là phép đo trả lời câu hỏi quan trọng nhất về brain. Bốn North Star nói brain
    # đang làm gì; chỉ so sánh với holdout nói brain có ĐÁNG làm hay không.
    body = html(client)
    assert "renderHoldout" in body
    assert "holdout_comparison" in body


def test_hien_ca_nhom_chua_phan_nhom(client):
    # Nhóm này phải nhìn thấy được: nếu nó lớn bất thường thì phép so sánh đang đo trên
    # một phần nhỏ dân số và kết luận không suy rộng ra được.
    assert "chưa phân nhóm" in html(client)


def test_canh_bao_chua_du_du_lieu_nam_trong_trang(client):
    body = html(client)
    assert "Chưa kết luận được" in body
    assert "c.warning" in body
