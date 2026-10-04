import SwiftUI

@main struct MyApp: App {
    @State private var model = AppModel(native: NativeAuthenticationProvider())
    init() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-test-reset-preferences") {
            UserDefaults.standard.removeObject(forKey: "agreementAccepted")
            UserDefaults.standard.removeObject(forKey: "appearance")
        }
        #endif
    }
    var body: some Scene {
        WindowGroup {
            ContentView().environment(model)
        }
    }
}
