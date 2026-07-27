package com.softwiredtech.dashpilot.ui.controls

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom control glyphs with no Material equivalent: a zoomed-in quarter view
 * of the car with the relevant lid popped open — rear quarter with the boot
 * lid up for [Trunk], front quarter with the hood up for [Frunk]. Line-art
 * style: body line sweeping into the fascia, a bold donut wheel, and the
 * rocker sill running off-frame (implying the rest of the car). The two are
 * not strict mirrors: the trunk lid is drawn with the bent tip of a boot lid,
 * while the frunk shows Tesla's long low nose and its flat one-piece hood as
 * a single straight panel hinged at the cowl. Paths are authored in a 24x24
 * viewport; color is overridden by Icon tint.
 */
object VehicleIcons {

    private const val LINE_STROKE = 2.0f
    private const val WHEEL_STROKE = 2.3f
    private const val WHEEL_RADIUS = 3.8f

    val Frunk: ImageVector by lazy {
        ImageVector.Builder(
            name = "Frunk",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Long low hood line from off-frame right, sweeping into the
            // rounded nose, bumper tucking under toward the wheel.
            strokePath(LINE_STROKE) {
                moveTo(21.4f, 8.0f)
                quadTo(13.0f, 8.4f, 9.0f, 9.6f)
                quadTo(4.0f, 11.0f, 4.0f, 13.4f)
                lineTo(4.0f, 14.0f)
                quadTo(4.0f, 16.3f, 6.2f, 16.3f)
            }
            // Open hood: one flat panel, hinged at the cowl, lifted at the
            // nose (Tesla's frunk cover has no bent lip).
            strokePath(LINE_STROKE) {
                moveTo(16.6f, 8.3f)
                lineTo(5.4f, 2.9f)
            }
            // Rocker sill running off-frame.
            strokePath(LINE_STROKE) {
                moveTo(21.4f, 17.7f)
                lineTo(16.0f, 17.7f)
            }
            wheel(cx = 11.4f, cy = 16.2f)
        }.build()
    }

    val Trunk: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trunk",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Beltline from off-frame left, sweeping down into the rear
            // fascia and bumper.
            strokePath(LINE_STROKE) {
                moveTo(2.6f, 5.4f)
                lineTo(5.8f, 5.4f)
                quadTo(7.2f, 5.4f, 8.3f, 5.9f)
                lineTo(17.3f, 9.9f)
                quadTo(20.3f, 11.1f, 20.3f, 13.0f)
                lineTo(20.3f, 14.0f)
                quadTo(20.3f, 16.3f, 18.1f, 16.3f)
            }
            // Open boot lid, branching off the beltline, tip bent at the tail.
            strokePath(LINE_STROKE) {
                moveTo(12.6f, 7.8f)
                lineTo(16.8f, 3.9f)
                quadTo(17.4f, 3.3f, 18.2f, 3.7f)
                lineTo(20.2f, 5.3f)
            }
            // Rocker sill running off-frame.
            strokePath(LINE_STROKE) {
                moveTo(2.6f, 17.7f)
                lineTo(8.0f, 17.7f)
            }
            wheel(cx = 12.6f, cy = 16.2f)
        }.build()
    }

    private fun ImageVector.Builder.wheel(cx: Float, cy: Float) {
        strokePath(WHEEL_STROKE) {
            moveTo(cx - WHEEL_RADIUS, cy)
            arcTo(
                WHEEL_RADIUS, WHEEL_RADIUS, 0f,
                isMoreThanHalf = true, isPositiveArc = true,
                x1 = cx + WHEEL_RADIUS, y1 = cy
            )
            arcTo(
                WHEEL_RADIUS, WHEEL_RADIUS, 0f,
                isMoreThanHalf = true, isPositiveArc = true,
                x1 = cx - WHEEL_RADIUS, y1 = cy
            )
            close()
        }
    }

    private fun ImageVector.Builder.strokePath(
        width: Float,
        pathBuilder: PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder
        )
    }
}
