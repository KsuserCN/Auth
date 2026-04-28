//
//  Ksuser_AuthApp.swift
//  Ksuser Auth
//
//  Created by Ksuserkqy on 2026/4/25.
//

import SwiftUI
import UIKit

@main
struct Ksuser_AuthApp: App {
    init() {
        configureTabBarAppearance()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

private func configureTabBarAppearance() {
    let appearance = UITabBarAppearance()
    appearance.configureWithOpaqueBackground()
    appearance.backgroundColor = UIColor.systemBackground

    let itemAppearance = appearance.stackedLayoutAppearance
    itemAppearance.normal.iconColor = UIColor.secondaryLabel
    itemAppearance.normal.titleTextAttributes = [
        .font: UIFont.systemFont(ofSize: 11, weight: .regular),
        .foregroundColor: UIColor.secondaryLabel,
    ]
    itemAppearance.selected.iconColor = UIColor.systemBlue
    itemAppearance.selected.titleTextAttributes = [
        .font: UIFont.systemFont(ofSize: 11, weight: .regular),
        .foregroundColor: UIColor.systemBlue,
    ]

    let tabBar = UITabBar.appearance()
    tabBar.standardAppearance = appearance
    if #available(iOS 15.0, *) {
        tabBar.scrollEdgeAppearance = appearance
    }
}
