import XCTest
@testable import MonetizationBrainCore

final class RewardModelTests: XCTestCase {
    override func tearDown() {
        RewardModel.reset()
        super.tearDown()
    }

    func test_adShownPlayerStayed_isHighestReward() {
        XCTAssertEqual(RewardModel.estimate(actionHadValue: true, playerContinued: true), 1.0, accuracy: 1e-9)
    }

    func test_notShownPlayerStayed_isSafeButLowerThanShown() {
        XCTAssertEqual(RewardModel.estimate(actionHadValue: false, playerContinued: true), 0.6, accuracy: 1e-9)
    }

    func test_adShownPlayerLeft_isWorseThanNotShownPlayerStayed() {
        let shownLeft = RewardModel.estimate(actionHadValue: true, playerContinued: false)
        let notShownStayed = RewardModel.estimate(actionHadValue: false, playerContinued: true)
        XCTAssertEqual(shownLeft, 0.4, accuracy: 1e-9)
        XCTAssertLessThan(shownLeft, notShownStayed)
    }

    func test_notShownPlayerLeft_isLowestReward() {
        XCTAssertEqual(RewardModel.estimate(actionHadValue: false, playerContinued: false), 0.0, accuracy: 1e-9)
    }

    func test_unknownContinuationIsNeutralNotGuessed() {
        let unknown = RewardModel.estimate(actionHadValue: false, playerContinued: nil)
        XCTAssertEqual(unknown, 0.3, accuracy: 1e-9) // weightContinuation / 2 = 0.3
    }

    func test_resetRestoresDefaultWeights() {
        RewardModel.weightImmediateValue = 0.9
        RewardModel.weightContinuation = 0.9
        RewardModel.reset()
        XCTAssertEqual(RewardModel.weightImmediateValue, 0.4)
        XCTAssertEqual(RewardModel.weightContinuation, 0.6)
    }
}
