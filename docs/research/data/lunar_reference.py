# -*- coding: utf-8 -*-
"""Ho Ngoc Duc lunar algorithm (Meeus new moon + sun longitude). tz = UTC offset (7 VN, 8 CN, 9 KR)."""
import math, json

def jd_from_date(dd, mm, yy):
    a = (14 - mm) // 12; y = yy + 4800 - a; m = mm + 12 * a - 3
    jd = dd + (153 * m + 2) // 5 + 365 * y + y // 4 - y // 100 + y // 400 - 32045
    if jd < 2299161:
        jd = dd + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083
    return jd

def jd_to_date(jd):
    if jd > 2299160:
        a = jd + 32044; b = (4 * a + 3) // 146097; c = a - (b * 146097) // 4
    else:
        b = 0; c = jd + 32082
    d = (4 * c + 3) // 1461; e = c - (1461 * d) // 4; m = (5 * e + 2) // 153
    return (e - (153 * m + 2) // 5 + 1, m + 3 - 12 * (m // 10), b * 100 + d - 4800 + m // 10)

def new_moon(k):
    T = k / 1236.85; T2 = T * T; T3 = T2 * T; dr = math.pi / 180
    Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3
    Jd1 += 0.00033 * math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr)
    M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3
    Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3
    F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3
    C1 = (0.1734 - 0.000393 * T) * math.sin(M * dr) + 0.0021 * math.sin(2 * dr * M)
    C1 += -0.4068 * math.sin(Mpr * dr) + 0.0161 * math.sin(dr * 2 * Mpr) - 0.0004 * math.sin(dr * 3 * Mpr)
    C1 += 0.0104 * math.sin(dr * 2 * F) - 0.0051 * math.sin(dr * (M + Mpr)) - 0.0074 * math.sin(dr * (M - Mpr))
    C1 += 0.0004 * math.sin(dr * (2 * F + M)) - 0.0004 * math.sin(dr * (2 * F - M)) - 0.0006 * math.sin(dr * (2 * F + Mpr))
    C1 += 0.0010 * math.sin(dr * (2 * F - Mpr)) + 0.0005 * math.sin(dr * (2 * Mpr + M))
    if T < -11:
        deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
    else:
        deltat = -0.000278 + 0.000265 * T + 0.000262 * T2
    return Jd1 + C1 - deltat

def sun_longitude(jdn):
    T = (jdn - 2451545.0) / 36525; T2 = T * T; dr = math.pi / 180
    M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2
    L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2
    DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * math.sin(dr * M)
    DL += (0.019993 - 0.000101 * T) * math.sin(dr * 2 * M) + 0.000290 * math.sin(dr * 3 * M)
    L = (L0 + DL) * dr
    L -= math.pi * 2 * int(L / (math.pi * 2))
    return L

def sun_long_seg(dayNumber, tz):  # 0..11, 30-degree segment at local midnight
    return int(sun_longitude(dayNumber - 0.5 - tz / 24.0) / math.pi * 6)

def new_moon_day(k, tz):
    return int(new_moon(k) + 0.5 + tz / 24.0)

def lunar_month11(yy, tz):
    off = jd_from_date(31, 12, yy) - 2415021
    k = int(off / 29.530588853)
    nm = new_moon_day(k, tz)
    if sun_long_seg(nm, tz) >= 9:
        nm = new_moon_day(k - 1, tz)
    return nm

def leap_offset(a11, tz):
    k = int((a11 - 2415021.076998695) / 29.530588853 + 0.5)
    i = 1
    arc = sun_long_seg(new_moon_day(k + i, tz), tz)
    while True:
        last = arc; i += 1
        arc = sun_long_seg(new_moon_day(k + i, tz), tz)
        if not (arc != last and i < 14):
            break
    return i - 1

def solar_to_lunar(dd, mm, yy, tz=7):
    """returns (lunarDay, lunarMonth, lunarYear, isLeap)"""
    dayNumber = jd_from_date(dd, mm, yy)
    k = int((dayNumber - 2415021.076998695) / 29.530588853)
    monthStart = new_moon_day(k + 1, tz)
    if monthStart > dayNumber:
        monthStart = new_moon_day(k, tz)
    a11 = lunar_month11(yy, tz); b11 = a11
    if a11 >= monthStart:
        lunarYear = yy; a11 = lunar_month11(yy - 1, tz)
    else:
        lunarYear = yy + 1; b11 = lunar_month11(yy + 1, tz)
    lunarDay = dayNumber - monthStart + 1
    diff = (monthStart - a11) // 29
    lunarLeap = 0; lunarMonth = diff + 11
    if b11 - a11 > 365:
        lo = leap_offset(a11, tz)
        if diff >= lo:
            lunarMonth = diff + 10
            if diff == lo: lunarLeap = 1
    if lunarMonth > 12: lunarMonth -= 12
    if lunarMonth >= 11 and diff < 4: lunarYear -= 1
    return (lunarDay, lunarMonth, lunarYear, lunarLeap)

def lunar_to_solar(ld, lm, ly, leap=0, tz=7):
    """returns (day, month, year) Gregorian or None"""
    if lm < 11:
        a11 = lunar_month11(ly - 1, tz); b11 = lunar_month11(ly, tz)
    else:
        a11 = lunar_month11(ly, tz); b11 = lunar_month11(ly + 1, tz)
    k = int(0.5 + (a11 - 2415021.076998695) / 29.530588853)
    off = lm - 11
    if off < 0: off += 12
    if b11 - a11 > 365:
        lo = leap_offset(a11, tz); leapMonth = lo - 2
        if leapMonth < 0: leapMonth += 12
        if leap != 0 and lm != leapMonth: return None
        elif leap != 0 or off >= lo: off += 1
    mstart = new_moon_day(k + off, tz)
    return jd_to_date(mstart + ld - 1)

