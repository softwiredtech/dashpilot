import SwiftUI

struct CommaShape: Shape {
    func path(in rect: CGRect) -> Path {
        // Original Android viewport: 24 x 42
        let scaleX = rect.width / 24
        let scaleY = rect.height / 42

        var path = Path()
        path.move(to: CGPoint(x: 3.2265 * scaleX, y: 42 * scaleY))
        path.addCurve(
            to: CGPoint(x: 3.2623 * scaleX, y: 39.4294 * scaleY),
            control1: CGPoint(x: 3.2265 * scaleX, y: 41.0282 * scaleY),
            control2: CGPoint(x: 3.1505 * scaleX, y: 40.2141 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 4.0189 * scaleX, y: 38.5344 * scaleY),
            control1: CGPoint(x: 3.3099 * scaleX, y: 39.0948 * scaleY),
            control2: CGPoint(x: 3.7004 * scaleX, y: 38.7365 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 8.7043 * scaleX, y: 35.6319 * scaleY),
            control1: CGPoint(x: 5.5717 * scaleX, y: 37.5489 * scaleY),
            control2: CGPoint(x: 7.2586 * scaleX, y: 36.7621 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 16.3145 * scaleX, y: 20.8905 * scaleY),
            control1: CGPoint(x: 13.387 * scaleX, y: 31.9708 * scaleY),
            control2: CGPoint(x: 16.1628 * scaleX, y: 27.1512 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 14.2108 * scaleX, y: 19.3612 * scaleY),
            control1: CGPoint(x: 16.3574 * scaleX, y: 19.1128 * scaleY),
            control2: CGPoint(x: 15.7039 * scaleX, y: 18.6687 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 2.7928 * scaleX, y: 16.4731 * scaleY),
            control1: CGPoint(x: 9.9009 * scaleX, y: 21.3604 * scaleY),
            control2: CGPoint(x: 5.2662 * scaleX, y: 20.1883 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 3.7072 * scaleX, y: 3.4862 * scaleY),
            control1: CGPoint(x: 0.0954 * scaleX, y: 12.421 * scaleY),
            control2: CGPoint(x: 0.4752 * scaleX, y: 7.0259 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 19.3123 * scaleX, y: 3.1476 * scaleY),
            control1: CGPoint(x: 7.8354 * scaleX, y: -1.0348 * scaleY),
            control2: CGPoint(x: 14.6281 * scaleX, y: -1.1701 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 23.7839 * scaleX, y: 12.8722 * scaleY),
            control1: CGPoint(x: 22.1182 * scaleX, y: 5.734 * scaleY),
            control2: CGPoint(x: 23.4514 * scaleX, y: 9.0736 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 6.8985 * scaleX, y: 40.695 * scaleY),
            control1: CGPoint(x: 24.8985 * scaleX, y: 25.598 * scaleY),
            control2: CGPoint(x: 18.5156 * scaleX, y: 36.0872 * scaleY)
        )
        path.addCurve(
            to: CGPoint(x: 3.2265 * scaleX, y: 42 * scaleY),
            control1: CGPoint(x: 5.7488 * scaleX, y: 41.1508 * scaleY),
            control2: CGPoint(x: 4.5698 * scaleX, y: 41.5252 * scaleY)
        )
        path.closeSubpath()
        return path
    }
}
