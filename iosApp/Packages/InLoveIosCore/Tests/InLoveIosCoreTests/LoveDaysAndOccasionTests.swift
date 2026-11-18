import XCTest
@testable import InLoveIosCore

final class LoveDaysAndOccasionTests: XCTestCase {
    private let calculator = LoveDaysCalculator(timeZone: TimeZone(identifier: "Asia/Ho_Chi_Minh")!)
    private lazy var engine = OccasionEngine(calculator: calculator)

    func testDayOneEqualsStartDateAndDayNIsStartPlusNMinusOne() throws {
        let start = try XCTUnwrap(calculator.parseDate("2025-01-01"))
        XCTAssertEqual(calculator.loveDays(from: start, to: start), 1)

        let day100Date = calculator.dateForMilestone(days: 100, from: start)
        XCTAssertEqual(calculator.formatIso(day100Date), "2025-04-10")
        XCTAssertEqual(calculator.loveDays(from: start, to: day100Date), 100)

        let day365Date = calculator.dateForMilestone(days: 365, from: start)
        XCTAssertEqual(calculator.formatIso(day365Date), "2025-12-31")
        XCTAssertEqual(calculator.loveDays(from: start, to: day365Date), 365)

        let futureStart = try XCTUnwrap(calculator.parseDate("2026-01-01"))
        XCTAssertEqual(calculator.loveDays(from: futureStart, to: start), 0)
    }

    func testMilestoneListIncludesSpecialRomanticNumbersAndClampedMonthiversaries() throws {
        XCTAssertEqual(
            LoveDaysCalculator.milestoneDays,
            [7, 14, 22, 30, 50, 52, 99, 100, 200, 300, 365, 500, 520, 730, 1000, 1095, 1314, 1825, 2000, 3650]
        )
        let start = try XCTUnwrap(calculator.parseDate("2025-01-31"))
        let ref = try XCTUnwrap(calculator.parseDate("2025-02-15"))
        let milestones = calculator.upcomingMilestones(startDate: start, referenceDate: ref, includeMonthiversaries: true)
        let firstMonth = try XCTUnwrap(milestones.first(where: { $0.isMonthiversary && $0.value == 1 }))
        XCTAssertEqual(firstMonth.targetDateIso, "2025-02-28")
    }

    func testLunarCalendar2027RamThangGiengAndAllHolidays2025To2040() {
        XCTAssertEqual(LunarCalendar2025To2040.solarIsoDate(for: .ramThangGieng, year: 2027), "2027-02-20")
        XCTAssertEqual(LunarCalendar2025To2040.solarIsoDate(for: .tet, year: 2027), "2027-02-06")
        XCTAssertEqual(LunarCalendar2025To2040.solarIsoDate(for: .thatTich, year: 2027), "2027-08-08")
        XCTAssertEqual(LunarCalendar2025To2040.solarIsoDate(for: .trungThu, year: 2040), "2040-09-20")

        for year in 2025...2040 {
            for holiday in LunarHolidayId.allCases {
                XCTAssertNotNil(
                    LunarCalendar2025To2040.solarIsoDate(for: holiday, year: year),
                    "Missing \(holiday.rawValue) for \(year)"
                )
            }
        }
    }

    func testOccasionReadinessStagesAcrossLeadTimes() throws {
        let valentine = try XCTUnwrap(OccasionEngine.coupleOccasionProfiles.first { $0.id == "valentine" })
        let targetDate = try XCTUnwrap(calculator.parseDate("2027-02-14"))

        XCTAssertEqual(engine.preparationStage(daysRemaining: 0, profile: valentine), .todayCelebration)
        XCTAssertEqual(engine.preparationStage(daysRemaining: 2, profile: valentine), .urgentLastMinute)
        XCTAssertEqual(engine.preparationStage(daysRemaining: 6, profile: valentine), .actionNeeded)
        XCTAssertEqual(engine.preparationStage(daysRemaining: 14, profile: valentine), .planAheadBooking)
        XCTAssertEqual(engine.preparationStage(daysRemaining: 30, profile: valentine), .relaxed)

        let readiness = engine.buildReadiness(profile: valentine, date: targetDate, daysRemaining: 14)
        XCTAssertEqual(readiness.stage, .planAheadBooking)
        XCTAssertEqual(readiness.targetDateIso, "2027-02-14")
        XCTAssertTrue(readiness.bookingAdvised)
    }

    func testRamThangGiengProfileAndNextCoupleOccasion() throws {
        let ramProfile = try XCTUnwrap(OccasionEngine.coupleOccasionProfiles.first { $0.id == "ram_thang_gieng" })
        XCTAssertEqual(ramProfile.coupleRelevance, .none)
        XCTAssertFalse(ramProfile.defaultReminderOn)

        let ram2027Date = try XCTUnwrap(calculator.parseDate(
            try XCTUnwrap(LunarCalendar2025To2040.solarIsoDate(for: .ramThangGieng, year: 2027))
        ))
        let ramReadiness = engine.buildReadiness(profile: ramProfile, date: ram2027Date, daysRemaining: 5)
        XCTAssertEqual(ramReadiness.targetDateIso, "2027-02-20")
        XCTAssertEqual(ramReadiness.stage, .actionNeeded)

        let refBeforeRam2027 = try XCTUnwrap(calculator.parseDate("2027-02-15"))
        let nextCouple = engine.nextOccasionReadiness(referenceDate: refBeforeRam2027, startDate: nil, region: "VN")
        XCTAssertNotEqual(nextCouple?.occasionId, "ram_thang_gieng")
    }
}
