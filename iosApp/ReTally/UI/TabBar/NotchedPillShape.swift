import Shared
import SwiftUI

/// Stadium pill with the same concave scan-notch as the shared Compose tab bar.
///
/// Every number comes from `ReTallyTabBarDefaults` through `IosScreens` (1 pt == 1 dp here),
/// so this shape cannot drift from the Compose bar it sits behind. It used to hold its own
/// copies, which silently went stale the moment the Kotlin tokens changed.
struct NotchedPillShape: Shape {
    static var barHeight: CGFloat { CGFloat(IosScreens.shared.tabBarPillHeightPoints) }
    static var horizontalMargin: CGFloat { CGFloat(IosScreens.shared.tabBarHorizontalMarginPoints) }
    static var fabOverhang: CGFloat { CGFloat(IosScreens.shared.tabBarFabOverhangPoints) }
    static var bottomMargin: CGFloat { CGFloat(IosScreens.shared.tabBarBottomMarginPoints) }
    static var notchRadius: CGFloat { CGFloat(IosScreens.shared.tabBarNotchRadiusPoints) }
    /// Signed offset of the notch centre from the pill's top edge; negative sits above it.
    static var notchCenterY: CGFloat { CGFloat(IosScreens.shared.tabBarNotchCenterYPoints) }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let r = rect.height / 2
        let cx = rect.midX
        // Signed, matching Kotlin's NotchedBarShape: the centre normally sits *above* the
        // top edge, so the circle only clips a shallow dip out of the bar.
        let cy = rect.minY + Self.notchCenterY
        let nr = Self.notchRadius

        // Half-width of the chord where the notch circle meets the bar's top edge. When the
        // circle does not reach the edge there is no notch at all, so draw a plain pill.
        let dy = rect.minY - cy
        guard abs(dy) < nr else { return Path(roundedRect: rect, cornerRadius: r) }
        let dx = sqrt(nr * nr - dy * dy)
        let leftX = cx - dx
        let rightX = cx + dx

        // Angles use the UIKit convention: 0 = +x axis, positive = visually clockwise.
        let notchStart = Angle(radians: atan2(rect.minY - cy, leftX - cx))
        let notchEnd = Angle(radians: atan2(rect.minY - cy, rightX - cx))

        path.move(to: CGPoint(x: rect.minX + r, y: rect.minY))
        path.addLine(to: CGPoint(x: leftX, y: rect.minY))
        // Concave dip: sweep counter-clockwise from the left intersection, through the
        // bottom of the notch circle, up to the right intersection.
        path.addArc(center: CGPoint(x: cx, y: cy), radius: nr,
                    startAngle: notchStart, endAngle: notchEnd, clockwise: false)
        path.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        // Right end cap: top -> rightmost -> bottom.
        path.addArc(center: CGPoint(x: rect.maxX - r, y: rect.midY), radius: r,
                    startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: true)
        path.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
        // Left end cap: bottom -> leftmost -> top.
        path.addArc(center: CGPoint(x: rect.minX + r, y: rect.midY), radius: r,
                    startAngle: .degrees(90), endAngle: .degrees(270), clockwise: true)
        path.closeSubpath()
        return path
    }
}
