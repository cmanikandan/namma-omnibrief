import SwiftUI

public struct ArchiveView: View {
    @ObservedObject var viewModel: MainViewModel

    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                headerCard

                if viewModel.archivedBriefs.isEmpty {
                    emptyState
                } else {
                    ForEach(viewModel.archivedBriefs) { brief in
                        briefCard(brief)
                    }
                }
            }
            .padding(16)
        }
        .background(OmniTheme.paperBackground.ignoresSafeArea())
    }

    private var headerCard: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("LOCAL ARCHIVE • FIFO ROLLOVER")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Text("Recent Briefings (\(viewModel.archivedBriefs.count)/10)")
                    .omniScaledFont(size: 19, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                Text("Stored strictly on-device. Oldest entries roll over automatically past 10.")
                    .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
            }
            Spacer()
            if !viewModel.archivedBriefs.isEmpty {
                Button("Clear All") {
                    viewModel.clearArchive()
                }
                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.errorCrimson)
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "tray")
                .font(.system(size: 32))
                .foregroundColor(OmniTheme.mutedSlate)
            Text("No saved briefs yet")
                .omniScaledFont(size: 15, weight: .semibold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)
            Text("Analyse a newspaper article or tap 'Sample FT' in the X Drafter tab to populate your local archive.")
                .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.mutedSlate)
                .multilineTextAlignment(.center)
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private func briefCard(_ brief: BriefItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(brief.sourceOrSpeaker.isEmpty ? "Article Brief" : brief.sourceOrSpeaker)
                    .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(OmniTheme.subtleSurface)
                    .cornerRadius(6)

                Spacer()

                Text(brief.timestamp.formatted(date: .abbreviated, time: .shortened))
                    .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
            }

            Text(brief.title)
                .omniScaledFont(size: 15, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)

            Text(brief.content)
                .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)
                .lineLimit(5)

            HStack(spacing: 12) {
                Button {
                    viewModel.loadArchivedDraft(brief)
                } label: {
                    Label("Reopen in Drafter", systemImage: "square.and.pencil")
                        .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                        .cornerRadius(8)
                }

                ShareLink(item: "# \(brief.title)\n\n\(brief.content)") {
                    Label("Share / Mail", systemImage: "square.and.arrow.up")
                        .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.deepInk)
                        .cornerRadius(8)
                }

                Spacer()

                Button {
                    viewModel.deleteArchivedBrief(id: brief.id)
                } label: {
                    Image(systemName: "trash")
                        .foregroundColor(OmniTheme.errorCrimson)
                }
            }
        }
        .padding(14)
        .background(OmniTheme.cardSurface)
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }
}
