// 包声明：表示当前类属于 com.droidrun.portal 包
// 包的作用类似于文件夹，用于组织代码并避免类名冲突
package com.droidrun.portal

// 导入Android上下文类：Context是Android应用的"环境"，提供访问系统资源和服务的能力
import android.content.Context
// 导入画布类：Canvas是绘图的"画板"，所有图形都在这上面绘制
import android.graphics.Canvas
// 导入颜色类：提供颜色常量和创建自定义颜色的方法
import android.graphics.Color
// 导入画笔类：控制绘图的样式，如颜色、线条粗细等
import android.graphics.Paint
// 导入像素格式类：定义图像的像素格式，如透明度设置
import android.graphics.PixelFormat
// 导入矩形类：用于表示矩形区域，存储上下左右坐标
import android.graphics.Rect
// 导入Handler类：用于在Android中处理消息和线程间通信，常用于更新UI
import android.os.Handler
// 导入Looper类：是消息循环器，确保Handler在正确的线程(通常是主线程)工作
import android.os.Looper
// 导入日志类：用于输出调试信息，帮助开发和排查问题
import android.util.Log
// 导入Gravity类：控制视图的对齐方式，如居中、靠左等
import android.view.Gravity
// 导入窗口管理器类：用于管理Android系统中的窗口，如创建悬浮窗
import android.view.WindowManager
// 导入帧布局类：一种简单的布局容器，视图可以堆叠显示
import android.widget.FrameLayout
// 导入意图类：用于Android组件间的通信，如启动活动、服务等
import android.content.Intent
// 导入视图基类：Android中所有可视化组件的父类
import android.view.View
// 导入原子布尔类：线程安全的布尔值，适合多线程环境下使用
import java.util.concurrent.atomic.AtomicBoolean

// 定义悬浮层管理类，用于创建和管理屏幕上的悬浮层
// 构造函数需要传入Context参数，用于获取系统服务和资源
class OverlayManager(private val context: Context) {
    // 获取窗口管理器实例，用于添加、移除悬浮层
    // 从上下文环境中获取系统的窗口管理服务
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    // 悬浮层视图对象，为空表示悬浮层未创建
    private var overlayView: OverlayView? = null
    // 创建主线程Handler，确保UI操作在主线程执行
    private val handler = Handler(Looper.getMainLooper())
    // 存储所有需要在悬浮层上绘制的元素信息
    private val elementRects = mutableListOf<ElementInfo>()
    // 标记悬浮层是否可见
    private var isOverlayVisible = false
    // 控制是否启用绘图功能的标志
    private var isDrawingEnabled = true // 临时禁用绘图的标志
    // 位置校正偏移量，用于微调元素位置
    private var positionCorrectionOffset = 0 // 默认校正偏移量
    // 元素索引计数器，用于为每个元素分配唯一索引
    private var elementIndexCounter = 0 // 为元素分配索引的计数器
    // 原子布尔值，标记悬浮层是否准备就绪，线程安全
    private val isOverlayReady = AtomicBoolean(false)
    // 悬浮层准备就绪后的回调函数
    private var onReadyCallback: (() -> Unit)? = null

    // 垂直偏移量，用于整体调整所有元素的垂直位置
    private var positionOffsetY = 0 // 默认偏移值

    // 伴生对象，包含静态常量和方法，全类共享
    companion object {
        // 日志标签，用于筛选该类的日志输出
        private const val TAG = "TOPVIEW_OVERLAY"
        // 重叠阈值，用于判断元素是否重叠的比例标准
        private const val OVERLAP_THRESHOLD = 0.5f // 较低的重叠阈值用于匹配

        // 定义8种视觉上明显不同的颜色方案
        private val COLOR_SCHEME = arrayOf(
            Color.rgb(0, 122, 255),    // 蓝色
            Color.rgb(255, 45, 85),    // 红色
            Color.rgb(52, 199, 89),    // 绿色
            Color.rgb(255, 149, 0),    // 橙色
            Color.rgb(175, 82, 222),   // 紫色
            Color.rgb(255, 204, 0),    // 黄色
            Color.rgb(90, 200, 250),   // 浅蓝色
            Color.rgb(88, 86, 214)     // 靛蓝色
        )
    }

