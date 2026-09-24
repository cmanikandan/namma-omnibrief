import Combine
import SwiftUI

public struct TodayHeadlinesView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.openURL) private var openURL
    @State private var nowEpochSeconds: Int64 = Int64(Date().timeIntervalSince1970)

    private let ageTicker = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                headerCard
                sortBar

                if let error = viewModel.headlinesError {
                    errorCard(error)
                }

                if viewModel.isLoadingHeadlines && viewModel.headlines.isEmpty {
                    ForEach(0..<4, id: \.self) { _ in
                        shimmerPlaceholder
                    }
                } else {
                    ForEach(Array(viewModel.sortedHeadlines.enumerated()), id: \.element.id) { index, item in
                        headlineCard(rank: index + 1, item: item)
                    }
                }
            }
            .padding(16)
        }
        .background(OmniTheme.paperBackground.ignoresSafeArea())
        .refreshable {
            await viewModel.refreshHeadlines()
        }
        .onReceive(ageTicker) { date in
            nowEpochSeconds = Int64(date.timeIntervalSince1970)
        }
    }

    private var headerCard: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("TODAY'S TOP 10 • INTEREST-RANKED")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Text("Hacker News Signal")
                    .omniScaledFont(size: 20, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                Text("GenAI • Gemini • OpenAI • Anthropic • India Tech")
                    .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
                if let updated = viewModel.headlinesLastUpdated {
                    Text("Updated \(updated)")
                        .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                }
            }
            Spacer()
            Button {
                Task { await viewModel.refreshHeadlines() }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .padding(10)
                    .background(OmniTheme.subtleSurface)
                    .clipShape(Circle())
            }
            .disabled(viewModel.isLoadingHeadlines)
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(OmniTheme.borderWarm, lineWidth: 1)
        )
    }

    private var sortBar: some View {
        HStack(spacing: 10) {
            ForEach(HeadlineSort.allCases) { sort in
                let isSelected = (viewModel.headlineSort == sort)
                Button {
                    viewModel.setHeadlineSort(sort)
                } label: {
                    Text(sort.label)
                        .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(isSelected ? OmniTheme.primaryTerracotta : OmniTheme.cardSurface)
                        .foregroundColor(isSelected ? .white : OmniTheme.deepInk)
                        .cornerRadius(20)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(isSelected ? OmniTheme.primaryTerracotta : OmniTheme.borderWarm, lineWidth: 1)
                        )
                }
            }
            Spacer()
        }
    }

    private func headlineCard(rank: Int, item: HeadlineItem) -> some View {
        let ageLabel = item.relativeAge(nowSeconds: nowEpochSeconds)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("#\(rank)")
                    .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(OmniTheme.primaryTerracotta)
                    .cornerRadius(6)

                ForEach(item.matchedInterests, id: \.self) { tag in
                    Text(tag)
                        .omniScaledFont(size: 11, weight: .semibold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(OmniTheme.subtleSurface)
                        .cornerRadius(6)
                }

                Spacer()

                if !ageLabel.isEmpty {
                    Text(ageLabel)
                        .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                }
            }

            Button {
                if let url = URL(string: item.openUrl) {
                    openURL(url)
                }
            } label: {
                Text(item.title)
                    .omniScaledFont(size: 15, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                    .multilineTextAlignment(.leading)
            }

            HStack {
                let metaParts = [
                    item.domain.isEmpty ? "news.ycombinator.com" : item.domain,
                    "\(item.points) pts",
                    "\(item.commentCount) comments"
                ]
                Text(metaParts.joined(separator: " • "))
                    .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)

                Spacer()

                Button {
                    viewModel.draftPostFromHeadline(item)
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "square.and.pencil")
                        Text("Draft")
                    }
                    .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(OmniTheme.subtleSurface)
                    .cornerRadius(8)
                }
            }
        }
        .padding(14)
        .background(OmniTheme.cardSurface)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(OmniTheme.borderWarm, lineWidth: 1)
        )
    }

    private var shimmerPlaceholder: some View {
        VStack(alignment: .leading, spacing: 10) {
            RoundedRectangle(cornerRadius: 4)
                .fill(OmniTheme.subtleSurface)
                .frame(width: 120, height: 14)
            RoundedRectangle(cornerRadius: 4)
                .fill(OmniTheme.subtleSurface)
                .frame(height: 18)
            RoundedRectangle(cornerRadius: 4)
                .fill(OmniTheme.subtleSurface)
                .frame(width: 220, height: 14)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(OmniTheme.cardSurface)
        .cornerRadius(12)
    }

    private func errorCard(_ message: String) -> some View {
        HStack {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(OmniTheme.errorCrimson)
            Text(message)
                .omniScaledFont(size: 13, weight: .medium, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.errorCrimson)
            Spacer()
        }
        .padding(12)
        .background(OmniTheme.cardSurface)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(OmniTheme.errorCrimson.opacity(0.4), lineWidth: 1)
        )
    }
}