def lunar_to_iso(ld, lm, ly, tz=7, leap=0):
    r = lunar_to_solar(ld, lm, ly, leap, tz)
    return "%04d-%02d-%02d" % (r[2], r[1], r[0])

def chap23(y, tz):
    """23/12 of lunar year y-1 (falls in Gregorian year y). Month 12 is never leap in 2025-2040."""
    return lunar_to_iso(23, 12, y - 1, tz)

VN_OCC = {"tet": (1, 1), "ram_thang_gieng": (15, 1), "gio_to": (10, 3), "doan_ngo": (5, 5),
          "that_tich": (7, 7), "vu_lan": (15, 7), "trung_thu": (15, 8)}
CN_OCC = {"lny": (1, 1), "lantern": (15, 1), "qixi": (7, 7), "mid_autumn": (15, 8), "double_ninth": (9, 9)}

def table(occ, tz, years):
    return {k: {str(y): lunar_to_iso(d, m, y, tz) for y in years} for k, (d, m) in occ.items()}

# (id, year, repo ISO) from VietnameseHolidays.kt, ground truth
REPO = [("tet",2025,"2025-01-29"),("tet",2026,"2026-02-17"),("tet",2027,"2027-02-06"),("tet",2028,"2028-01-26"),
 ("ram_thang_gieng",2026,"2026-03-03"),("ram_thang_gieng",2027,"2027-02-19"),
 ("gio_to",2025,"2025-04-07"),("gio_to",2026,"2026-04-26"),("gio_to",2027,"2027-04-16"),("gio_to",2028,"2028-04-04"),
 ("vu_lan",2025,"2025-09-06"),("vu_lan",2026,"2026-08-27"),
 ("trung_thu",2025,"2025-10-06"),("trung_thu",2026,"2026-09-25"),("trung_thu",2027,"2027-09-15")]

# Test vectors: (Gregorian d,m,y, tz) -> (lunar d,m,y,leap); all cross-checked vs HKO (tz8) or Vietnamese sites (tz7)
VECTORS = [((29,1,2025,7),(1,1,2025,0)), ((17,2,2026,7),(1,1,2026,0)), ((6,2,2027,7),(1,1,2027,0)),
           ((26,1,2028,7),(1,1,2028,0)), ((2,2,2030,7),(1,1,2030,0)), ((3,2,2030,8),(1,1,2030,0)),
           ((2,2,2030,8),(30,12,2029,0)), ((25,7,2025,7),(1,6,2025,1)), ((6,10,2025,7),(15,8,2025,0)),
           ((26,9,2034,7),(15,8,2034,0)), ((26,9,2034,8),(14,8,2034,0)), ((24,4,2037,7),(10,3,2037,0)),
           ((24,4,2037,8),(9,3,2037,0)), ((13,4,2038,7),(10,3,2038,0)), ((20,2,2027,7),(15,1,2027,0))]

if __name__ == "__main__":
    Y = range(2025, 2041)
    vn = table(VN_OCC, 7, Y); cn = table(CN_OCC, 8, Y)
    vn["chap23_ong_tao"] = {str(y): chap23(y, 7) for y in Y}
    cn["chap23"] = {str(y): chap23(y, 8) for y in Y}
    ok = 0
    for i, y, iso in REPO:
        got = vn[i][str(y)]; good = got == iso; ok += good
        print("REPO", i, y, iso, got, "OK" if good else "MISMATCH")
    print("repo match %d/%d" % (ok, len(REPO)))
    diff = []
    for y in Y:
        for k, (d, m) in VN_OCC.items():
            c = lunar_to_iso(d, m, y, 8)
            if vn[k][str(y)] != c: diff.append({"year": y, "occasion": k, "vn": vn[k][str(y)], "cn": c})
        for k, (d, m) in (("double_ninth", (9, 9)),):
            pass
        if vn["chap23_ong_tao"][str(y)] != cn["chap23"][str(y)]:
            diff.append({"year": y, "occasion": "chap23", "vn": vn["chap23_ong_tao"][str(y)], "cn": cn["chap23"][str(y)]})
    krdiff = []
    for y in Y:
        for k, (d, m) in VN_OCC.items():
            c = lunar_to_iso(d, m, y, 9)
            if c != vn[k][str(y)]: krdiff.append({"year": y, "occasion": k, "vn": vn[k][str(y)], "kr": c})
    # leap months per lunar year (VN / CN)
    def leaps(tz):
        r = {}
        for y in range(2024, 2041):
            a11 = lunar_month11(y - 1, tz); b11 = lunar_month11(y, tz)
            if b11 - a11 > 365:
                lo = leap_offset(a11, tz); lm = lo - 2
                r[y] = lm + 12 if lm < 0 else lm
        return r
    print("LEAPS VN", leaps(7)); print("LEAPS CN", leaps(8))
    print("DIFF", json.dumps(diff, ensure_ascii=False)); print("KRDIFF", json.dumps(krdiff))
    for k in vn: print(k, " ".join(vn[k][str(y)] for y in Y))
    for k in cn: print("CN", k, " ".join(cn[k][str(y)] for y in Y))
    good = 0
    for g, l in VECTORS:
        r = solar_to_lunar(*g[:3], tz=g[3]); good += (r == l)
        if r != l: print("VEC FAIL", g, l, r)
        # round trip lunar -> solar
        assert lunar_to_solar(l[0], l[1], l[2], l[3], g[3]) == (g[0], g[1], g[2]), (g, l)
    print("VECTORS pass %d/%d (+ round trip)" % (good, len(VECTORS)))
