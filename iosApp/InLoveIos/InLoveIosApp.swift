import InLoveIosCore
import SwiftUI

@main
struct InLoveIosApp: App {
    @StateObject private var env = AppEnvironment()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MainTabRootView()
                .environmentObject(env)
                .preferredColorScheme(.light)
                .task {
                    await env.bootstrap()
                }
                .onOpenURL { url in
                    env.handleIncomingURL(url)
                }
                .onChange(of: scenePhase) { _, newPhase in
                    if newPhase == .active {
                        env.refreshComputedState()
                        Task { await env.monetizationBootstrap.refreshRemoteConfig() }
                    }
                }
        }
    }
}

private struct MainTabRootView: View {
    @EnvironmentObject private var env: AppEnvironment

    var body: some View {
        TabView(selection: $env.selectedTab) {
            LoveHomeView()
                .tabItem {
                    Label(env.isEnglish ? "Home" : "Trang Chủ", systemImage: "heart.fill")
                }
                .tag(InLoveTab.home)

            CalendarMilestonesView()
                .tabItem {
                    Label(env.isEnglish ? "Calendar" : "Lịch", systemImage: "calendar")
                }
                .tag(InLoveTab.calendar)

            GiftConciergeView()
                .tabItem {
                    Label(env.isEnglish ? "Gifts" : "Quà Tặng", systemImage: "gift.fill")
                }
                .tag(InLoveTab.gifts)

            MemoriesGridView()
                .tabItem {
                    Label(env.isEnglish ? "Memories" : "Kỷ Niệm", systemImage: "photo.on.rectangle.angled")
                }
                .tag(InLoveTab.memories)

            SettingsView()
                .tabItem {
                    Label(env.isEnglish ? "Settings" : "Cài Đặt", systemImage: "gearshape.fill")
                }
                .tag(InLoveTab.settings)
        }
        .tint(InLoveColors.rose)
        .sheet(isPresented: $env.showPaywall) {
            PaywallView()
                .environmentObject(env)
        }
        .sheet(isPresented: $env.showCouplePairingSheet) {
            CouplePairingSheet()
                .environmentObject(env)
        }
        .sheet(isPresented: $env.showGuideSheet) {
            InLoveGuideSheet()
                .environmentObject(env)
        }
        .overlay(alignment: .top) {
            if let message = env.toastBannerMessage {
                Text(message)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(InLoveColors.pearl)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(InLoveColors.deepPlum))
                    .padding(.top, 12)
                    .onTapGesture { env.toastBannerMessage = nil }
            }
        }
    }
}
