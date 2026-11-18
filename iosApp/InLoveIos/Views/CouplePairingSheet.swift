import InLoveIosCore
import SwiftUI

public struct CouplePairingSheet: View {
    @EnvironmentObject private var env: AppEnvironment
    @Environment(\.dismiss) private var dismiss

    @State private var codeInput: String = ""
    @State private var copiedCode = false
    @State private var demoAccountName: String = ""
    @State private var showFinalUnpairConfirm = false

    public init() {}

    private var state: CouplePairingState { env.pairingState }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    statusHeaderCard
                    if !state.isSignedIn {
                        signInCard
                    }
                    ownInviteCodeCard
                    phaseActionSection
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(env.isEnglish ? "Couple Pairing" : "Kết Nối Đôi Bạn")
                        .font(InLoveTypography.serifHeadline)
                        .foregroundStyle(InLoveColors.deepPlum)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(env.isEnglish ? "Done" : "Xong") { dismiss() }
                        .foregroundStyle(InLoveColors.rose)
                        .inLoveTapTarget()
                }
            }
            .alert(
                env.isEnglish ? "Confirm disconnecting couple?" : "Xác nhận hủy ghép đôi?",
                isPresented: $showFinalUnpairConfirm
            ) {
                Button(env.isEnglish ? "Keep connected" : "Giữ kết nối", role: .cancel) {}
                Button(env.isEnglish ? "Disconnect now" : "Hủy ghép đôi", role: .destructive) {
                    env.completeUnpairWorkflow()
                }
            } message: {
                Text(
                    env.isEnglish
                        ? "Your personal notes and memories stay safely on this phone."
                        : "Hồ sơ kỷ niệm trên máy bạn vẫn được giữ nguyên."
                )
            }
        }
    }

    private var statusHeaderCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(state.phase.statusLabel(isEnglish: env.isEnglish))
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                Circle()
                    .fill(statusDotColor)
                    .frame(width: 10, height: 10)
            }

            HStack(spacing: 12) {
                Label(
                    state.isNetworkReachable
                        ? (env.isEnglish ? "Online" : "Đang trực tuyến")
                        : (env.isEnglish ? "Offline mode" : "Đang ngoại tuyến"),
                    systemImage: state.isNetworkReachable ? "wifi" : "wifi.slash"
                )
                .font(.caption.weight(.medium))
                .foregroundStyle(InLoveColors.subtlePlum)

                if state.pendingSyncCount > 0 {
                    Text(
                        env.isEnglish
                            ? "\(state.pendingSyncCount) pending changes"
                            : "\(state.pendingSyncCount) thay đổi chờ đồng bộ"
                    )
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(InLoveColors.warningAmber)
                }
            }

            if let note = state.statusMessage, !note.isEmpty {
                Text(note)
                    .font(.caption)
                    .foregroundStyle(InLoveColors.subtlePlum)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }

    private var signInCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "Sign in for cloud backup & live pairing" : "Đăng nhập để sao lưu & ghép đôi trực tuyến")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            TextField(
                env.isEnglish ? "Your display name" : "Tên hiển thị của bạn",
                text: $demoAccountName
            )
            .textFieldStyle(.roundedBorder)
            .frame(minHeight: InLoveMetrics.minTapTarget)

            Button(env.isEnglish ? "Continue with Couple Account" : "Đăng nhập tài khoản đôi bạn") {
                let name = demoAccountName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? env.partner1Name
                    : demoAccountName.trimmingCharacters(in: .whitespacesAndNewlines)
                env.signInOnlineAccount(displayName: name)
            }
            .buttonStyle(InLovePrimaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var ownInviteCodeCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "Your couple invite code" : "Mã mời của bạn")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            HStack(spacing: 8) {
                Text(state.ownCoupleCode)
                    .font(InLoveTypography.metricNumber)
                    .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                Button {
                    #if os(iOS)
                    UIPasteboard.general.string = state.ownCoupleCode
                    #endif
                    copiedCode = true
                } label: {
                    Label(
                        copiedCode ? (env.isEnglish ? "Copied" : "Đã chép") : (env.isEnglish ? "Copy" : "Chép"),
                        systemImage: copiedCode ? "checkmark" : "doc.on.doc"
                    )
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()

                if let shareUrl = URL(string: CoupleCodeParser.buildInviteLink(code: state.ownCoupleCode)) {
                    ShareLink(item: shareUrl) {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundStyle(InLoveColors.rose)
                            .frame(minWidth: InLoveMetrics.minTapTarget, minHeight: InLoveMetrics.minTapTarget)
                    }
                }
            }
        }
        .inLoveCard()
    }

    @ViewBuilder
    private var phaseActionSection: some View {
        switch state.phase {
        case .guestLocal, .signedInSolo:
            sendInviteCard
        case .inviteOutgoing:
            outgoingInviteCard
        case .inviteIncoming:
            incomingInviteCard
        case .coupledSynced, .coupledOfflineQueue:
            coupledStatusCard
        case .breakupPending:
            breakupPendingCard
        }
    }

    private var sendInviteCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "Pair with your partner" : "Nhập mã mời của người ấy")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)

            TextField(
                env.isEnglish ? "Paste partner code or invite link" : "Dán mã mời hoặc liên kết đôi bạn",
                text: $codeInput
            )
            .textFieldStyle(.roundedBorder)
            .frame(minHeight: InLoveMetrics.minTapTarget)

            Button(env.isEnglish ? "Send pairing invite" : "Gửi lời mời kết đôi") {
                if env.sendPairingInvite(rawInput: codeInput) {
                    codeInput = ""
                }
            }
            .buttonStyle(InLovePrimaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var outgoingInviteCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "Waiting for partner confirmation" : "Đang chờ người ấy xác nhận")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            if let target = state.outgoingInvite?.toCode {
                Text(
                    env.isEnglish
                        ? "Sent invite to code \(target)."
                        : "Đã gửi lời mời tới mã \(target)."
                )
                .font(.subheadline)
                .foregroundStyle(InLoveColors.deepPlum)
            }

            Button(env.isEnglish ? "Cancel sent invite" : "Hủy lời mời đã gửi") {
                env.cancelOutgoingPairingInvite()
            }
            .buttonStyle(InLoveSecondaryButtonStyle())
            .inLoveTapTarget()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }

    private var incomingInviteCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Incoming invite" : "Lời mời ghép đôi")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            if let invite = state.incomingInvite {
                Text(
                    env.isEnglish
                        ? "\(invite.fromName) (\(invite.fromCode)) wants to connect."
                        : "\(invite.fromName) (\(invite.fromCode)) muốn kết nối đôi bạn với bạn."
                )
                .font(.subheadline)
                .foregroundStyle(InLoveColors.deepPlum)
            }

            HStack(spacing: 10) {
                Button(env.isEnglish ? "Accept" : "Chấp nhận") {
                    env.acceptIncomingPairingInvite()
                }
                .buttonStyle(InLovePrimaryButtonStyle())
                .inLoveTapTarget()

                Button(env.isEnglish ? "Decline" : "Từ chối") {
                    env.declineIncomingPairingInvite()
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()
            }
        }
        .inLoveCard()
    }

    private var coupledStatusCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(
                env.isEnglish
                    ? "Connected with \(state.partnerName ?? state.partnerCode ?? "Partner")"
                    : "Đã ghép đôi với \(state.partnerName ?? state.partnerCode ?? "Người ấy")",
                systemImage: "heart.circle.fill"
            )
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(InLoveColors.emeraldCheck)

            HStack(spacing: 10) {
                Button(env.isEnglish ? "Sync now" : "Đồng bộ ngay") {
                    Task { await env.triggerManualReconnectSync() }
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()

                Button(env.isEnglish ? "Request unpair" : "Hủy ghép đôi") {
                    env.requestTwoWayUnpair()
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.urgentCrimson)
                .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                .background(
                    RoundedRectangle(cornerRadius: InLoveMetrics.buttonCornerRadius, style: .continuous)
                        .fill(InLoveColors.surfaceContainerLow)
                )
                .inLoveTapTarget()
            }

            if state.isSignedIn {
                Button(env.isEnglish ? "Sign out" : "Đăng xuất tài khoản") {
                    env.signOutOnlineAccount()
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(InLoveColors.subtlePlum)
                .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                .inLoveTapTarget()
            }
        }
        .inLoveCard()
    }

    private var breakupPendingCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Two-way unpair confirmation" : "Xác nhận hủy ghép đôi 2 chiều")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.urgentCrimson)

            Text(
                env.isEnglish
                    ? "Confirm disconnecting or cancel to keep your shared couple link active."
                    : "Xác nhận ngắt kết nối hoặc hủy yêu cầu để tiếp tục đồng hành cùng nhau."
            )
            .font(.caption)
            .foregroundStyle(InLoveColors.subtlePlum)

            HStack(spacing: 10) {
                Button(env.isEnglish ? "Keep pairing" : "Giữ kết nối") {
                    env.cancelTwoWayUnpair()
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()

                Button(env.isEnglish ? "Confirm unpair" : "Xác nhận hủy") {
                    showFinalUnpairConfirm = true
                }
                .buttonStyle(InLovePrimaryButtonStyle())
                .inLoveTapTarget()
            }
        }
        .inLoveCard(background: InLoveColors.vipGoldContainer, borderColor: InLoveColors.vipGoldBorder)
    }

    private var statusDotColor: Color {
        switch state.phase {
        case .coupledSynced:
            return InLoveColors.emeraldCheck
        case .coupledOfflineQueue, .inviteOutgoing, .inviteIncoming:
            return InLoveColors.warningAmber
        case .breakupPending:
            return InLoveColors.urgentCrimson
        case .guestLocal, .signedInSolo:
            return InLoveColors.rose
        }
    }
}
