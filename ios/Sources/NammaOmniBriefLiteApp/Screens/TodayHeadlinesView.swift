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
                subSectionPillBar

                switch viewModel.todaySubSection {
                case .headlines:
                    headlinesSection
                case .lumaEvents:
                    lumaEventsSection
                case .arxivPaper:
                    arxivPaperSection
                }
            }
            .padding(16)
        }
        .background(OmniTheme.paperBackground.ignoresSafeArea())
        .refreshable {
            switch viewModel.todaySubSection {
            case .headlines:
                await viewModel.refreshHeadlines()
            case .lumaEvents:
                await viewModel.fetchLumaBengaluruEvents(forceRefresh: true)
            case .arxivPaper:
                await viewModel.loadDailyArxivPaper(forceRefresh: true)
            }
        }
        .onReceive(ageTicker) { date in
            nowEpochSeconds = Int64(date.timeIntervalSince1970)
        }
        .onChange(of: viewModel.todaySubSection) { _, newSection in
            Task {
                if newSection == .lumaEvents && viewModel.lumaEvents.isEmpty {
                    await viewModel.fetchLumaBengaluruEvents()
                } else if newSection == .arxivPaper && viewModel.dailyArxivPaper == nil {
                    await viewModel.loadDailyArxivPaper()
                }
            }
        }
    }

    // MARK: - Sub-section Pill Bar

    private var subSectionPillBar: some View {
        HStack(spacing: 8) {
            ForEach(TodaySubSection.allCases) { section in
                let isSelected = (viewModel.todaySubSection == section)
                Button {
                    viewModel.todaySubSection = section
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: section.icon)
                            .font(.system(size: 11, weight: .bold))
                        Text(section.rawValue)
                            .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                    }
                    .padding(.horizontal, 12)
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

    // MARK: - 1. Hacker News Section

    @ViewBuilder
    private var headlinesSection: some View {
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
                    Text("Auto-refreshed hourly • Updated \(updated)")
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

    // MARK: - 2. Bengaluru Lu.ma Tech & AI Events Section

    @ViewBuilder
    private var lumaEventsSection: some View {
        lumaHeaderCard

        if let error = viewModel.lumaEventsError {
            errorCard(error)
        }

        if viewModel.isLoadingLumaEvents && viewModel.lumaEvents.isEmpty {
            ForEach(0..<3, id: \.self) { _ in
                shimmerPlaceholder
            }
        } else {
            ForEach(viewModel.lumaEvents) { event in
                lumaEventCard(event)
            }
        }
    }

    private var lumaHeaderCard: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("BENGALURU TECH & AI • LU.MA")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Text("Upcoming Tech Meetups")
                    .omniScaledFont(size: 20, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                Text("Curated AI demo nights, agent hackathons & founder summits in Bengaluru.")
                    .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
                if let updated = viewModel.lumaEventsLastUpdated {
                    Text("Refreshed 2-3x daily • Updated \(updated)")
                        .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                }
            }
            Spacer()
            Button {
                Task { await viewModel.fetchLumaBengaluruEvents(forceRefresh: true) }
            } label: {
                if viewModel.isLoadingLumaEvents {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: OmniTheme.primaryTerracotta))
                        .padding(10)
                } else {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(OmniTheme.primaryTerracotta)
                        .padding(10)
                        .background(OmniTheme.subtleSurface)
                        .clipShape(Circle())
                }
            }
            .disabled(viewModel.isLoadingLumaEvents)
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(OmniTheme.borderWarm, lineWidth: 1)
        )
    }

    private func lumaEventCard(_ event: LumaBengaluruEvent) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                HStack(spacing: 4) {
                    Image(systemName: "calendar")
                        .font(.system(size: 10))
                    Text(event.date)
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(OmniTheme.primaryTerracotta)
                .foregroundColor(.white)
                .cornerRadius(6)

                Text(event.category)
                    .omniScaledFont(size: 11, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(OmniTheme.subtleSurface)
                    .cornerRadius(6)

                Spacer()

                if !event.time.isEmpty {
                    Text(event.time)
                        .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                }
            }

            Text(event.title)
                .omniScaledFont(size: 16, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)

            HStack(spacing: 5) {
                Image(systemName: "mappin.circle.fill")
                    .foregroundColor(OmniTheme.forestGreen)
                    .font(.system(size: 12))
                Text(event.venue)
                    .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)

                if !event.organizer.isEmpty {
                    Text("•  \(event.organizer)")
                        .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                        .lineLimit(1)
                }
            }

            if !event.description.isEmpty {
                Text(event.description)
                    .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk.opacity(0.85))
            }

            HStack {
                Spacer()
                Button {
                    if let url = URL(string: event.url) {
                        openURL(url)
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "arrow.up.right.square")
                        Text("Register on Lu.ma")
                    }
                    .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
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

    // MARK: - 3. Daily Trending arXiv GenAI Paper Section

    @ViewBuilder
    private var arxivPaperSection: some View {
        arxivHeaderCard

        if let error = viewModel.arxivPaperError {
            errorCard(error)
        }

        if viewModel.isLoadingArxivPaper && viewModel.dailyArxivPaper == nil {
            shimmerPlaceholder
        } else if let paper = viewModel.dailyArxivPaper {
            arxivPaperDetailCard(paper)
        }
    }

    private var arxivHeaderCard: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("DAILY TRENDING ARXIV GENAI")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                Text("AI Paper Simplified")
                    .omniScaledFont(size: 20, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                Text("Cutting-edge ML breakthroughs explained for tech leaders — zero dense math.")
                    .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
            }
            Spacer()
            HStack(spacing: 6) {
                Button {
                    Task { await viewModel.loadNextArxivPaper() }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 11))
                        Text("Next")
                            .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .cornerRadius(8)
                }
                .disabled(viewModel.isLoadingArxivPaper)

                Button {
                    Task { await viewModel.loadDailyArxivPaper(forceRefresh: true) }
                } label: {
                    if viewModel.isLoadingArxivPaper {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: OmniTheme.primaryTerracotta))
                            .padding(8)
                    } else {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(OmniTheme.primaryTerracotta)
                            .padding(10)
                            .background(OmniTheme.subtleSurface)
                            .clipShape(Circle())
                    }
                }
                .disabled(viewModel.isLoadingArxivPaper)
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(OmniTheme.borderWarm, lineWidth: 1)
        )
    }

    private func arxivPaperDetailCard(_ paper: ArxivGenAIPaper) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            // Badges
            HStack(spacing: 6) {
                Text("cs.AI • GenAI")
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(OmniTheme.primaryTerracotta)
                    .foregroundColor(.white)
                    .cornerRadius(6)

                if !paper.publishedDate.isEmpty {
                    Text(paper.publishedDate)
                        .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.mutedSlate)
                }

                Spacer()

                Button {
                    if let url = URL(string: paper.pdfUrl) {
                        openURL(url)
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "doc.fill")
                        Text("PDF")
                    }
                    .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .cornerRadius(6)
                }
            }

            // Title
            Text(paper.title)
                .omniScaledFont(size: 18, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)

            if !paper.authors.isEmpty {
                Text(paper.authors.joined(separator: ", "))
                    .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
            }

            // 🍌 NANO-BANANA INFOGRAPHIC
            nanoBananaInfographicView(paper.nanoBanana)

            Divider()

            // 1. Plain English Analogy
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: "lightbulb.fill")
                        .foregroundColor(OmniTheme.mysoreGold)
                    Text("PLAIN-ENGLISH CORE CONCEPT")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                }
                Text(paper.plainEnglishSummary)
                    .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                    .lineSpacing(3)
            }
            .padding(12)
            .background(OmniTheme.subtleSurface)
            .cornerRadius(10)

            // 2. The Core Problem
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundColor(OmniTheme.errorCrimson)
                    Text("THE BOTTLENECK IT SOLVES")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                }
                Text(paper.coreProblem)
                    .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk.opacity(0.85))
            }

            // 3. The Breakthrough
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles")
                        .foregroundColor(OmniTheme.primaryTerracotta)
                    Text("THE NOVEL BREAKTHROUGH")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                }
                Text(paper.coreBreakthrough)
                    .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk.opacity(0.85))
            }

            // 4. Engineering Implication
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "wrench.and.screwdriver.fill")
                        .foregroundColor(OmniTheme.forestGreen)
                    Text("REAL-WORLD ENGINEERING IMPLICATION")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                }
                Text(paper.engineeringImplication)
                    .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk.opacity(0.85))
            }

            // 5. 2-Minute CTO Takeaway
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: "timer")
                        .foregroundColor(OmniTheme.primaryTerracotta)
                    Text("THE 2-MINUTE CTO TAKEAWAY")
                        .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.deepInk)
                }
                Text(paper.twoMinuteTakeaway)
                    .omniScaledFont(size: 13, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
            }
            .padding(12)
            .background(OmniTheme.forestGreen.opacity(0.08))
            .cornerRadius(10)

            // X Post Status Feedback (if active)
            if let status = viewModel.arxivPostStatus {
                HStack {
                    Text(status)
                        .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                        .foregroundColor(status.contains("❌") ? OmniTheme.errorCrimson : (status.contains("✅") ? OmniTheme.forestGreen : OmniTheme.primaryTerracotta))
                    Spacer()
                    Button {
                        viewModel.arxivPostStatus = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(OmniTheme.mutedSlate)
                    }
                }
                .padding(10)
                .background(OmniTheme.subtleSurface)
                .cornerRadius(8)
            }

            // Action Buttons: X Posting & Paper Links
            VStack(spacing: 8) {
                HStack(spacing: 10) {
                    // 1. Draft in X Drafter
                    Button {
                        viewModel.draftPostFromArxivPaper(paper)
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "square.and.pencil")
                            Text("Draft in X Drafter")
                        }
                        .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.primaryTerracotta)
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(OmniTheme.primaryTerracotta.opacity(0.3), lineWidth: 1)
                        )
                    }

                    // 2. Direct Push to X via API
                    Button {
                        Task {
                            await viewModel.postArxivPaperToX(paper)
                        }
                    } label: {
                        HStack(spacing: 5) {
                            if viewModel.isPostingArxivToX {
                                ProgressView()
                                    .scaleEffect(0.8)
                                    .tint(.white)
                            } else {
                                Image(systemName: "paperplane.fill")
                            }
                            Text(viewModel.isPostingArxivToX ? "Pushing..." : "Push to X Now")
                        }
                        .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(OmniTheme.deepInk)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    }
                    .disabled(viewModel.isPostingArxivToX)
                }

                HStack(spacing: 10) {
                    Button {
                        if let url = URL(string: paper.arxivUrl) {
                            openURL(url)
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.up.right.square")
                            Text("arXiv Abstract")
                        }
                        .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(OmniTheme.subtleSurface)
                        .foregroundColor(OmniTheme.deepInk)
                        .cornerRadius(8)
                    }

                    Button {
                        if let url = URL(string: paper.pdfUrl) {
                            openURL(url)
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "doc.text.fill")
                            Text("Open Full PDF")
                        }
                        .omniScaledFont(size: 12, weight: .bold, scale: viewModel.fontScale)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(OmniTheme.primaryTerracotta)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    }
                }
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(OmniTheme.borderWarm, lineWidth: 1)
        )
    }

    private func nanoBananaInfographicView(_ nano: NanoBananaInfographic) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // Infographic Header Badge
            HStack(spacing: 6) {
                Text("🍌")
                    .font(.system(size: 16))
                Text("NANO-BANANA INFOGRAPHIC")
                    .omniScaledFont(size: 11, weight: .black, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
                Text("• ARCHITECTURE SNAPSHOT")
                    .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.mutedSlate)
                Spacer()
            }

            // Visual comparison grid
            VStack(spacing: 8) {
                // 1. The Bottleneck (Before)
                HStack(alignment: .top, spacing: 8) {
                    Text("🛑")
                        .font(.system(size: 14))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("LEGACY BOTTLENECK")
                            .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.errorCrimson)
                        Text(nano.beforeBottleneck)
                            .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.deepInk)
                    }
                }
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(OmniTheme.errorCrimson.opacity(0.06))
                .cornerRadius(8)

                // 2. The Mechanism (The Pivot)
                HStack(alignment: .top, spacing: 8) {
                    Text("⚡️")
                        .font(.system(size: 14))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("THE PIVOT MECHANISM")
                            .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.primaryTerracotta)
                        Text(nano.mechanism)
                            .omniScaledFont(size: 12, weight: .medium, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.deepInk)
                    }
                }
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(OmniTheme.primaryTerracotta.opacity(0.08))
                .cornerRadius(8)

                // 3. The Nano-Banana Benchmark (After)
                HStack(alignment: .top, spacing: 8) {
                    Text("🚀")
                        .font(.system(size: 14))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("NANO-SCALE BENCHMARK")
                            .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.forestGreen)
                        Text(nano.afterScale)
                            .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.deepInk)
                    }
                }
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(OmniTheme.forestGreen.opacity(0.08))
                .cornerRadius(8)
            }

            // Metric Efficiency Bars
            HStack(spacing: 12) {
                // Memory Saved
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Memory Saved")
                            .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.mutedSlate)
                        Spacer()
                        Text("-\(nano.memorySavingsPercent)%")
                            .omniScaledFont(size: 11, weight: .black, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.forestGreen)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(OmniTheme.borderWarm)
                                .frame(height: 6)
                            Capsule()
                                .fill(OmniTheme.forestGreen)
                                .frame(width: geo.size.width * CGFloat(nano.memorySavingsPercent) / 100.0, height: 6)
                        }
                    }
                    .frame(height: 6)
                }
                .frame(maxWidth: .infinity)

                // Speedup
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Throughput")
                            .omniScaledFont(size: 10, weight: .bold, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.mutedSlate)
                        Spacer()
                        Text(String(format: "%.1fx", nano.speedupFactor))
                            .omniScaledFont(size: 11, weight: .black, scale: viewModel.fontScale)
                            .foregroundColor(OmniTheme.primaryTerracotta)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(OmniTheme.borderWarm)
                                .frame(height: 6)
                            Capsule()
                                .fill(OmniTheme.primaryTerracotta)
                                .frame(width: min(geo.size.width, geo.size.width * CGFloat(nano.speedupFactor) / 8.0), height: 6)
                        }
                    }
                    .frame(height: 6)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.top, 4)

            // Banana Scale Comparison Callout
            HStack(spacing: 6) {
                Text("🍌")
                    .font(.system(size: 12))
                Text("Scale: \(nano.scaleComparison)")
                    .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk.opacity(0.8))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(OmniTheme.mysoreGold.opacity(0.15))
            .cornerRadius(6)
        }
        .padding(12)
        .background(OmniTheme.subtleSurface)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(OmniTheme.mysoreGold.opacity(0.4), lineWidth: 1)
        )
    }

    // MARK: - Helpers

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
