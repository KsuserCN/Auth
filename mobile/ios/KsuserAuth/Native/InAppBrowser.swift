import SafariServices
import SwiftUI

struct BrowserPage: Identifiable {
    let url: URL
    let id = UUID()
}

struct SafariPresenter: UIViewControllerRepresentable {
    @Binding var page: BrowserPage?

    func makeUIViewController(context: Context) -> SafariPresenterController { SafariPresenterController() }

    func updateUIViewController(_ controller: SafariPresenterController, context: Context) {
        controller.onDismiss = { page = nil }
        controller.update(page: page)
    }

    static func dismantleUIViewController(_ controller: SafariPresenterController, coordinator: ()) {
        controller.onDismiss = nil
        controller.stop()
    }
}

// Safari must be presented as a native modal, rather than embedded in a SwiftUI sheet.
@MainActor final class SafariPresenterController: UIViewController, SFSafariViewControllerDelegate, UIAdaptivePresentationControllerDelegate {
    var onDismiss: (() -> Void)?
    private var page: BrowserPage?
    private var activeID: UUID?
    private weak var browser: SFSafariViewController?

    override func loadView() { view = UIView(); view.backgroundColor = .clear }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); presentIfNeeded() }

    func update(page: BrowserPage?) {
        self.page = page
        if page == nil, activeID != nil {
            activeID = nil
            browser?.dismiss(animated: true)
            browser = nil
        } else { presentIfNeeded() }
    }

    private func presentIfNeeded() {
        guard viewIfLoaded?.window != nil, presentedViewController == nil, activeID == nil, let page else { return }
        activeID = page.id
        let browser = SFSafariViewController(url: page.url)
        browser.modalPresentationStyle = .pageSheet
        self.browser = browser
        browser.preferredControlTintColor = UIColor(Brand.gold)
        browser.dismissButtonStyle = .done
        browser.delegate = self
        var presenter = view.window?.rootViewController
        while let next = presenter?.presentedViewController { presenter = next }
        presenter?.present(browser, animated: true) {
            browser.view.accessibilityIdentifier = "inAppSafari"
            browser.presentationController?.delegate = self
        }
    }

    func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        controller.dismiss(animated: true) { self.activeID = nil; self.browser = nil; self.onDismiss?() }
    }

    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        activeID = nil; browser = nil; onDismiss?()
    }

    func stop() { browser?.dismiss(animated: false); browser = nil; activeID = nil }
}

private struct InAppBrowserModifier: ViewModifier {
    @State private var page: BrowserPage?

    func body(content: Content) -> some View {
        content
            .environment(\.openURL, OpenURLAction { url in
                guard ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
                      url.host != nil, url.host?.lowercased() != "apps.apple.com" else { return .systemAction }
                page = BrowserPage(url: url)
                return .handled
            })
            .background { SafariPresenter(page: $page).frame(width: 0, height: 0) }
    }
}

// Each navigation presentation owns its browser, including screens already in a sheet.
struct AppNavigationStack<Content: View>: View {
    private let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }

    var body: some View {
        NavigationStack { content }
            .modifier(InAppBrowserModifier())
            .tint(Brand.gold)
    }
}
