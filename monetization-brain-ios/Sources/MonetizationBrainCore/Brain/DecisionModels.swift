/// Result of `AdsBrain.chooseCrossModal(...)`.
public struct CrossModalDecision: Sendable, Equatable {
    public let action: ActionMode
    public let isHoldout: Bool

    public init(action: ActionMode, isHoldout: Bool) {
        self.action = action
        self.isHoldout = isHoldout
    }
}

/// Result of `AdsBrain.chooseAssist(...)`.
public struct AssistDecision: Sendable, Equatable {
    public let action: ActionMode
    public let isHoldout: Bool

    public init(action: ActionMode, isHoldout: Bool) {
        self.action = action
        self.isHoldout = isHoldout
    }
}
