package com.app.mindunload.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.app.mindunload.ui.theme.IconSize

/**
 * Small line icons following the stroke paths from the Claude design prototype
 * ("Alltagsplaner Prototyp v2"), as Canvas instead of ImageVector — more robust than
 * hand-derived Bezier paths, same look (round strokes, 24x24 grid).
 *
 * The 24x24 design grid is fixed ([GRID]); rendered size and stroke come from
 * [IconSize] so every icon stays visually consistent.
 */
private const val GRID = 24f

@Composable
private fun IconCanvas(
    modifier: Modifier,
    tint: Color,
    strokeWidthDp: Float = IconSize.stroke,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(scale: Float, sw: Float) -> Unit,
) {
    Canvas(modifier = modifier.size(IconSize.default)) {
        val scale = size.width / GRID
        draw(scale, strokeWidthDp.dp.toPx())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pt(x: Float, y: Float, scale: Float) =
    Offset(x * scale, y * scale)

@Composable
fun MenuIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawLine(tint, pt(4f, 6.5f, scale), pt(20f, 6.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(4f, 12f, scale), pt(20f, 12f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(4f, 17.5f, scale), pt(14f, 17.5f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun BackChevronIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(14f * scale, 5f * scale)
            lineTo(7f * scale, 12f * scale)
            lineTo(14f * scale, 19f * scale)
        }
        drawPath(
            path,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawLine(tint, pt(6f, 6f, scale), pt(18f, 18f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(18f, 6f, scale), pt(6f, 18f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(5f * scale, 12.5f * scale)
            lineTo(9.5f * scale, 17f * scale)
            lineTo(19f * scale, 7.5f * scale)
        }
        drawPath(
            path,
            tint,
            style = Stroke(
                width = sw * 1.4f,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

@Composable
fun TrashIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Lid with handle, body, two inner strokes.
        drawLine(tint, pt(4f, 6.5f, scale), pt(20f, 6.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(9.5f, 6.5f, scale), pt(9.5f, 3.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(9.5f, 3.5f, scale), pt(14.5f, 3.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(14.5f, 3.5f, scale), pt(14.5f, 6.5f, scale), sw, StrokeCap.Round)
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(6.5f * scale, 6.5f * scale)
            lineTo(7.5f * scale, 20f * scale)
            lineTo(16.5f * scale, 20f * scale)
            lineTo(17.5f * scale, 6.5f * scale)
        }
        drawPath(
            body,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        drawLine(tint, pt(10.5f, 10f, scale), pt(10.8f, 16.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(13.5f, 10f, scale), pt(13.2f, 16.5f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawCircle(
            tint,
            radius = 6.5f * scale,
            center = pt(10.5f, 10.5f, scale),
            style = Stroke(sw)
        )
        drawLine(tint, pt(15.5f, 15.5f, scale), pt(21f, 21f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun MicIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawRoundRect(
            tint,
            topLeft = pt(9f, 3f, scale),
            size = Size(6f * scale, 11f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale),
            style = Stroke(sw),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(5.5f * scale, 11.5f * scale)
            arcTo(
                androidx.compose.ui.geometry.Rect(pt(5.5f, 5f, scale), pt(18.5f, 18f, scale)),
                0f,
                180f,
                false,
            )
        }
        drawPath(path, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        drawLine(tint, pt(12f, 18f, scale), pt(12f, 21f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun SendIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(4.5f * scale, 20f * scale)
            lineTo(20.5f * scale, 12f * scale)
            lineTo(4.5f * scale, 4f * scale)
            lineTo(4.5f * scale, 10.5f * scale)
            lineTo(13.5f * scale, 12f * scale)
            lineTo(4.5f * scale, 13.5f * scale)
            close()
        }
        drawPath(
            path,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            ),
        )
    }
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Zahnrad: Ring + 8 Zaehne aussen + Nabe
        val c = pt(12f, 12f, scale)
        drawCircle(tint, radius = 6.2f * scale, center = c, style = Stroke(sw))
        drawCircle(tint, radius = 2.6f * scale, center = c, style = Stroke(sw))
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0 - 90.0)
            val dx = kotlin.math.cos(a).toFloat()
            val dy = kotlin.math.sin(a).toFloat()
            drawLine(
                tint,
                Offset(c.x + dx * 6.2f * scale, c.y + dy * 6.2f * scale),
                Offset(c.x + dx * 9f * scale, c.y + dy * 9f * scale),
                sw * 1.25f,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun RetryIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Open circular arc (gap at the top right) with a tangential arrowhead at the end —
        // the tip is computed from the arc end instead of hard-coded.
        val r = 7f * scale
        val center = pt(12f, 12f, scale)
        val endDeg = 300.0
        val arc = androidx.compose.ui.graphics.Path().apply {
            arcTo(
                androidx.compose.ui.geometry.Rect(pt(5f, 5f, scale), pt(19f, 19f, scale)),
                0f,
                300f,
                false
            )
        }
        drawPath(arc, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        val t = Math.toRadians(endDeg)
        val tip = Offset(
            center.x + r * kotlin.math.cos(t).toFloat(),
            center.y + r * kotlin.math.sin(t).toFloat(),
        )
        // Tangent of the drawing direction = t + 90°; wings point ±32° backwards.
        val back = t + Math.toRadians(90.0) + Math.PI
        val len = 4.4f * scale
        for (off in listOf(-0.56, 0.56)) {
            val a = back + off
            drawLine(
                tint,
                tip,
                Offset(
                    tip.x + len * kotlin.math.cos(a).toFloat(),
                    tip.y + len * kotlin.math.sin(a).toFloat()
                ),
                sw,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun TabTodayIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawCircle(tint, radius = 4f * scale, center = pt(12f, 12f, scale), style = Stroke(sw))
        drawLine(tint, pt(12f, 3f, scale), pt(12f, 5.4f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(12f, 18.6f, scale), pt(12f, 21f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(3f, 12f, scale), pt(5.4f, 12f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(18.6f, 12f, scale), pt(21f, 12f, scale), sw, StrokeCap.Round)
    }
}

/**
 * The four-point AI spark of the launcher/splash icon (ic_launcher_foreground), with its
 * control-point ratios (0.15/0.42 of the radius) kept intact.
 */
private fun androidx.compose.ui.graphics.Path.addSpark(cx: Float, cy: Float, r: Float) {
    moveTo(cx, cy - r)
    cubicTo(cx + .15f * r, cy - .42f * r, cx + .42f * r, cy - .15f * r, cx + r, cy)
    cubicTo(cx + .42f * r, cy + .15f * r, cx + .15f * r, cy + .42f * r, cx, cy + r)
    cubicTo(cx - .15f * r, cy + .42f * r, cx - .42f * r, cy + .15f * r, cx - r, cy)
    cubicTo(cx - .42f * r, cy - .15f * r, cx - .15f * r, cy - .42f * r, cx, cy - r)
    close()
}

/**
 * The splash-screen motif (ic_launcher_foreground): rounded speech bubble with a tail at
 * the bottom left and the AI spark inside. The launcher geometry (viewport 108: body
 * 31..77 x 38..70, radius 10, tail to y=78, spark r=12 around 54,54) is mapped onto the
 * 24-grid of the tab icons with the factor 16/46, so the proportions carry over. Drawn as
 * an outline instead of the filled launcher shape — the other tabs are line icons too;
 * the spark is a touch smaller than 1:1 because the stroke eats into the bubble's inside.
 */
@Composable
fun TabChatIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val r = 3.48f // corner radius
        val bottom = 15.13f // lower edge of the bubble body
        fun corner(l: Float, t: Float) = androidx.compose.ui.geometry.Rect(
            pt(l, t, scale),
            pt(l + 2 * r, t + 2 * r, scale),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo((4f + r) * scale, 4f * scale)
            lineTo((20f - r) * scale, 4f * scale)
            arcTo(corner(20f - 2 * r, 4f), -90f, 90f, false)
            lineTo(20f * scale, (bottom - r) * scale)
            arcTo(corner(20f - 2 * r, bottom - 2 * r), 0f, 90f, false)
            lineTo(11.3f * scale, bottom * scale) // tail
            lineTo(8.17f * scale, 17.9f * scale)
            lineTo(8.17f * scale, bottom * scale)
            lineTo((4f + r) * scale, bottom * scale)
            arcTo(corner(4f, bottom - 2 * r), 90f, 90f, false)
            lineTo(4f * scale, (4f + r) * scale)
            arcTo(corner(4f, 4f), 180f, 90f, false)
            close()
        }
        drawPath(
            path,
            tint,
            style = Stroke(width = sw, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
        drawPath(
            androidx.compose.ui.graphics.Path()
                .apply { addSpark(12f * scale, 9.57f * scale, 3.4f * scale) },
            tint,
        )
    }
}

@Composable
fun TabTasksIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawRoundRect(
            tint,
            topLeft = pt(4f, 4f, scale),
            size = Size(16f * scale, 16f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * scale, 5f * scale),
            style = Stroke(sw),
        )
        val check = androidx.compose.ui.graphics.Path().apply {
            moveTo(8.5f * scale, 12.2f * scale)
            lineTo(11f * scale, 14.7f * scale)
            lineTo(15.7f * scale, 9.7f * scale)
        }
        drawPath(
            check,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

@Composable
fun TabAppointmentsIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawRoundRect(
            tint,
            topLeft = pt(4f, 5.5f, scale),
            size = Size(16f * scale, 14.5f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale, 4f * scale),
            style = Stroke(sw),
        )
        drawLine(tint, pt(4f, 10f, scale), pt(20f, 10f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(8.5f, 3.5f, scale), pt(8.5f, 6.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(15.5f, 3.5f, scale), pt(15.5f, 6.5f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun TabShoppingIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val basket = androidx.compose.ui.graphics.Path().apply {
            moveTo(5f * scale, 8f * scale)
            lineTo(19f * scale, 8f * scale)
            lineTo(17.8f * scale, 20f * scale)
            lineTo(6.2f * scale, 20f * scale)
            close()
        }
        drawPath(
            basket,
            tint,
            style = Stroke(width = sw, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
        val handle = androidx.compose.ui.graphics.Path().apply {
            moveTo(8.8f * scale, 10.5f * scale)
            lineTo(8.8f * scale, 7f * scale)
            arcTo(
                androidx.compose.ui.geometry.Rect(pt(8.8f, 0.6f, scale), pt(15.2f, 7f, scale)),
                180f,
                -180f,
                false
            )
            lineTo(15.2f * scale, 10.5f * scale)
        }
        drawPath(handle, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

/** Heart with a cross — used for the "Gesundheit" (health) category row in the drawer. */
@Composable
fun HealthIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val heart = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * scale, 18.5f * scale)
            cubicTo(
                5f * scale, 12.5f * scale,
                5f * scale, 6.5f * scale,
                9.2f * scale, 6.5f * scale,
            )
            cubicTo(11f * scale, 6.5f * scale, 12f * scale, 8f * scale, 12f * scale, 8.3f * scale)
            cubicTo(
                12f * scale, 8f * scale,
                13f * scale, 6.5f * scale,
                14.8f * scale, 6.5f * scale,
            )
            cubicTo(19f * scale, 6.5f * scale, 19f * scale, 12.5f * scale, 12f * scale, 18.5f * scale)
            close()
        }
        drawPath(
            heart,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        drawLine(tint, pt(12f, 9.8f, scale), pt(12f, 13f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(10.4f, 11.4f, scale), pt(13.6f, 11.4f, scale), sw, StrokeCap.Round)
    }
}

/** Briefcase — used for the "Arbeit" (work) category row in the drawer. */
@Composable
fun BriefcaseIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val handle = androidx.compose.ui.graphics.Path().apply {
            moveTo(9f * scale, 7.2f * scale)
            lineTo(9f * scale, 5.7f * scale)
            arcTo(
                androidx.compose.ui.geometry.Rect(pt(9f, 3f, scale), pt(15f, 6f, scale)),
                180f,
                -180f,
                false,
            )
            lineTo(15f * scale, 7.2f * scale)
        }
        drawPath(handle, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        drawRoundRect(
            tint,
            topLeft = pt(4f, 7.2f, scale),
            size = Size(16f * scale, 11f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * scale, 2.2f * scale),
            style = Stroke(sw),
        )
        drawLine(tint, pt(4f, 12.5f, scale), pt(20f, 12.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(11f, 11.5f, scale), pt(13f, 11.5f, scale), sw * 1.4f, StrokeCap.Round)
    }
}

/** Smiling face — used for the "Persönlich" (personal) category row in the drawer. */
@Composable
fun SmileyIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawCircle(tint, radius = 8f * scale, center = pt(12f, 12f, scale), style = Stroke(sw))
        drawCircle(tint, radius = 1f * scale, center = pt(8.8f, 10.2f, scale))
        drawCircle(tint, radius = 1f * scale, center = pt(15.2f, 10.2f, scale))
        val smile = androidx.compose.ui.graphics.Path().apply {
            moveTo(8f * scale, 13.5f * scale)
            quadraticTo(12f * scale, 17.5f * scale, 16f * scale, 13.5f * scale)
        }
        drawPath(smile, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

/** Small office tower — used for the "Verwaltung" (administration) category row in the drawer. */
@Composable
fun OfficeTowerIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawRoundRect(
            tint,
            topLeft = pt(7f, 3.3f, scale),
            size = Size(10f * scale, 17.2f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * scale, 1.4f * scale),
            style = Stroke(sw),
        )
        drawLine(tint, pt(4f, 20.5f, scale), pt(20f, 20.5f, scale), sw, StrokeCap.Round)
        val windowStroke = sw * 0.8f
        listOf(9.4f, 14.6f).forEach { cx ->
            listOf(6.8f, 10.6f, 14.4f).forEach { cy ->
                drawRoundRect(
                    tint,
                    topLeft = pt(cx - 1f, cy - 1f, scale),
                    size = Size(2f * scale, 2f * scale),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.4f * scale, 0.4f * scale),
                    style = Stroke(windowStroke),
                )
            }
        }
    }
}

/**
 * Icon for a drawer category row. Named categories the user is likely to create
 * (Wissen/Gesundheit/Arbeit/Persönlich/Verwaltung, matched case-insensitively) get a
 * bespoke icon instead of the generic per-item-type one, so they read at a glance instead
 * of collapsing onto whichever entry type happens to dominate the category. Anything else
 * falls back to [TypeIcon].
 */
@Composable
fun CategoryIcon(
    name: String,
    topType: com.app.mindunload.data.ItemType,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    when (name.trim().lowercase()) {
        "wissen" -> KnowledgeIcon(modifier, tint)
        "gesundheit" -> HealthIcon(modifier, tint)
        "arbeit" -> BriefcaseIcon(modifier, tint)
        "persönlich", "personlich" -> SmileyIcon(modifier, tint)
        "verwaltung" -> OfficeTowerIcon(modifier, tint)
        else -> TypeIcon(topType, modifier, tint)
    }
}

/** Icon for an entry type — e.g. for category rows that carry the icon of their dominant type. */
@Composable
fun TypeIcon(
    type: com.app.mindunload.data.ItemType,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    when (type) {
        com.app.mindunload.data.ItemType.TASK -> TabTasksIcon(modifier, tint)
        com.app.mindunload.data.ItemType.APPOINTMENT -> TabAppointmentsIcon(modifier, tint)
        com.app.mindunload.data.ItemType.SHOPPING_ITEM -> TabShoppingIcon(modifier, tint)
        com.app.mindunload.data.ItemType.NOTE -> KnowledgeIcon(modifier, tint)
        com.app.mindunload.data.ItemType.IDEA -> IdeasIcon(modifier, tint)
        com.app.mindunload.data.ItemType.GOAL -> GoalsIcon(modifier, tint)
    }
}

@Composable
fun KnowledgeIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Open book: two pages fanning out from a center spine, each an open contour
        // (not closed/filled) so it reads as paper, not a solid shape.
        val leftPage = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * scale, 8f * scale)
            cubicTo(
                10.5f * scale, 6.3f * scale,
                7.5f * scale, 5.5f * scale,
                4.7f * scale, 6f * scale,
            )
            lineTo(4.7f * scale, 17f * scale)
            cubicTo(
                7.5f * scale, 16.5f * scale,
                10.5f * scale, 17.3f * scale,
                12f * scale, 19f * scale,
            )
        }
        val rightPage = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * scale, 8f * scale)
            cubicTo(
                13.5f * scale, 6.3f * scale,
                16.5f * scale, 5.5f * scale,
                19.3f * scale, 6f * scale,
            )
            lineTo(19.3f * scale, 17f * scale)
            cubicTo(
                16.5f * scale, 16.5f * scale,
                13.5f * scale, 17.3f * scale,
                12f * scale, 19f * scale,
            )
        }
        val pageStyle = Stroke(
            width = sw,
            cap = StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        drawPath(leftPage, tint, style = pageStyle)
        drawPath(rightPage, tint, style = pageStyle)
        drawLine(tint, pt(12f, 8f, scale), pt(12f, 19f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun IdeasIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawCircle(tint, radius = 5.5f * scale, center = pt(12f, 10f, scale), style = Stroke(sw))
        drawLine(tint, pt(10f, 18.5f, scale), pt(14f, 18.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(10.5f, 21f, scale), pt(13.5f, 21f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun GoalsIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawCircle(tint, radius = 8f * scale, center = pt(12f, 12f, scale), style = Stroke(sw))
        drawCircle(tint, radius = 3f * scale, center = pt(12f, 12f, scale), style = Stroke(sw))
    }
}

/**
 * Ascending usage bars — distinct from [GoalsIcon]'s bullseye so the drawer's "Goals" and
 * "API usage" rows (previously both the bullseye) no longer read as the same entry.
 */
@Composable
fun UsageIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawLine(tint, pt(6f, 19f, scale), pt(6f, 13f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(12f, 19f, scale), pt(12f, 8f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(18f, 19f, scale), pt(18f, 5f, scale), sw, StrokeCap.Round)
    }
}

/**
 * Classic jagged trend line — peaks and valleys across the grid, the familiar chart-line
 * glyph. Deliberately not more ascending bars ([UsageIcon]) so the drawer's "API usage"
 * and "Usage statistics" rows read as different things at a glance.
 */
@Composable
fun StatsIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(3.5f * scale, 16.5f * scale)
            lineTo(8f * scale, 10f * scale)
            lineTo(11f * scale, 13.5f * scale)
            lineTo(15.5f * scale, 6f * scale)
            lineTo(20.5f * scale, 9.5f * scale)
        }
        drawPath(
            path,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
fun ImageIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Frame, sun, mountain ridge.
        drawRoundRect(
            tint,
            topLeft = pt(3.5f, 4.5f, scale),
            size = Size(17f * scale, 15f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale),
            style = Stroke(width = sw),
        )
        drawCircle(tint, radius = 1.6f * scale, center = pt(9f, 10f, scale), style = Stroke(width = sw))
        val ridge = androidx.compose.ui.graphics.Path().apply {
            moveTo(4.5f * scale, 17f * scale)
            lineTo(10f * scale, 12.5f * scale)
            lineTo(14f * scale, 16f * scale)
            lineTo(16.5f * scale, 13.5f * scale)
            lineTo(19.5f * scale, 16.5f * scale)
        }
        drawPath(
            ridge,
            tint,
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
fun PlusIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawLine(tint, pt(12f, 5.5f, scale), pt(12f, 18.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(5.5f, 12f, scale), pt(18.5f, 12f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun FileIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Sheet with a folded corner, plus two text lines.
        val sheet = androidx.compose.ui.graphics.Path().apply {
            moveTo(13.5f * scale, 3.5f * scale)
            lineTo(6.5f * scale, 3.5f * scale)
            lineTo(6.5f * scale, 20.5f * scale)
            lineTo(17.5f * scale, 20.5f * scale)
            lineTo(17.5f * scale, 7.5f * scale)
            close()
        }
        drawPath(
            sheet,
            tint,
            style = Stroke(
                width = sw,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        drawLine(tint, pt(13.5f, 3.5f, scale), pt(13.5f, 7.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(13.5f, 7.5f, scale), pt(17.5f, 7.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(9.5f, 12.5f, scale), pt(14.5f, 12.5f, scale), sw, StrokeCap.Round)
        drawLine(tint, pt(9.5f, 16f, scale), pt(14.5f, 16f, scale), sw, StrokeCap.Round)
    }
}

@Composable
fun CameraIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        // Viewfinder bump, body, lens — same 24x24 grid and stroke as [ImageIcon], so
        // the two read as one pair in the attachment menu.
        drawRoundRect(
            tint,
            topLeft = pt(9f, 4.5f, scale),
            size = Size(6f * scale, 3.5f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * scale, 1.5f * scale),
            style = Stroke(width = sw),
        )
        drawRoundRect(
            tint,
            topLeft = pt(3.5f, 7f, scale),
            size = Size(17f * scale, 12.5f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale),
            style = Stroke(width = sw),
        )
        drawCircle(
            tint,
            radius = 3.4f * scale,
            center = pt(12f, 13.4f, scale),
            style = Stroke(width = sw)
        )
    }
}

@Composable
fun PlayIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, _ ->
        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(8f * scale, 5.5f * scale)
            lineTo(19f * scale, 12f * scale)
            lineTo(8f * scale, 18.5f * scale)
            close()
        }
        drawPath(triangle, tint)
    }
}

@Composable
fun PauseIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, sw ->
        drawLine(tint, pt(9f, 5.5f, scale), pt(9f, 18.5f, scale), sw * 1.8f, StrokeCap.Round)
        drawLine(tint, pt(15f, 5.5f, scale), pt(15f, 18.5f, scale), sw * 1.8f, StrokeCap.Round)
    }
}

@Composable
fun StopIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    IconCanvas(modifier, tint) { scale, _ ->
        drawRoundRect(
            tint,
            topLeft = pt(7f, 7f, scale),
            size = Size(10f * scale, 10f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale, 2f * scale),
        )
    }
}
