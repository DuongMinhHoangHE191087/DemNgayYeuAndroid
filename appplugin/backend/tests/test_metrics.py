"""Test bốn North Star Metric.

Trọng tâm không phải "phép cộng có đúng không" mà là **những chỗ dễ sai một cách im lặng**:
quy đổi tiền tệ, mẫu số của ARPDAU, và ràng buộc không được giấu ComfortIndex.
"""

from __future__ import annotations

import pytest

from app import metrics


def row(**kw):
    base = {"client_ts_date": "2026-08-24", "user_id": "u1", "event_name": "brain_decision"}
    base.update(kw)
    return base


def iap(price_micros, currency="USD", channel="PLAY_STORE", sub=0, user="u1", day="2026-08-24"):
    return row(event_name="iap_purchase", price_micros=price_micros, currency_code=currency,
               channel=channel, is_subscription=sub, user_id=user, client_ts_date=day)


def ad(amount, currency="USD", user="u1", day="2026-08-24"):
    return row(event_name="ad_revenue", ad_revenue=amount, currency_code=currency,
               user_id=user, client_ts_date=day)


def comfort(value, coverage, missing="a;b;c", day="2026-08-24"):
    return row(event_name="brain_comfort", comfort_value=value, comfort_coverage=coverage,
               components_missing=missing, client_ts_date=day)


# ═══ Ràng buộc kiến trúc quan trọng nhất ═══

def test_luon_tra_ve_ca_bon_chi_so():
    # Doc 26 mục C: không được xem riêng ARPDAU mà ẩn ComfortIndex.
    r = metrics.compute([])
    assert {"arpdau", "revenue", "subscription", "comfort"} <= set(r)


def test_khong_co_cach_nao_yeu_cau_tap_con():
    # Chữ ký hàm chỉ nhận rows + fx. Thêm một tham số kiểu `only=[...]` sẽ phá ràng buộc
    # trên, nên test này khoá luôn chữ ký.
    import inspect
    params = set(inspect.signature(metrics.compute).parameters)
    assert params == {"rows", "fx"}


def test_comfort_do_phu_thap_phai_tu_canh_bao():
    r = metrics.compute([comfort(0.8, 0.4)])
    assert r["comfort"]["trustworthy"] is False
    assert "DO PHU THAP" in r["comfort"]["warning"]
    assert r["comfort"]["components_missing"] == ["a", "b", "c"]


def test_comfort_du_do_phu_thi_khong_canh_bao():
    r = metrics.compute([comfort(0.8, 0.8)])
    assert r["comfort"]["trustworthy"] is True
    assert r["comfort"]["warning"] is None


# ═══ Quy đổi tiền tệ ═══

def test_doanh_thu_khong_quy_doi_duoc_KHONG_bi_bo_qua():
    # Đây là test quan trọng nhất của file. Bỏ qua âm thầm sẽ làm ARPDAU thấp giả tạo đúng ở
    # những thị trường chưa nạp tỉ giá — sai lệch có hệ thống theo vùng địa lý, trông hệt như
    # một kết luận về thị trường.
    r = metrics.compute([iap(99_000_000_000, "VND")], fx={})
    assert r["revenue"]["iap_usd"] == 0.0
    assert r["revenue"]["unconverted"]["by_currency"]["VND"] == pytest.approx(99000.0)
    assert r["revenue"]["unconverted"]["event_count"] == 1


def test_quy_doi_dung_khi_co_ti_gia():
    r = metrics.compute([iap(9_990_000, "EUR")], fx={"EUR": 1.08})
    assert r["revenue"]["iap_usd"] == pytest.approx(10.79, abs=0.01)
    assert r["revenue"]["unconverted"]["event_count"] == 0


def test_USD_khong_can_ti_gia():
    r = metrics.compute([iap(4_990_000, "USD")], fx={})
    assert r["revenue"]["iap_usd"] == pytest.approx(4.99)


