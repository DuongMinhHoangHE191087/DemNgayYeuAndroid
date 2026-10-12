"""So sánh nhóm treatment với nhóm đối chứng (holdout).

Đây là phép đo trả lời câu hỏi quan trọng nhất về brain: *"personalization có tốt hơn không
làm gì không?"* Mọi con số ARPDAU tăng đều có thể do mùa vụ, do bản cập nhật game, do chiến
dịch UA. Chỉ nhóm đối chứng chạy SONG SONG mới cho so sánh sạch.

Trọng tâm của bộ test này không phải phép trừ hai số, mà là **những chỗ phép đo dễ nói dối**:
người chưa được phân nhóm, và mẫu quá nhỏ để kết luận.
"""

from __future__ import annotations

import pytest

from app import metrics


def decision(user, holdout, day="2026-08-24"):
    return {"event_name": "brain_decision", "user_id": user, "client_ts_date": day,
            "is_holdout": 1 if holdout else 0, "guardrails_applied": ""}


def iap(user, micros, day="2026-08-24", currency="USD"):
    return {"event_name": "iap_purchase", "user_id": user, "client_ts_date": day,
            "price_micros": micros, "currency_code": currency, "channel": "PLAY_STORE",
            "is_subscription": 0}


def ad(user, amount=0.01, day="2026-08-24"):
    return {"event_name": "ad_revenue", "user_id": user, "client_ts_date": day,
            "ad_revenue": amount, "currency_code": "USD"}


def many(prefix, n, holdout, micros=0):
    rows = []
    for i in range(n):
        u = f"{prefix}{i}"
        rows.append(decision(u, holdout))
        if micros:
            rows.append(iap(u, micros))
    return rows


# ═══ Phân nhóm ═══

def test_phan_nhom_lay_tu_co_holdout_trong_decision_log():
    r = metrics.compare_holdout([decision("u1", False), decision("u2", True)])
    assert r["treatment"]["users"] == 1
    assert r["holdout"]["users"] == 1


def test_nguoi_chua_tung_co_quyet_dinh_KHONG_bi_gan_vao_nhom_nao():
    # Người ngoài rollout, hoặc chơi trước khi brain được bật, không có quyết định nào.
    # Gán họ vào một trong hai nhóm sẽ làm loãng chính phép đo — và gán vào treatment
    # (nhóm đông hơn) sẽ kéo trung bình về phía dân số chung, che mất hiệu ứng thật.
    rows = [decision("u1", False), iap("u_la", 5_000_000)]
    r = metrics.compare_holdout(rows)
    assert r["unassigned"]["users"] == 1
    assert r["treatment"]["users"] == 1
    assert r["holdout"]["users"] == 0


def test_doanh_thu_cua_nguoi_chua_phan_nhom_khong_vao_ca_hai_nhom():
    rows = [decision("u1", False), iap("u1", 1_000_000), iap("u_la", 9_000_000)]
    r = metrics.compare_holdout(rows)
    assert r["treatment"]["revenue_usd"] == pytest.approx(1.0)
    assert r["holdout"]["revenue_usd"] == 0.0
    assert r["unassigned"]["revenue_usd"] == pytest.approx(9.0)


def test_mot_nguoi_luon_thuoc_dung_mot_nhom():
    # Phân nhóm phía SDK là tất định theo hash, nên cùng một user không thể vừa treatment
    # vừa holdout. Nếu log có mâu thuẫn thì đó là lỗi thật, phải báo chứ không im lặng chọn bừa.
    rows = [decision("u1", False), decision("u1", True)]
    r = metrics.compare_holdout(rows)
    assert r["conflicting_users"] == 1


# ═══ Chống kết luận sớm ═══

def test_mau_qua_nho_thi_bao_la_CHUA_du_du_lieu():
    # doc 38 mục F: "so sánh sau ít nhất 2 tuần; đừng kết luận sớm hơn". Công cụ phải cưỡng
    # chế sự thận trọng đó, không chỉ hiện số rồi để người đọc tự nhớ.
    r = metrics.compare_holdout(many("t", 3, False, 1_000_000) + many("h", 2, True))
    assert r["sufficient_data"] is False
    assert r["warning"] is not None
    assert "chưa đủ" in r["warning"].lower() or "chua du" in r["warning"].lower()


def test_du_mau_thi_khong_canh_bao():
    rows = many("t", 250, False, 1_000_000) + many("h", 250, True, 1_000_000)
    r = metrics.compare_holdout(rows)
    assert r["sufficient_data"] is True
    assert r["warning"] is None


def test_khong_co_holdout_nao_thi_noi_ro_thay_vi_bao_brain_thang():
    # Đây là cái bẫy nguy hiểm nhất: holdout_fraction = 0 thì nhóm đối chứng rỗng, và một
    # phép chia ngây thơ sẽ cho "treatment tốt hơn vô hạn".
    r = metrics.compare_holdout(many("t", 100, False, 1_000_000))
    assert r["holdout"]["users"] == 0
    assert r["sufficient_data"] is False
    assert "holdout" in r["warning"].lower()
    assert r["lift"] is None


# ═══ Phép đo ═══

def test_lift_tinh_dung_khi_du_du_lieu():
    # treatment: 200 user, mỗi người $2 -> $2/user. holdout: 200 user, mỗi người $1.
    rows = many("t", 200, False, 2_000_000) + many("h", 200, True, 1_000_000)
    r = metrics.compare_holdout(rows)
    assert r["treatment"]["revenue_per_user"] == pytest.approx(2.0)
    assert r["holdout"]["revenue_per_user"] == pytest.approx(1.0)
    assert r["lift"]["revenue_per_user_pct"] == pytest.approx(100.0)


def test_lift_am_duoc_bao_cao_binh_thuong():
    # Brain làm giảm doanh thu là một kết quả hợp lệ và phải nhìn thấy được. Che nó đi là
    # cách chắc chắn nhất để một thí nghiệm trở nên vô dụng.
    rows = many("t", 200, False, 1_000_000) + many("h", 200, True, 2_000_000)
    r = metrics.compare_holdout(rows)
    assert r["lift"]["revenue_per_user_pct"] == pytest.approx(-50.0)


def test_do_ad_pressure_de_thay_cai_gia_cua_doanh_thu():
    # Doanh thu tăng mà số quảng cáo mỗi người cũng tăng vọt thì đó không phải brain thông
    # minh hơn, chỉ là hung hăng hơn. Hai con số phải nhìn cùng nhau.
    rows = many("t", 200, False) + many("h", 200, True)
    rows += [ad("t0") for _ in range(400)]
    r = metrics.compare_holdout(rows)
    assert r["treatment"]["ads_per_user"] == pytest.approx(2.0)
    assert r["holdout"]["ads_per_user"] == 0.0


def test_khong_chia_cho_khong_khi_nhom_rong():
    r = metrics.compare_holdout([])
    assert r["treatment"]["revenue_per_user"] == 0.0
    assert r["lift"] is None
    assert r["sufficient_data"] is False


# ═══ Nối vào bốn North Star ═══

def test_so_sanh_holdout_nam_trong_ket_qua_compute():
    r = metrics.compute([decision("u1", False), decision("u2", True)])
    assert "holdout_comparison" in r
    assert r["holdout_comparison"]["treatment"]["users"] == 1
