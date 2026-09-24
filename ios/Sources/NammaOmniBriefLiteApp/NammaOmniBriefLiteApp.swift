import SwiftUI

@main
public struct NammaOmniBriefLiteApp: App {
    @StateObject private var viewModel = MainViewModel()
    @Environment(\.scenePhase) private var scenePhase

    public init() {}

    public var body: some Scene {
        WindowGroup {
            VStack(spacing: 0) {
                topBar
                TabView(selection: $viewModel.selectedTab) {
                    TodayHeadlinesView(viewModel: viewModel)
                        .tabItem {
                            Label(LiteDestination.today.rawValue, systemImage: LiteDestination.today.systemIcon)
                        }
                        .tag(LiteDestination.today)

                    ArticleToXView(viewModel: viewModel)
                        .tabItem {
                            Label(LiteDestination.articleToX.rawValue, systemImage: LiteDestination.articleToX.systemIcon)
                        }
                        .tag(LiteDestination.articleToX)

                    ArchiveView(viewModel: viewModel)
                        .tabItem {
                            Label(LiteDestination.archive.rawValue, systemImage: LiteDestination.archive.systemIcon)
                        }
                        .tag(LiteDestination.archive)

                    SettingsView(viewModel: viewModel)
                        .tabItem {
                            Label(LiteDestination.settings.rawValue, systemImage: LiteDestination.settings.systemIcon)
                        }
                        .tag(LiteDestination.settings)
                }
                .tint(OmniTheme.primaryTerracotta)
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    viewModel.onAppResumed()
                }
            }
        }
    }

    private var topBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Namma Omnibrief")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(OmniTheme.deepInk)
                Text("BENGALURU EXECUTIVE BRIEF • iOS LITE")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(OmniTheme.primaryTerracotta)
            }
            Spacer()
            Text(viewModel.geminiModel.replacingOccurrences(of: "gemini-", with: "").uppercased())
                .font(.system(size: 10, weight: .bold))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(OmniTheme.subtleSurface)
                .foregroundColor(OmniTheme.deepInk)
                .cornerRadius(6)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(OmniTheme.cardSurface)
        .overlay(
            Rectangle()
                .frame(height: 1)
                .foregroundColor(OmniTheme.borderWarm),
            alignment: .bottom
        )
    }
}
