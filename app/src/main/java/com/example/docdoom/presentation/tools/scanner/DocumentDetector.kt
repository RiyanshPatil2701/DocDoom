package com.example.docdoom.presentation.tools.scanner

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Lightweight quadrilateral detector for the live camera preview.
 *
 * Input is a grayscale image already rotated into display orientation.
 * Returned points are in that same image's pixel coordinate space.
 */
class DocumentDetector {

    data class Detection(
        val corners: List<Point>,
        val confidence: Float,
        val imageWidth: Int,
        val imageHeight: Int
    )

    fun detect(gray: Mat): Detection? {
        if (gray.empty() || gray.cols() < 80 || gray.rows() < 80) return null

        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        try {
            Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 160.0)
            Imgproc.dilate(
                edges,
                edges,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
            )
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = gray.cols().toDouble() * gray.rows().toDouble()
            var best: Detection? = null
            var bestScore = 0.0

            for (contour in contours) {
                val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                if (perimeter <= 0.0) continue

                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(curve, approx, perimeter * 0.025, true)
                    val points = approx.toArray()
                    if (points.size != 4) continue

                    val polygon = MatOfPoint(*points)
                    try {
                        if (!Imgproc.isContourConvex(polygon)) continue
                        val area = abs(Imgproc.contourArea(polygon))
                        val areaRatio = area / imageArea
                        if (areaRatio < 0.12 || areaRatio > 0.96) continue

                        val ordered = orderCorners(points.toList())
                        if (!looksLikeQuadrilateral(ordered)) continue

                        // Prefer a large, reasonably balanced quadrilateral.
                        val score = areaRatio
                        if (score > bestScore) {
                            bestScore = score
                            best = Detection(
                                corners = ordered,
                                confidence = (areaRatio.coerceIn(0.0, 1.0) * 1.15)
                                    .toFloat().coerceIn(0f, 1f),
                                imageWidth = gray.cols(),
                                imageHeight = gray.rows()
                            )
                        }
                    } finally {
                        polygon.release()
                    }
                } finally {
                    curve.release()
                    approx.release()
                }
            }
            return best
        } finally {
            blurred.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun orderCorners(points: List<Point>): List<Point> {
        val sumSorted = points.sortedBy { it.x + it.y }
        val diffSorted = points.sortedBy { it.y - it.x }
        val topLeft = sumSorted.first()
        val bottomRight = sumSorted.last()
        val topRight = diffSorted.first()
        val bottomLeft = diffSorted.last()
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun looksLikeQuadrilateral(points: List<Point>): Boolean {
        if (points.size != 4) return false
        val widthTop = distance(points[0], points[1])
        val widthBottom = distance(points[3], points[2])
        val heightLeft = distance(points[0], points[3])
        val heightRight = distance(points[1], points[2])
        val maxSide = maxOf(widthTop, widthBottom, heightLeft, heightRight)
        val minSide = minOf(widthTop, widthBottom, heightLeft, heightRight)
        if (minSide < 35.0 || maxSide / minSide > 8.0) return false

        // Reject very narrow triangular-like shapes with unstable corner ordering.
        val cross = (points[1].x - points[0].x) * (points[3].y - points[0].y) -
                (points[1].y - points[0].y) * (points[3].x - points[0].x)
        return abs(cross) > 1500.0
    }

    private fun distance(a: Point, b: Point): Double =
        kotlin.math.hypot(a.x - b.x, a.y - b.y)
}