    // 数据类，用于存储单个元素的信息
    data class ElementInfo(
        val rect: Rect,              // 元素的矩形区域
        val type: String,            // 元素类型
        val text: String,            // 元素文本
        val depth: Int = 0,          // 层级深度，用于追踪层级关系
        val color: Int = Color.GREEN,// 元素颜色，默认绿色
        val index: Int = 0           // 元素索引，用于标识元素
    )

    // 添加调整垂直偏移量的方法
    fun setPositionOffsetY(offsetY: Int) {
        // 更新垂直偏移量
        this.positionOffsetY = offsetY
        // 复制现有元素列表，避免修改原列表时出现问题
        val existingElements = ArrayList(elementRects)
        // 清空现有元素列表
        elementRects.clear()

        // 使用更新后的偏移量重新添加元素
        for (element in existingElements) {
            val originalRect = Rect(element.rect)
            // 通过移除旧偏移量调整回原始位置
            originalRect.offset(0, -positionOffsetY)
            // 使用新偏移量重新添加元素
            addElement(
                rect = originalRect,
                type = element.type,
                text = element.text,
                depth = element.depth,
                color = element.color
            )
        }

        // 刷新悬浮层以应用新的偏移量
        refreshOverlay()
    }

    // 添加获取当前偏移值的方法
    fun getPositionOffsetY(): Int {
        return positionOffsetY
    }

    // 设置悬浮层准备就绪后的回调函数
    fun setOnReadyCallback(callback: () -> Unit) {
        onReadyCallback = callback
        // 如果已经准备就绪，立即调用回调
        if (isOverlayReady.get()) {
            handler.post(callback)
        }
    }

