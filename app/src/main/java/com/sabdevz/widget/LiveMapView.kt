package com.sabdevz.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.sabdevz.livemap.R
import java.io.InputStream
import java.util.Locale
import kotlin.math.sin
import androidx.core.graphics.createBitmap


class LiveMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Runnable {

    data class Node(val name: String, val lat: Double, val lng: Double)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3BDE32")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(15f, 0f, 0f, Color.parseColor("#3BDE32"))
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
    }

    private val nodes = ArrayList<Node>()
    private var running = false
    private var thread: Thread? = null
    var isBlinking: Boolean = false
    private var activeNode = -1
    private var homeNodeIndex = 0
    private var selectedNodeIndex = -1
    private var pulse = 0f
    private var linePhase = 0f
    private var lineProgress = 0f
    private var worldMap: Bitmap? = null
    private var activeFlagBitmap: Bitmap? = null
    private var homeFlagBitmap: Bitmap? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        val original = BitmapFactory.decodeResource(resources, R.drawable.world_map)
        if (original != null) {
            worldMap = processMapBitmap(original)
        }
        initNodes()
        val deviceCountry = Locale.getDefault().displayCountry
        setHomeCountry(deviceCountry)
    }

    private fun openFlagStream(name: String): InputStream? {
        if (name.isBlank()) return null
        val formatted = name.trim()
        val candidates = listOf(
            "flags/$formatted.png",
            "flags/${formatted.replace(" ", "_")}.png",
            "flags/${formatted.lowercase(Locale.ROOT)}.png",
            "flags/${formatted.lowercase(Locale.ROOT).replace(" ", "_")}.png",
            "flags/flag_$formatted.png",
            "flags/flag_${formatted.lowercase(Locale.ROOT).replace(" ", "_")}.png"
        )
        for (path in candidates) {
            try {
                return context.assets.open(path)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun updateHomeFlag() {
        val homeName = if (homeNodeIndex in nodes.indices) nodes[homeNodeIndex].name else "Philippines"
        val stream = openFlagStream(homeName)
            ?: (if (homeName == "USA") openFlagStream("United States") else null)
            ?: openFlagStream("Philippines")
            ?: openFlagStream("DEFAULT")

        if (stream != null) {
            try {
                val original = BitmapFactory.decodeStream(stream)
                if (original != null) {
                    homeFlagBitmap = getCircularBitmap(original)
                }
                stream.close()
            } catch (e: Exception) {
                Log.e("LiveMapView", "Error decoding home flag: ${e.message}")
            }
        }
    }

    fun setActiveFlag(flagName: String) {
        val stream = openFlagStream(flagName)
            ?: (if (activeNode in nodes.indices) openFlagStream(nodes[activeNode].name) else null)
            ?: (if (activeNode in nodes.indices && nodes[activeNode].name == "USA") openFlagStream("United States") else null)
            ?: openFlagStream("Philippines")
            ?: openFlagStream("DEFAULT")

        if (stream != null) {
            try {
                val original = BitmapFactory.decodeStream(stream)
                if (original != null) {
                    activeFlagBitmap = getCircularBitmap(original)
                }
                stream.close()
                postInvalidate()
            } catch (e: Exception) {
                Log.e("LiveMapView", "Error decoding active flag: ${e.message}")
            }
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, size, size)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    fun setActiveNode(index: Int) {
        if (activeNode != index) {
            activeNode = index
            lineProgress = 0f
        }
        postInvalidate()
    }

    @SuppressLint("UseKtx")
    private fun processMapBitmap(source: Bitmap): Bitmap {
        val bmp = createBitmap(source.width, source.height)
        val canvas = Canvas(bmp)

        // 1. Gawing Grayscale ang image para mawala ang original colors
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        val tintColor = context.getColor(R.color.colorAccent)
        paint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_ATOP)
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        return bmp
    }
    private fun initNodes() {
        nodes.clear()
        addNode("Philippines", 12.8797, 121.7740)
        
        // ASIA
        addNode("Singapore", 1.3521, 103.8198)
        addNode("Japan", 36.2048, 138.2529)
        addNode("Hong Kong", 22.3193, 114.1694)
        addNode("South Korea", 35.9078, 127.7669)
        addNode("Taiwan", 23.6978, 120.9605)
        addNode("Vietnam", 14.0583, 108.2772)
        addNode("Thailand", 15.8700, 100.9925)
        addNode("Malaysia", 4.2105, 101.9758)
        addNode("Indonesia", -0.7893, 113.9213)
        addNode("India", 20.5937, 78.9629)
        addNode("Bangladesh", 23.6850, 90.3563)
        
        // OCEANIA
        addNode("Australia", -25.2744, 133.7751)
        addNode("New Zealand", -40.9006, 174.8860)
        
        // NORTH AMERICA
        addNode("USA", 37.0902, -95.7129)
        addNode("Canada", 56.1304, -106.3468)
        addNode("Mexico", 23.6345, -102.5528)
        
        // SOUTH AMERICA
        addNode("Brazil", -14.2350, -51.9253)
        addNode("Argentina", -38.4161, -63.6167)
        addNode("Chile", -35.6751, -71.5430)
        addNode("Colombia", 4.5709, -74.2973)
        
        // EUROPE
        addNode("Finland", 61.9241, 25.7482)
        addNode("United Kingdom", 55.3781, -3.4360)
        addNode("Germany", 51.1657, 10.4515)
        addNode("France", 46.2276, 2.2137)
        addNode("Netherlands", 52.1326, 5.2913)
        addNode("Italy", 41.8719, 12.5674)
        addNode("Spain", 40.4637, -3.7492)
        addNode("Russia", 61.5240, 105.3188)
        addNode("Sweden", 60.1282, 18.6435)
        addNode("Norway", 60.4720, 8.4689)
        addNode("Switzerland", 46.8182, 8.2275)
        addNode("Turkey", 38.9637, 35.2433)
        
        // MIDDLE EAST & AFRICA
        addNode("UAE", 23.4241, 53.8478)
        addNode("Saudi Arabia", 23.8859, 45.0792)
        addNode("Israel", 31.0461, 34.8516)
        addNode("South Africa", -30.5595, 22.9375)
        addNode("Egypt", 26.8206, 30.8025)
        addNode("Nigeria", 9.0820, 8.6753)
    }

    private fun addNode(name: String, lat: Double, lng: Double) {
        nodes.add(Node(name, lat, lng))
    }

    fun setHomeCountry(countryName: String) {
        homeNodeIndex = 0
        updateHomeFlag()
        postInvalidate()
    }

    @JvmOverloads
    fun setCountry(countryName: String, flagName: String = "") {
        Log.d("LiveMapView", "setCountry called with countryName: $countryName, flagName: $flagName")
        if (countryName.isEmpty() && flagName.isEmpty()) return

        fun findMatch(searchQuery: String): Int {
            if (searchQuery.isBlank()) return -1
            val name = searchQuery.uppercase(Locale.ROOT)
            var idx = nodes.indexOfFirst {
                val nodeName = it.name.uppercase(Locale.ROOT)
                nodeName == name || name.contains(nodeName) || nodeName.contains(name) ||
                (nodeName == "PHILIPPINES" && (name.contains("PH") || name.contains("PHIL") || name.contains(".PH") || name == "PH")) ||
                (nodeName == "SINGAPORE" && (name.contains("SG") || name.contains("SINGA") || name.contains(".SG") || name == "SG")) ||
                (nodeName == "JAPAN" && (name.contains("JP") || name.contains("JAPAN") || name.contains(".JP") || name == "JP")) ||
                (nodeName == "HONG KONG" && (name.contains("HK") || name.contains("HONGKONG") || name.contains(".HK") || name == "HK")) ||
                (nodeName == "SOUTH KOREA" && (name.contains("KOREA") || name.contains("KR"))) ||
                (nodeName == "TAIWAN" && (name.contains("TW") || name.contains(".TW") || name == "TW")) ||
                (nodeName == "VIETNAM" && (name.contains("VN") || name.contains(".VN") || name == "VN")) ||
                (nodeName == "THAILAND" && (name.contains("TH") || name.contains(".TH") || name == "TH")) ||
                (nodeName == "MALAYSIA" && (name.contains("MY") || name.contains(".MY") || name == "MY")) ||
                (nodeName == "INDONESIA" && (name.contains("ID") || name.contains(".ID") || name == "ID")) ||
                (nodeName == "INDIA" && (name.contains("IN") || name.contains(".IN") || name == "IN")) ||
                (nodeName == "BANGLADESH" && (name.contains("BD") || name.contains(".BD") || name == "BD")) ||
                (nodeName == "AUSTRALIA" && (name.contains("AU") || name.contains(".AU") || name == "AU")) ||
                (nodeName == "NEW ZEALAND" && (name.contains("NZ") || name.contains(".NZ") || name == "NZ")) ||
                (nodeName == "USA" && (name.contains("UNITED STATES") || name.contains("US ") || name.startsWith("US-") || name.contains(".US") || name.contains("AMERICA") || name == "US")) ||
                (nodeName == "CANADA" && (name.contains("CA") || name.contains(".CA") || name == "CA")) ||
                (nodeName == "MEXICO" && (name.contains("MX") || name.contains(".MX") || name == "MX")) ||
                (nodeName == "BRAZIL" && (name.contains("BR") || name.contains(".BR") || name == "BR")) ||
                (nodeName == "ARGENTINA" && (name.contains("AR") || name.contains(".AR") || name == "AR")) ||
                (nodeName == "CHILE" && (name.contains("CL") || name.contains(".CL") || name == "CL")) ||
                (nodeName == "COLOMBIA" && (name.contains("CO") || name.contains(".CO") || name == "CO")) ||
                (nodeName == "FINLAND" && (name.contains("FI") || name.contains(".FI") || name == "FI")) ||
                (nodeName == "UNITED KINGDOM" && (name.contains("UK ") || name.contains("GREAT BRITAIN") || name.startsWith("UK-") || name.contains(".UK") || name.contains("ENGLAND") || name == "UK")) ||
                (nodeName == "GERMANY" && (name.contains("DE") || name.contains(".DE") || name == "DE")) ||
                (nodeName == "FRANCE" && (name.contains("FR") || name.contains(".FR") || name == "FR")) ||
                (nodeName == "NETHERLANDS" && (name.contains("NL") || name.contains(".NL") || name.contains("HOLLAND") || name == "NL")) ||
                (nodeName == "ITALY" && (name.contains("IT") || name.contains(".IT") || name == "IT")) ||
                (nodeName == "SPAIN" && (name.contains("ES") || name.contains(".ES") || name == "ES")) ||
                (nodeName == "RUSSIA" && (name.contains("RU") || name.contains(".RU") || name == "RU")) ||
                (nodeName == "SWEDEN" && (name.contains("SE") || name.contains(".SE") || name == "SE")) ||
                (nodeName == "NORWAY" && (name.contains("NO") || name.contains(".NO") || name == "NO")) ||
                (nodeName == "SWITZERLAND" && (name.contains("CH") || name.contains(".CH") || name == "CH")) ||
                (nodeName == "TURKEY" && (name.contains("TR") || name.contains(".TR") || name == "TR")) ||
                (nodeName == "UAE" && (name.contains("AE") || name.contains(".AE") || name.contains("UNITED ARAB EMIRATES") || name == "AE")) ||
                (nodeName == "SAUDI ARABIA" && (name.contains("SAUDI") || name.contains("KSA") || name.contains(".SA") || name == "KSA")) ||
                (nodeName == "ISRAEL" && (name.contains("IL") || name.contains(".IL") || name == "IL")) ||
                (nodeName == "SOUTH AFRICA" && (name.contains("ZA") || name.contains(".ZA") || name == "ZA")) ||
                (nodeName == "EGYPT" && (name.contains("EG") || name.contains(".EG") || name == "EG")) ||
                (nodeName == "NIGERIA" && (name.contains("NG") || name.contains(".NG") || name == "NG"))
            }
            if (idx == -1) {
                val parts = name.split(Regex("[^A-Z]"))
                for (part in parts) {
                    if (part.length >= 2) {
                        idx = nodes.indexOfFirst { it.name.uppercase(Locale.ROOT).contains(part) }
                        if (idx != -1) break
                    }
                }
            }
            return idx
        }

        var newIndex = findMatch(countryName)
        if (newIndex == -1 && flagName.isNotEmpty()) {
            newIndex = findMatch(flagName)
        }

        if (newIndex != -1) {
            if (activeNode != newIndex) {
                activeNode = newIndex
                lineProgress = 0f
                Log.d("LiveMapView", "Match found! Node: ${nodes[newIndex].name} (Index: $newIndex)")
            }
        } else {
            Log.d("LiveMapView", "No match found for: $countryName / $flagName. Fallback to Singapore.")
            val sgIndex = nodes.indexOfFirst { it.name == "Singapore" }
            if (sgIndex != -1) {
                activeNode = sgIndex
                lineProgress = 0f
            }
        }
        postInvalidate()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val mapRect = RectF(width * 0.05f, height * 0.15f, width * 0.95f, height * 0.85f)
            var clickedOnNode = false
            for (i in nodes.indices) {
                val coords = getScreenCoords(nodes[i], mapRect)
                val dx = event.x - coords.x
                val dy = event.y - coords.y
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                if (distance < 45f) {
                    selectedNodeIndex = i
                    clickedOnNode = true
                    break
                }
            }
            if (!clickedOnNode) selectedNodeIndex = -1
            performClick()
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.TRANSPARENT) 
        val mapRect = RectF(width * 0.05f, height * 0.15f, width * 0.95f, height * 0.85f)

        if (width == 0 || height == 0) return

        worldMap?.let {
            canvas.drawBitmap(it, null, mapRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 180 })
        }

        pulse += 0.35f
        if (pulse > 1000f) pulse = 0f

        // 1. Draw Connection Line (From Own Country to VPN Country)
        if (activeNode != -1 && activeNode != homeNodeIndex) {
            val homeCoords = getScreenCoords(nodes[homeNodeIndex], mapRect)
            val activeCoords = getScreenCoords(nodes[activeNode], mapRect)

            linePhase -= 1.8f
            // Dash effect ay lilitaw lang kapag tapos na ang animation (progress == 1.0)
            if (lineProgress >= 1.0f) {
                linePaint.pathEffect = DashPathEffect(floatArrayOf(30f, 20f), linePhase)
            } else {
                linePaint.pathEffect = null
            }
            drawCurveLine(canvas, homeCoords.x, homeCoords.y, activeCoords.x, activeCoords.y, lineProgress)
        }

        // 2. Draw Nodes
        for (i in nodes.indices) {
            val coords = getScreenCoords(nodes[i], mapRect)

            when (i) {
                activeNode -> {
                    // --- VPN COUNTRY (Neon Green Radar Pulse + Flag) ---
                    val activePulse = (pulse * 2.5f % 100f)
                    val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#3BDE32")
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                        alpha = (200 * (1 - activePulse / 100f)).toInt()
                    }
                    canvas.drawCircle(coords.x, coords.y, 15f + (activePulse * 0.7f), radarPaint)

                    activeFlagBitmap?.let {
                        val flagSize = 25f // Bahagyang pinalaki
                        val destRect = RectF(
                            coords.x - flagSize,
                            coords.y - flagSize,
                            coords.x + flagSize,
                            coords.y + flagSize
                        )
                        canvas.drawBitmap(it, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))
                    } ?: run {
                        nodePaint.color = Color.parseColor("#3BDE32")
                        nodePaint.alpha = 255
                        canvas.drawCircle(coords.x, coords.y, 10f, nodePaint)
                    }

                    // textPaint.color = Color.parseColor("#3BDE32")
                    // canvas.drawText(nodes[i].name, coords.x, coords.y - 50f, textPaint)
                }
                homeNodeIndex -> {
                    // --- OWN COUNTRY (Professional Green Radar Pulse + Local Flag/Logo) ---
                    val homePulse = (pulse * 2.0f % 100f)
                    val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#3BDE32")
                        style = Paint.Style.STROKE
                        strokeWidth = 3.5f
                        alpha = (180 * (1 - homePulse / 100f)).toInt()
                    }
                    canvas.drawCircle(coords.x, coords.y, 15f + (homePulse * 0.6f), radarPaint)

                    homeFlagBitmap?.let {
                        val flagSize = 22f
                        val destRect = RectF(
                            coords.x - flagSize,
                            coords.y - flagSize,
                            coords.x + flagSize,
                            coords.y + flagSize
                        )
                        canvas.drawBitmap(it, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))
                    } ?: run {
                        nodePaint.color = Color.parseColor("#3BDE32")
                        nodePaint.alpha = 255
                        canvas.drawCircle(coords.x, coords.y, 9f, nodePaint)
                    }
                }
                else -> {
                    // --- INACTIVE NODES (Twinkling Stars) ---
                    val twinkle = (sin((pulse * 0.15f + i * 0.8f).toDouble()).toFloat() + 1f) / 2f
                    nodePaint.color = Color.parseColor("#94A3B8")
                    nodePaint.alpha = (100 + (twinkle * 130)).toInt()

                    val radius = 5.5f + (twinkle * 2.5f)
                    canvas.drawCircle(coords.x, coords.y, radius, nodePaint)
                }
            }
        }
    }

    private fun getScreenCoords(node: Node, rect: RectF): PointF {
        val x = rect.left + (node.lng + 180) * (rect.width() / 360f)
        val y = rect.top + (90 - node.lat) * (rect.height() / 180f)
        return PointF(x.toFloat(), y.toFloat())
    }

    private fun drawCurveLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, progress: Float) {
        if (progress <= 0f) return
        
        val path = Path()
        path.moveTo(x1, y1)
        
        // Dynamic Curve Height: Adjust curve based on distance to keep it within view
        val dist = Math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble()).toFloat()
        val midX = (x1 + x2) / 2
        val curveHeight = Math.min(dist / 2f, 100f)
        val midY = (y1 + y2) / 2 - curveHeight
        
        path.quadTo(midX, midY, x2, y2)

        val pathMeasure = PathMeasure(path, false)
        val partialPath = Path()
        pathMeasure.getSegment(0f, pathMeasure.length * progress, partialPath, true)
        
        canvas.drawPath(partialPath, linePaint)
        
        // Draw a leading particle (Meteor/Glow effect)
        if (progress > 0f) {
            val pos = FloatArray(2)
            pathMeasure.getPosTan(pathMeasure.length * progress, pos, null)
            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFFFFF") // White center
                style = Paint.Style.FILL
                setShadowLayer(20f, 0f, 0f, Color.parseColor("#3BDE32")) // Green glow
            }
            canvas.drawCircle(pos[0], pos[1], 10f, particlePaint)
            
            // Outer glow circle
            particlePaint.color = Color.parseColor("#3BDE32")
            particlePaint.alpha = 100
            canvas.drawCircle(pos[0], pos[1], 18f, particlePaint)
        }
    }

    override fun run() {
        while (running) {
            if (activeNode != -1 && lineProgress < 1.0f) {
                lineProgress += 0.02f
                if (lineProgress > 1.0f) lineProgress = 1.0f
            }
            postInvalidate()
            try { Thread.sleep(16) } catch (e: Exception) {}
        }
    }

    @SuppressLint("ThreadConstraint")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        thread = Thread(this).apply { start() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }
}
