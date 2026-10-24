import SwiftUI

/// Keep navigation, form state and presented authorization requests alive during an outage.
struct ServiceAvailabilityBanner: View {
    @Environment(\.locale) private var localizationLocale
    @State private var retrying = false
    let retry: () async -> Void

    var body: some View {
        HStack(spacing: 12) {
            Label(L10n.text("当前服务不可用"), systemImage: "exclamationmark.icloud")
                .font(.subheadline).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            Button {
                guard !retrying else { return }
                retrying = true
                Task {
                    defer { retrying = false }
                    await retry()
                }
            } label: {
                if retrying { ProgressView() }
                else { Text(L10n.text("重试")) }
            }
            .frame(minWidth: 44, minHeight: 44)
            .disabled(retrying)
            .accessibilityLabel(L10n.text("重试"))
        }
        .padding(.horizontal, 20)
        .foregroundStyle(Brand.danger)
        .background(Brand.card)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("serviceAvailabilityBanner")
    }
}