def test_ti_gia_am_bi_coi_nhu_khong_biet():
    r = metrics.compute([iap(1_000_000, "EUR")], fx={"EUR": -1.0})
    assert r["revenue"]["iap_usd"] == 0.0
    assert "EUR" in r["revenue"]["unconverted"]["by_currency"]


# ═══ ARPDAU ═══

def test_arpdau_gom_ca_hai_ve_iap_va_iaa():
    r = metrics.compute([iap(1_000_000), ad(2.0)])
    assert r["revenue"]["iap_usd"] == pytest.approx(1.0)
    assert r["revenue"]["iaa_usd"] == pytest.approx(2.0)
    assert r["arpdau"]["value_usd"] == pytest.approx(3.0)   # 1 ngay, 1 user


def test_mau_so_la_DAU_trung_binh_theo_ngay_khong_phai_user_duy_nhat_toan_ky():
    # Chia cho tổng user duy nhất cả kỳ sẽ cho con số nhỏ hơn nhiều và không so sánh được
    # với bất kỳ báo cáo ngành nào. 2 ngày, mỗi ngày 1 user khác nhau -> DAU trung bình = 1.
    rows = [
        iap(2_000_000, user="u1", day="2026-08-24"),
        iap(2_000_000, user="u2", day="2026-08-25"),
    ]
    r = metrics.compute(rows)
    assert r["arpdau"]["avg_dau"] == pytest.approx(1.0)
    assert r["arpdau"]["days_covered"] == 2
    assert r["arpdau"]["value_usd"] == pytest.approx(2.0)


def test_khong_co_du_lieu_thi_khong_chia_cho_khong():
    r = metrics.compute([])
    assert r["arpdau"]["value_usd"] == 0.0
    assert r["arpdau"]["avg_dau"] == 0.0


# ═══ Doanh thu theo kênh ═══

def test_tach_doanh_thu_theo_kenh():
    # Biên lợi nhuận store vs web-shop chênh rất lớn (doc 18) — gộp là mất thông tin quan
    # trọng nhất về payment routing.
    rows = [iap(1_000_000, channel="PLAY_STORE"), iap(3_000_000, channel="WEB_SHOP"), ad(5.0)]
    ch = metrics.compute(rows)["revenue"]["by_channel_usd"]
    assert ch["PLAY_STORE"] == pytest.approx(1.0)
    assert ch["WEB_SHOP"] == pytest.approx(3.0)
    assert ch["AD"] == pytest.approx(5.0)


# ═══ Subscription ═══

def test_subscription_noi_ro_gioi_han_ngay_trong_payload():
    # SDK chỉ ghi lượt MUA, không theo dõi expiry — nên không suy ra được số sub active.
    # Giới hạn này phải nằm trong payload, không phải giấu xuống tài liệu.
    r = metrics.compute([iap(9_990_000, sub=1, user="u1"), iap(9_990_000, sub=1, user="u2")])
    assert r["subscription"]["purchase_events"] == 2
    assert r["subscription"]["distinct_users"] == 2
    assert "KHONG phai so subscription dang active" in r["subscription"]["note"]


# ═══ Bối cảnh chính sách (không phải North Star) ═══

def test_boi_canh_chinh_sach_tach_khoi_bon_chi_so():
    # doc 40: guardrail nổ nhiều là tín hiệu sức khoẻ CHÍNH SÁCH, không phải trải nghiệm
    # người chơi. Gộp vào comfort làm cả hai mất ý nghĩa.
    rows = [
        row(event_name="brain_decision", is_holdout=0, guardrails_applied="AD_PRESSURE_COOLDOWN"),
        row(event_name="brain_decision", is_holdout=1, guardrails_applied=""),
    ]
    pc = metrics.compute(rows)["policy_context"]
    assert pc["decisions"] == 2
    assert pc["holdout_share"] == pytest.approx(0.5)
    assert pc["guardrail_hits"]["AD_PRESSURE_COOLDOWN"] == 1
