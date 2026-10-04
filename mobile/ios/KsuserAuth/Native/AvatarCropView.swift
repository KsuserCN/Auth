import ImageIO
import SwiftUI
import UIKit

enum AvatarImage {
    enum ReadError: LocalizedError {
        case invalidImage
        var errorDescription: String? { "无法读取这张图片，请选择另一张图片" }
    }

    static func prepare(_ data: Data) throws -> UIImage {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 2048,
                kCGImageSourceShouldCacheImmediately: true
              ] as CFDictionary) else { throw ReadError.invalidImage }
        return UIImage(cgImage: thumbnail)
    }

    static func crop(_ image: UIImage, rect: CGRect) -> UIImage {
        let side = min(min(rect.width, rect.height), min(image.size.width, image.size.height))
        let origin = CGPoint(x: min(max(0, rect.minX), image.size.width - side),
                             y: min(max(0, rect.minY), image.size.height - side))
        let ratio = 1024 / side
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: 1024, height: 1024), format: format).image { context in
            UIColor.white.setFill(); context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
            image.draw(in: CGRect(x: -origin.x * ratio, y: -origin.y * ratio,
                                  width: image.size.width * ratio, height: image.size.height * ratio))
        }
    }
}

struct AvatarSelection: Identifiable {
    let id = UUID()
    let image: UIImage
}

struct AvatarCropView: UIViewControllerRepresentable {
    let image: UIImage
    let onCancel: () -> Void
    let onCrop: (UIImage) -> Void

    func makeUIViewController(context: Context) -> UINavigationController {
        let cropper = AvatarCropController(image: image, onCancel: onCancel, onCrop: onCrop)
        let navigation = UINavigationController(rootViewController: cropper)
        navigation.navigationBar.tintColor = UIColor(Brand.gold)
        return navigation
    }

    func updateUIViewController(_ controller: UINavigationController, context: Context) {}
}

@MainActor final class AvatarCropController: UIViewController, UIScrollViewDelegate {
    private let image: UIImage
    private let onCancel: () -> Void
    private let onCrop: (UIImage) -> Void
    private let scroll = UIScrollView()
    private let imageView = UIImageView()
    private let guide = UIView()
    private let mask = CAShapeLayer()
    private var viewport: CGSize = .zero

    init(image: UIImage, onCancel: @escaping () -> Void, onCrop: @escaping (UIImage) -> Void) {
        self.image = image; self.onCancel = onCancel; self.onCrop = onCrop
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "裁剪头像"
        view.backgroundColor = .systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "取消", style: .plain, target: self, action: #selector(cancel))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "使用头像", style: .done, target: self, action: #selector(confirm))
        navigationItem.rightBarButtonItem?.accessibilityIdentifier = "confirmAvatarCrop"

        scroll.delegate = self
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.showsHorizontalScrollIndicator = false; scroll.showsVerticalScrollIndicator = false
        scroll.bouncesZoom = true
        scroll.backgroundColor = .black
        scroll.layer.cornerRadius = 16; scroll.clipsToBounds = true
        scroll.accessibilityIdentifier = "avatarCropViewport"
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)
        imageView.image = image
        imageView.isAccessibilityElement = true
        imageView.accessibilityLabel = "可拖动和缩放的头像预览"
        imageView.frame = CGRect(origin: .zero, size: image.size)
        scroll.addSubview(imageView)
        scroll.contentSize = image.size

        guide.isUserInteractionEnabled = false
        guide.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(guide)
        mask.fillRule = .evenOdd; mask.fillColor = UIColor.black.withAlphaComponent(0.35).cgColor
        mask.strokeColor = UIColor.white.withAlphaComponent(0.8).cgColor; mask.lineWidth = 1
        guide.layer.addSublayer(mask)

        let instructions = UILabel()
        instructions.text = "拖动调整位置，双指缩放。圆圈内是头像预览。"
        instructions.font = .preferredFont(forTextStyle: .subheadline)
        instructions.adjustsFontForContentSizeCategory = true
        instructions.textColor = .secondaryLabel; instructions.textAlignment = .center; instructions.numberOfLines = 0
        instructions.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(instructions)

        let preferredWidth = scroll.widthAnchor.constraint(equalTo: view.safeAreaLayoutGuide.widthAnchor, constant: -40)
        preferredWidth.priority = .defaultHigh
        NSLayoutConstraint.activate([
            preferredWidth,
            scroll.widthAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.widthAnchor, constant: -40),
            scroll.heightAnchor.constraint(equalTo: scroll.widthAnchor),
            scroll.heightAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.heightAnchor, constant: -140),
            scroll.centerXAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerXAnchor),
            scroll.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor, constant: -25),
            guide.leadingAnchor.constraint(equalTo: scroll.leadingAnchor),
            guide.trailingAnchor.constraint(equalTo: scroll.trailingAnchor),
            guide.topAnchor.constraint(equalTo: scroll.topAnchor),
            guide.bottomAnchor.constraint(equalTo: scroll.bottomAnchor),
            instructions.topAnchor.constraint(equalTo: scroll.bottomAnchor, constant: 20),
            instructions.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            instructions.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            instructions.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12)
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let size = scroll.bounds.size
        guard size.width > 0, size != viewport else { return }
        let initial = viewport == .zero
        let center = initial ? CGPoint(x: image.size.width / 2, y: image.size.height / 2) :
            CGPoint(x: (scroll.contentOffset.x + viewport.width / 2) / scroll.zoomScale,
                    y: (scroll.contentOffset.y + viewport.height / 2) / scroll.zoomScale)
        let relativeZoom = initial ? 1 : scroll.zoomScale / scroll.minimumZoomScale
        viewport = size
        let minimum = max(size.width / image.size.width, size.height / image.size.height)
        scroll.minimumZoomScale = minimum; scroll.maximumZoomScale = minimum * 6
        scroll.zoomScale = min(scroll.maximumZoomScale, minimum * relativeZoom)
        scroll.contentOffset = CGPoint(
            x: min(max(0, center.x * scroll.zoomScale - size.width / 2), max(0, scroll.contentSize.width - size.width)),
            y: min(max(0, center.y * scroll.zoomScale - size.height / 2), max(0, scroll.contentSize.height - size.height)))
        mask.frame = guide.bounds
        let path = UIBezierPath(rect: guide.bounds)
        path.append(UIBezierPath(ovalIn: guide.bounds.insetBy(dx: 1, dy: 1)))
        mask.path = path.cgPath
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
    @objc private func cancel() { onCancel() }
    @objc private func confirm() {
        let rect = CGRect(x: scroll.contentOffset.x / scroll.zoomScale, y: scroll.contentOffset.y / scroll.zoomScale,
                          width: scroll.bounds.width / scroll.zoomScale, height: scroll.bounds.height / scroll.zoomScale)
        onCrop(AvatarImage.crop(image, rect: rect))
    }
}
