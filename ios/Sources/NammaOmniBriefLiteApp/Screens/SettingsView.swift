import SwiftUI

public struct SettingsView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var bulkEnvInput: String = ""

    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                textSizeSection
                lockScreenSection
                geminiConfigSection
                xCredentialsSection
                bulkImportSection

                if let msg = viewModel.settingsStatusMessage {
                    Text(msg)
                        .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                        .foregroundColor(OmniTheme.forestGreen)
                        .padding(10)
                }
            }
            .padding(16)
        }
        .background(OmniTheme.paperBackground.ignoresSafeArea())
    }

    private var lockScreenSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("LOCK SCREEN & HOURLY SIGNAL")
                .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)

            Toggle("Show Top Story on iPhone Lock Screen", isOn: Binding(
                get: { viewModel.lockScreenHourlyEnabled },
                set: { viewModel.setLockScreenHourlyEnabled($0) }
            ))
            .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)

            Text("Pushes the #1 AI/tech headline to your iPhone Lock Screen automatically every hour so you can glance at breaking tech signal without unlocking.")
                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.mutedSlate)

            HStack(spacing: 6) {
                Image(systemName: "lock.shield.fill")
                    .foregroundColor(OmniTheme.forestGreen)
                    .font(.system(size: 12))
                Text("Lock Screen Widget: Long-press Lock Screen > Customise > Add YenGuru widget.")
                    .omniScaledFont(size: 11, weight: .medium, scale: viewModel.fontScale)
                    .foregroundColor(OmniTheme.deepInk)
            }
            .padding(10)
            .background(OmniTheme.subtleSurface)
            .cornerRadius(8)
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var textSizeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("APPEARANCE • TEXT SIZE")
                .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)

            Text("Scales every piece of type across the app immediately without restarting.")
                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.mutedSlate)

            ForEach(AppPreferencesStore.fontScaleOptions, id: \.scale) { option in
                let isSelected = abs(viewModel.fontScale - option.scale) < 0.02
                Button {
                    viewModel.setFontScale(option.scale)
                } label: {
                    HStack {
                        Text("\(option.label) (\(String(format: "%.2f×", option.scale)))")
                            .font(.system(size: round(14 * CGFloat(option.scale)), weight: .semibold))
                            .foregroundColor(isSelected ? .white : OmniTheme.deepInk)
                        Spacer()
                        if isSelected {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.white)
                        }
                    }
                    .padding(12)
                    .background(isSelected ? OmniTheme.primaryTerracotta : OmniTheme.subtleSurface)
                    .cornerRadius(10)
                }
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var geminiConfigSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("GEMINI AI CONFIGURATION")
                .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)

            SecureField("Gemini API Key (AIzaSy...)", text: $viewModel.geminiApiKey)
                .textFieldStyle(.roundedBorder)
                .omniScaledFont(size: 13, weight: .regular, scale: viewModel.fontScale)

            Text("Preferred Model (automatic fallback chain: 3.8 → 3.7 → 4-argon)")
                .omniScaledFont(size: 12, weight: .semibold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.deepInk)

            ForEach(AppPreferencesStore.availableModels, id: \.id) { model in
                let selected = (viewModel.geminiModel == model.id)
                Button {
                    viewModel.geminiModel = model.id
                    viewModel.saveSettings()
                } label: {
                    HStack {
                        Text(model.label)
                            .omniScaledFont(size: 13, weight: .medium, scale: viewModel.fontScale)
                            .foregroundColor(selected ? .white : OmniTheme.deepInk)
                        Spacer()
                        if selected {
                            Image(systemName: "checkmark")
                                .foregroundColor(.white)
                        }
                    }
                    .padding(10)
                    .background(selected ? OmniTheme.primaryTerracotta : OmniTheme.subtleSurface)
                    .cornerRadius(8)
                }
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var xCredentialsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("X (TWITTER) OAUTH 2.0 & BEHAVIOUR")
                .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)

            Toggle("X Premium / Blue (up to 25,000 chars)", isOn: $viewModel.isXBlue)
                .omniScaledFont(size: 13, weight: .medium, scale: viewModel.fontScale)
            Toggle("Attach source photo to X posts (requires media.write)", isOn: $viewModel.attachImageToPost)
                .omniScaledFont(size: 13, weight: .medium, scale: viewModel.fontScale)

            TextField("X OAuth 2.0 Client ID", text: $viewModel.xClientId)
                .textFieldStyle(.roundedBorder)
            SecureField("X OAuth 2.0 Client Secret", text: $viewModel.xClientSecret)
                .textFieldStyle(.roundedBorder)
            SecureField("X OAuth 2.0 Access Token", text: $viewModel.xAccessToken)
                .textFieldStyle(.roundedBorder)
            SecureField("X OAuth 2.0 Refresh Token", text: $viewModel.xRefreshToken)
                .textFieldStyle(.roundedBorder)

            Button {
                viewModel.saveSettings()
            } label: {
                Text("Save Keys On-Device")
                    .omniScaledFont(size: 14, weight: .bold, scale: viewModel.fontScale)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(OmniTheme.primaryTerracotta)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }

    private var bulkImportSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("BULK IMPORT (.ENV PASTE)")
                .omniScaledFont(size: 11, weight: .bold, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.primaryTerracotta)
            Text("Paste lines from your local .env or tools/x_oauth_setup.py output (e.g. GEMINI_API_KEY=..., X_CLIENT_ID=FAKE_CLIENT_ID_123):")
                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                .foregroundColor(OmniTheme.mutedSlate)

            TextEditor(text: $bulkEnvInput)
                .omniScaledFont(size: 12, weight: .regular, scale: viewModel.fontScale)
                .frame(minHeight: 80)
                .padding(6)
                .background(OmniTheme.paperBackground)
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(OmniTheme.borderWarm, lineWidth: 1))

            Button {
                viewModel.importBulkEnv(bulkEnvInput)
                bulkEnvInput = ""
            } label: {
                Text("Import .env Keys")
                    .omniScaledFont(size: 13, weight: .semibold, scale: viewModel.fontScale)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(OmniTheme.subtleSurface)
                    .foregroundColor(OmniTheme.primaryTerracotta)
                    .cornerRadius(8)
            }
        }
        .padding(16)
        .background(OmniTheme.cardSurface)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(OmniTheme.borderWarm, lineWidth: 1))
    }
}