    // 显示悬浮层的方法
    fun showOverlay() {
        // 如果悬浮层视图已存在
        if (overlayView != null) {
            Log.d(TAG, "悬浮层已存在，检查是否已附加")
            try {
                // 检查视图是否实际已附加到窗口
                overlayView?.parent ?: run {
                    Log.w(TAG, "悬浮层存在但未附加，重新创建")
                    overlayView = null
                    createAndAddOverlay()
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查悬浮层状态时出错: ${e.message}", e)
                overlayView = null
                createAndAddOverlay()
                return
            }
            // 标记悬浮层已准备就绪
            isOverlayReady.set(true)
            // 调用准备就绪回调
            onReadyCallback?.let { handler.post(it) }
            return
        }
        // 创建并添加悬浮层
        createAndAddOverlay()
    }

    // 私有方法，创建并添加悬浮层到窗口
    private fun createAndAddOverlay() {
        try {
            Log.d(TAG, "创建新的悬浮层")
            // 创建自定义悬浮层视图
            overlayView = OverlayView(context).apply {
                // 设置硬件加速
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            // 创建悬浮层布局参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,  // 宽度匹配屏幕
                WindowManager.LayoutParams.MATCH_PARENT,  // 高度匹配屏幕
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,  // 悬浮层类型
                // 悬浮层标志：不可聚焦、不可触摸、全屏布局、硬件加速
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT  // 透明像素格式
            )
            // 设置悬浮层对齐方式：左上角
            params.gravity = Gravity.TOP or Gravity.START

            // 在主线程中添加悬浮层
            handler.post {
                try {
                    // 将悬浮层添加到窗口
                    windowManager.addView(overlayView, params)
                    // 标记悬浮层可见
                    isOverlayVisible = true

                    // 延迟一小段时间设置就绪状态并通知回调，确保视图已布局完成
                    handler.postDelayed({
                        // 检查视图是否已正确附加
                        if (overlayView?.parent != null) {
                            isOverlayReady.set(true)
                            onReadyCallback?.let { it() }
                        } else {
                            Log.e(TAG, "延迟后悬浮层仍未正确附加")
                            // 如果未附加，尝试重新创建
                            hideOverlay()
                            showOverlay()
                        }
                    }, 500)
                } catch (e: Exception) {
                    Log.e(TAG, "添加悬浮层时出错: ${e.message}", e)
                    // 失败时清理资源
                    overlayView = null
                    isOverlayVisible = false
                    isOverlayReady.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮层时出错: ${e.message}", e)
            // 失败时清理资源
            overlayView = null
            isOverlayVisible = false
            isOverlayReady.set(false)
        }
    }

    // 隐藏悬浮层的方法
    fun hideOverlay() {
        // 在主线程中执行
        handler.post {
            try {
                // 如果悬浮层存在，则从窗口中移除
                overlayView?.let {
                    windowManager.removeView(it)
                    overlayView = null
                }
                // 更新状态标志
                isOverlayVisible = false
                isOverlayReady.set(false)
                Log.d(TAG, "悬浮层已移除")
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮层时出错: ${e.message}", e)
            }
        }
    }

    // 清除所有元素的方法
    fun clearElements() {
        // 清空元素列表
        elementRects.clear()
        // 重置元素索引计数器
        elementIndexCounter = 0 // 清除元素时重置索引计数器
        // 刷新悬浮层
        refreshOverlay()
    }

    // 添加元素到悬浮层的方法
    fun addElement(rect: Rect, type: String, text: String, depth: Int = 0, color: Int = Color.GREEN, index: Int? = null) {
        // 校正矩形位置
        val correctedRect = correctRectPosition(rect)
        // 确定元素索引，如果未指定则使用计数器值并递增
        val elementIndex = index ?: elementIndexCounter++
        // 根据索引从颜色方案中分配颜色
        val colorFromScheme = COLOR_SCHEME[elementIndex % COLOR_SCHEME.size]
        // 将元素添加到列表
        elementRects.add(ElementInfo(correctedRect, type, text, depth, colorFromScheme, elementIndex))
        // 不在每次添加时刷新，避免添加多个元素时过度重绘
    }

    // 校正矩形位置以更好地匹配实际UI元素
    private fun correctRectPosition(rect: Rect): Rect {
        val correctedRect = Rect(rect)

        // 应用垂直偏移量向上移动矩形
        correctedRect.offset(0, positionOffsetY)

        return correctedRect
    }

    // 刷新悬浮层的方法
    fun refreshOverlay() {
        // 在主线程中执行
        handler.post {
            // 如果悬浮层视图为空，则显示悬浮层
            if (overlayView == null) {
                Log.e(TAG, "无法刷新悬浮层 - 视图为空")
                showOverlay()
            }
            // 触发视图重绘
            overlayView?.invalidate()
        }
    }

    // 获取悬浮层中元素数量的方法
    fun getElementCount(): Int {
        return elementRects.size
    }

    // 临时禁用/启用悬浮层绘图的方法，用于干净截图
    fun setDrawingEnabled(enabled: Boolean) {
        isDrawingEnabled = enabled
        refreshOverlay()
    }

    // 检查绘图是否启用的方法
    fun isDrawingEnabled(): Boolean {
        return isDrawingEnabled
    }

    // 临时隐藏悬浮层以获取干净截图的便捷方法
    fun withOverlayHidden(action: () -> Unit) {
        // 保存当前绘图状态
        val wasEnabled = isDrawingEnabled
        try {
            // 禁用绘图
            setDrawingEnabled(false)
            // 小延迟确保悬浮层已重绘且没有元素
            handler.postDelayed({
                // 执行传入的操作
                action()
                // 恢复绘图状态
                setDrawingEnabled(wasEnabled)
            }, 50)
        } catch (e: Exception) {
            // 即使操作失败，也要恢复状态
            setDrawingEnabled(wasEnabled)
            throw e
        }
    }

    // 内部类，自定义悬浮层视图，负责实际绘图工作
    inner class OverlayView(context: Context) : FrameLayout(context) {
        // 绘制矩形边框的画笔
        private val boxPaint = Paint().apply {
            style = Paint.Style.STROKE  // 描边样式
            strokeWidth = 2f  // 较细的边框
            isAntiAlias = true  // 抗锯齿，使线条更平滑
            // 启用硬件加速功能
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }

        // 绘制文本的画笔
        private val textPaint = Paint().apply {
            color = Color.WHITE  // 白色文本
            textSize = 32f  // 较大的文本大小，提高可见性
            isAntiAlias = true  // 抗锯齿
            // 启用硬件加速功能
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }

        // 绘制文本背景的画笔
        private val textBackgroundPaint = Paint().apply {
            // 颜色将动态设置为与边框颜色匹配
            style = Paint.Style.FILL  // 填充样式
            // 启用硬件加速功能
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }

        // 初始化块，视图创建时执行
        init {
            // 允许视图执行自定义绘图
            setWillNotDraw(false)
            // 设置背景为透明
            setBackgroundColor(Color.TRANSPARENT)
            // 启用硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        // 重写绘图方法，当视图需要绘制时调用
        override fun onDraw(canvas: Canvas) {
            try {
                // 检查画布是否为空
                if (canvas == null) {
                    Log.e(TAG, "onDraw中画布为空")
                    return
                }

                // 如果悬浮层不可见或绘图被禁用，则不绘制
                if (!isOverlayVisible || !isDrawingEnabled) {
                    if (!isDrawingEnabled) {
                        Log.d(TAG, "悬浮层绘图已禁用，跳过绘制")
                    } else {
                        Log.d(TAG, "悬浮层不可见，跳过绘制")
                    }
                    return
                }

                // 调用父类的onDraw方法
                super.onDraw(canvas)

                // 记录开始绘制时间
                val startTime = System.currentTimeMillis()

                // 如果没有元素要绘制，且处于调试模式，则绘制调试矩形
                if (elementRects.isEmpty()) {
                    if (isDebugging()) {
                        drawDebugRect(canvas)
                    }
                    return
                }

                // 创建本地副本以防止并发修改异常
                val elementsToDraw = ArrayList(elementRects)

                // 按层级深度排序元素，确定绘制顺序
                val sortedElements = elementsToDraw.sortedBy { it.depth }

                // 绘制每个元素
                for (elementInfo in sortedElements) {
                    drawElement(canvas, elementInfo)
                }

                // 计算绘制耗时（未使用，可用于性能优化）
                val drawTime = System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                Log.e(TAG, "onDraw中出错: ${e.message}", e)
            }
        }

        // 绘制单个元素的方法
        private fun drawElement(canvas: Canvas, elementInfo: ElementInfo) {
            try {
                // 确保矩形尺寸有效
                if (elementInfo.rect.width() <= 0 || elementInfo.rect.height() <= 0) {
                    Log.w(TAG, "元素 ${elementInfo.index} 的矩形尺寸无效")
                    return
                }

                // 重要：设置当前元素的颜色
                val elementColor = elementInfo.color

                // 确保颜色具有完全的透明度（不透明）
                val colorWithAlpha = Color.argb(
                    255,  // 完全不透明
                    Color.red(elementColor),    // 红色分量
                    Color.green(elementColor),  // 绿色分量
                    Color.blue(elementColor)    // 蓝色分量
                )

                // 设置边框画笔颜色
                boxPaint.color = colorWithAlpha

                // 设置文本背景颜色，与边框颜色匹配但有一些透明度
                // 设置文本背景画笔的颜色。使用 Color.argb 方法可以精确控制颜色的透明度（Alpha）和RGB值。
                textBackgroundPaint.color = Color.argb(
                    200, // Alpha 通道值，设置为200 (大约78%不透明度)，使背景色半透明，
                    // 这样可以看到被背景覆盖的元素边框或其他内容，同时又能清晰地突出文本。
                    Color.red(elementColor),    // 从当前元素的主要颜色 (elementColor) 中提取红色分量，
                    // 确保背景色与元素的边框/标识色保持一致或协调。
                    Color.green(elementColor),  // 从当前元素的主要颜色 (elementColor) 中提取绿色分量。
                    Color.blue(elementColor)    // 从当前元素的主要颜色 (elementColor) 中提取蓝色分量。
                )

                // 绘制矩形边框
                canvas.drawRect(elementInfo.rect, boxPaint)

                // 准备要显示的索引文本
                val displayText = "${elementInfo.index}"
                // 计算文本宽度
                val textWidth = textPaint.measureText(displayText)
                // 文本高度（较大的值以匹配增大的文本大小）
                val textHeight = 36f

                // 计算文本位置：右上角，带有小的内边距
                val textX = elementInfo.rect.right - textWidth - 4f  // 右边缘4px内边距
                val textY = elementInfo.rect.top + textHeight  // 顶部带有一些内边距

                // 计算文本背景矩形
                val backgroundPadding = 4f  // 背景内边距
                // 创建一个矩形对象，用于定义文本背景的区域
                val backgroundRect = Rect(
                    // 计算背景矩形的左边坐标：
                    // 文本绘制的起始X坐标 (textX) 减去左边的内边距 (backgroundPadding)
                    (textX - backgroundPadding).toInt(),

                    // 计算背景矩形的顶边坐标：
                    // 文本绘制的基线Y坐标 (textY) 减去文本的高度 (textHeight)，得到文本顶部的位置，
                    // 这将作为背景矩形的顶部
                    (textY - textHeight).toInt(),

                    // 计算背景矩形的右边坐标：
                    // 文本绘制的起始X坐标 (textX) 加上文本的宽度 (textWidth)，得到文本的右边缘，
                    // 再加上右边的内边距 (backgroundPadding)
                    (textX + textWidth + backgroundPadding).toInt(),

                    // 计算背景矩形的底边坐标：
                    // 文本绘制的基线Y坐标 (textY) 加上底边的内边距 (backgroundPadding)。
                    // 注意：这里没有减去 textHeight，这意味着背景的底部会延伸到文本基线下方一点。
                    // 这通常是为了确保背景能完全覆盖文本的下降部分（例如 'g', 'j', 'p', 'q', 'y' 的下垂部分）。
                    (textY + backgroundPadding).toInt()
                )

                // 绘制文本背景和文本
                // 使用指定的画笔 (textBackgroundPaint) 在画布上绘制文本的背景矩形 (backgroundRect)
                canvas.drawRect(backgroundRect, textBackgroundPaint)

                // 使用指定的画笔 (textPaint) 在画布上绘制文本 (displayText)。
                // 绘制的起始点是 (textX, textY - backgroundPadding)。
                // textX 是文本的左上角X坐标。
                // textY 是文本的基线Y坐标，减去 backgroundPadding 可以让文本整体稍微上移，
                // 使其在背景矩形中垂直居中或更美观。
                canvas.drawText(
                    displayText,           // 要绘制的文本内容
                    textX,                 // 文本绘制的起始X坐标（通常是左上角的X）
                    textY - backgroundPadding, // 文本绘制的起始Y坐标（基线Y坐标减去一点偏移）
                    textPaint              // 用于绘制文本的画笔
                )
            } catch (e: Exception) {
                Log.e(TAG, "绘制元素 ${elementInfo.index} 时出错: ${e.message}", e)
            }
        }

        // 绘制调试矩形的方法
        private fun drawDebugRect(canvas: Canvas) {
            try {
                // 获取屏幕宽度和高度
                val screenWidth = width
                val screenHeight = height
                // 创建测试矩形（屏幕中间的矩形）
                val testRect = Rect(
                    screenWidth / 4,          // 左
                    screenHeight / 4,         // 上
                    (screenWidth * 3) / 4,    // 右
                    (screenHeight * 3) / 4    // 下
                )
                // 设置调试矩形颜色为绿色
                boxPaint.color = Color.GREEN
                // 绘制调试矩形
                canvas.drawRect(testRect, boxPaint)
                Log.d(TAG, "绘制测试矩形在 $testRect")
            } catch (e: Exception) {
                Log.e(TAG, "绘制调试矩形时出错: ${e.message}", e)
            }
        }

        // 判断是否处于调试模式
        private fun isDebugging(): Boolean {
            return false // 设置为true以显示测试矩形
        }
    }
}



