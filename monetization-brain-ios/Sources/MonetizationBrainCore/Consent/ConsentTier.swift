/// Consent tiers used to decide how much personalization the SDK is allowed to do.
///
/// `.unknown` is the most important case here. A single boolean cannot distinguish "the user
/// declined" from "nobody has asked yet" — collapsing those two forces a choice between two bad
/// outcomes: treat everyone as declined (a studio outside a consent-mandatory region loses
/// personalization the moment it upgrades the SDK), or treat everyone as granted (which does
/// exactly what consent regulation forbids). The SDK never invents a refusal nobody gave.
public enum ConsentTier: Sendable, Equatable {
    /// Consent has not been configured yet. Behavior stays as if consent were granted, but
    /// `AdsBrain.selfCheckReport()` warns about it.
    case unknown

    /// The user declined, or consent is required and hasn't been obtained.
    case denied

    /// Partially granted. Treated conservatively — see `ConsentState.allowsProfiling()` —
    /// because there is no per-feature masking yet.
    case partial

    /// Fully granted.
    case granted
}
