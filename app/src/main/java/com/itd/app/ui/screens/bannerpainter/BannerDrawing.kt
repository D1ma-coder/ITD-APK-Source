package com.itd.app.ui.screens.bannerpainter

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class BannerTool(val apiName: String, val freehand: Boolean) {
    BRUSH("brush", true),
    ERASER("eraser", true),
    LINE("line", false),
    RECTANGLE("rectangle", false),
    CIRCLE("circle", false),
}

/** One drawing operation; [points] holds x/y pairs. Freehand tools need at least one point, shapes exactly two. */
class BannerStroke(val tool: BannerTool, val color: Int, val width: Float, val points: FloatArray) {
    init {
        require(points.size % 2 == 0) { "odd coordinate count" }
        val count = points.size / 2
        require(if (tool.freehand) count >= 1 else count == 2) { "${tool.apiName} has $count points" }
    }
}

class BannerDrawing(var width: Int = 0, var height: Int = 0, val strokes: ArrayList<BannerStroke> = ArrayList()) {
    fun toJson(): String {
        val ops = strokes.map { stroke ->
            JsonObject(
                mapOf(
                    "tool" to JsonPrimitive(stroke.tool.apiName),
                    "color" to JsonPrimitive(if (stroke.tool == BannerTool.ERASER) "#ffffff" else String.format("#%06x", stroke.color and 0xFFFFFF)),
                    "width" to JsonPrimitive((round2(stroke.width.toDouble())).coerceIn(0.25, 100.0)),
                    "points" to JsonArray(
                        (0 until stroke.points.size / 2).map { i ->
                            JsonArray(listOf(JsonPrimitive(round2(stroke.points[i * 2].toDouble())), JsonPrimitive(round2(stroke.points[i * 2 + 1].toDouble()))))
                        },
                    ),
                ),
            )
        }
        return JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "width" to JsonPrimitive(width),
                "height" to JsonPrimitive(height),
                "ops" to JsonArray(ops),
            ),
        ).toString()
    }

    private fun round2(value: Double) = (value * 100.0).roundToLong() / 100.0

    companion object {
        const val MAX_JSON_BYTES = 2_097_152
    }
}
