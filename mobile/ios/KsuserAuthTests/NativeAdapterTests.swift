import XCTest
import Vision
import UIKit
@testable import KsuserAuth

final class NativeAdapterTests: XCTestCase {
    func testAvatarCropExportsSelectedRegionAs1024PixelSquare() throws {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let source = UIGraphicsImageRenderer(size: CGSize(width: 300, height: 100), format: format).image { context in
            UIColor.red.setFill(); context.fill(CGRect(x: 0, y: 0, width: 100, height: 100))
            UIColor.green.setFill(); context.fill(CGRect(x: 100, y: 0, width: 100, height: 100))
            UIColor.blue.setFill(); context.fill(CGRect(x: 200, y: 0, width: 100, height: 100))
        }
        // A bounced viewport is clamped to the image edge without leaving blank pixels.
        let cropped = AvatarImage.crop(source, rect: CGRect(x: 250, y: 0, width: 100, height: 100))
        let cgImage = try XCTUnwrap(cropped.cgImage)
        XCTAssertEqual(cgImage.width, 1024); XCTAssertEqual(cgImage.height, 1024)
        var pixel = [UInt8](repeating: 0, count: 4)
        try pixel.withUnsafeMutableBytes { buffer in
            let context = try XCTUnwrap(CGContext(data: buffer.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        XCTAssertLessThan(pixel[0], 5); XCTAssertLessThan(pixel[1], 5); XCTAssertGreaterThan(pixel[2], 250)
        XCTAssertThrowsError(try AvatarImage.prepare(Data("invalid image".utf8)))
        let prepared = try AvatarImage.prepare(XCTUnwrap(source.pngData()))
        XCTAssertEqual(prepared.size.width / prepared.size.height, 3, accuracy: 0.01)
    }

    @MainActor func testAboutLogoIsBundled() { XCTAssertNotNil(UIImage(named: "AppLogo")) }

    func testBase64URLPreservesBinaryCredentialWithoutPadding() {
        let data = Data([0, 128, 255, 239, 17, 44, 19])
        XCTAssertEqual(Data(base64URL: data.base64URL), data)
        XCTAssertFalse(data.base64URL.contains("="))
        XCTAssertFalse(data.base64URL.contains("+"))
        XCTAssertFalse(data.base64URL.contains("/"))
    }

    func testLocalRecoveryQRCanBeDecodedWithoutExternalService() throws {
        let content = "https://auth.ksuser.cn/forgot-password?recoveryCode=LOCAL-TEST-ONLY&apiBaseUrl=https%3A%2F%2Fapi.ksuser.cn"
        let data = try XCTUnwrap(LocalQRCode.image(from: content)?.pngData())
        XCTAssertEqual(try QRImageDecoder.decode(data).first, content)
    }
}
