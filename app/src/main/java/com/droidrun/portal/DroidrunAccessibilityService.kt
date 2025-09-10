package com.droidrun.portal

import android.accessibilityservice.AccessibilityService // 核心基类，用于创建后台监听系统事件、访问UI层次结构的无障碍服务。
import android.accessibilityservice.AccessibilityServiceInfo // 用于配置无障碍服务，指定监听的事件类型、反馈类型等。
import android.graphics.Rect // 矩形类，常用于获取UI元素在屏幕上的边界坐标。
import android.util.Log // 日志工具类，用于输出调试和错误信息到Logcat。
import android.view.Display // 代表显示设备（如屏幕），可获取屏幕尺寸、方向等信息。
import android.view.WindowManager // 窗口管理器，用于获取Display对象以及管理窗口。
import android.view.accessibility.AccessibilityEvent // 系统产生的无障碍事件，如窗口变化、点击等。
import android.view.accessibility.AccessibilityNodeInfo // UI层次结构中的节点信息，代表一个UI元素，可获取其属性和执行操作。
import android.view.accessibility.AccessibilityWindowInfo // 代表屏幕上的一个窗口的信息。
import com.droidrun.portal.model.ElementNode // 自定义模型类，可能用于封装或表示从AccessibilityNodeInfo提取的元素信息。
import com.droidrun.portal.model.PhoneState // 自定义模型类，可能用于存储或传递设备状态信息。
import android.os.Handler // 用于在特定线程（如主线程）执行代码或发送延迟/定时消息。
import android.os.Looper // 与消息循环相关的类，Handler通常需要与Looper配合使用。
import java.util.concurrent.atomic.AtomicBoolean // 线程安全的布尔值，适用于在多线程环境中控制状态标志。
import android.graphics.Bitmap // 位图类，用于处理图像数据，例如屏幕截图。
import android.util.Base64 // 提供Base64编码和解码功能，常用于将二进制数据（如图片）转换为字符串。
import java.io.ByteArrayOutputStream // 字节数组输出流，常用于将数据（如Bitmap）写入字节数组。
import java.util.concurrent.CompletableFuture // 用于异步编程，表示一个可能尚未完成的异步计算的结果。

// 注意：AccessibilityService 本身需要在 AndroidManifest.xml 中正确声明和配置才能工作。


/**
 * Droidrun 无障碍服务核心类。
 * 负责监听系统事件、获取界面元素、管理覆盖层和套接字服务器。
 */
class DroidrunAccessibilityService : AccessibilityService(), ConfigManager.ConfigChangeListener {

    companion object {
        private const val TAG = "DroidrunA11yService"
        private var instance: DroidrunAccessibilityService? = null
        // 定义元素的最小尺寸，过滤掉过小的元素
        private const val MIN_ELEMENT_SIZE = 5

        // 周期性更新常量
        // 每 250 毫秒更新一次元素信息
        private const val REFRESH_INTERVAL_MS = 250L
        // 帧之间最小时间（大约 60 FPS），防止更新过于频繁
        private const val MIN_FRAME_TIME_MS = 16L

        /**
         * 获取服务实例。
         */
        fun getInstance(): DroidrunAccessibilityService? = instance
    }

    // 覆盖层管理器
    private lateinit var overlayManager: OverlayManager
    // 屏幕边界
    private val screenBounds = Rect()
    // 配置管理器
    private lateinit var configManager: ConfigManager
    // 主线程 Handler，用于执行 UI 相关操作
    private val mainHandler = Handler(Looper.getMainLooper())
    // 套接字服务器实例
    private var socketServer: SocketServer? = null

    // 周期性更新状态
    private var isInitialized = false // 服务是否已初始化
    private val isProcessing = AtomicBoolean(false) // 是否正在处理元素刷新
    private var lastUpdateTime = 0L // 上次更新时间戳
    private var currentPackageName: String = "" // 当前活动应用包名
    // 当前可见元素列表（用于回收资源）
    private val visibleElements = mutableListOf<ElementNode>()

    /**
     * 服务创建时调用。
     * 初始化覆盖层管理器、屏幕边界、配置管理器和套接字服务器。
     */
    override fun onCreate() {
        super.onCreate()
        // 初始化覆盖层管理器
        overlayManager = OverlayManager(this)
        // 获取屏幕尺寸
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val windowMetrics = windowManager.currentWindowMetrics
        val bounds = windowMetrics.bounds
        screenBounds.set(0, 0, bounds.width(), bounds.height())

        // 初始化配置管理器
        configManager = ConfigManager.getInstance(this)
        configManager.addListener(this)

        // 初始化套接字服务器
        socketServer = SocketServer(this)

        isInitialized = true
    }

    /**
     * 服务连接成功时调用。
     * 配置无障碍服务参数，应用初始配置，启动周期性更新和套接字服务器。
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        // 显示覆盖层
        overlayManager.showOverlay()
        // 设置单例实例
        instance = this

        // 配置无障碍服务
        serviceInfo = AccessibilityServiceInfo().apply {
            // 监听所有类型的事件
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK

            // 监控所有应用包
            packageNames = null

            // 设置反馈类型
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // 设置标志位以获得更好的访问权限
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE

            // 启用截图功能 (API 34+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
            }
        }

        // 应用加载的配置
        applyConfiguration()

        // 开始周期性更新
        startPeriodicUpdates()

        // 如果启用，则启动套接字服务器
        startSocketServerIfEnabled()

        Log.d(TAG, "无障碍服务已连接并配置")
    }

    /**
     * 接收到无障碍事件时调用。
     * 处理窗口状态变化、内容变化和滚动事件，触发元素更新。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventPackage = event?.packageName?.toString() ?: ""

        // 检测应用包变化
        if (eventPackage.isNotEmpty() && eventPackage != currentPackageName && currentPackageName.isNotEmpty()) {
            resetOverlayState() // 重置覆盖层状态
        }

        if (eventPackage.isNotEmpty()) {
            currentPackageName = eventPackage
        }

        // 在相关事件触发时更新元素
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // 让周期性更新任务处理刷新，此处不直接处理
            }
        }
    }

    // 周期性更新任务
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 如果服务已初始化且覆盖层可见
            if (isInitialized && configManager.overlayVisible) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdateTime

                // 控制帧率，避免更新过于频繁
                if (timeSinceLastUpdate >= MIN_FRAME_TIME_MS) {
                    refreshVisibleElements() // 刷新可见元素
                    lastUpdateTime = currentTime
                }
            }
            // 安排下一次更新
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    /**
     * 启动周期性更新任务。
     */
    private fun startPeriodicUpdates() {
        lastUpdateTime = System.currentTimeMillis()
        mainHandler.postDelayed(updateRunnable, REFRESH_INTERVAL_MS)
        Log.d(TAG, "已启动周期性更新")
    }

    /**
     * 停止周期性更新任务。
     */
    private fun stopPeriodicUpdates() {
        mainHandler.removeCallbacks(updateRunnable)
        Log.d(TAG, "已停止周期性更新")
    }

    /**
     * 刷新当前屏幕可见的元素。
     * 这是核心逻辑，负责获取元素并更新覆盖层。
     */
    private fun refreshVisibleElements() {
        // 使用 AtomicBoolean 确保同一时间只有一个刷新任务在运行
        if (!isProcessing.compareAndSet(false, true)) {
            return // 已经在处理中，直接返回
        }

        try {
            // 如果当前包名为空，清空覆盖层并返回
            if (currentPackageName.isEmpty()) {
                overlayManager.clearElements()
                overlayManager.refreshOverlay()
                return
            }

            // 清除上一次的元素列表
            clearElementList()

            // 获取新的可见元素列表
            val elements = getVisibleElementsInternal()

            // 如果覆盖层可见且有元素，则更新覆盖层
            if (configManager.overlayVisible && elements.isNotEmpty()) {
                overlayManager.clearElements()

                elements.forEach { rootElement ->
                    // 将元素及其子元素添加到覆盖层
                    addElementAndChildrenToOverlay(rootElement, 0)
                }

                // 刷新覆盖层显示
                overlayManager.refreshOverlay()
            }

        } catch (e: Exception) {
            Log.e(TAG, "刷新可见元素时出错: ${e.message}", e)
        } finally {
            // 重置处理状态
            isProcessing.set(false)
        }
    }

    /**
     * 重置覆盖层状态，通常在应用切换时调用。
     */
    private fun resetOverlayState() {
        try {
            overlayManager.clearElements()
            overlayManager.refreshOverlay()
            clearElementList()
            Log.d(TAG, "因应用切换重置覆盖层状态")
        } catch (e: Exception) {
            Log.e(TAG, "重置覆盖层状态时出错: ${e.message}", e)
        }
    }

    /**
     * 清除并回收元素列表中的资源。
     */
    private fun clearElementList() {
        for (element in visibleElements) {
            try {
                // 回收 AccessibilityNodeInfo 资源
                element.nodeInfo.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "回收节点时出错: ${e.message}")
            }
        }
        visibleElements.clear()
    }

    /**
     * 应用当前配置。
     */
    private fun applyConfiguration() {
        mainHandler.post {
            try {
                val config = configManager.getCurrentConfiguration()
                if (config.overlayVisible) {
                    overlayManager.showOverlay()
                } else {
                    overlayManager.hideOverlay()
                }
                // 设置覆盖层偏移
                overlayManager.setPositionOffsetY(config.overlayOffset)
                Log.d(TAG, "已应用配置: overlayVisible=${config.overlayVisible}, overlayOffset=${config.overlayOffset}")
            } catch (e: Exception) {
                Log.e(TAG, "应用配置时出错: ${e.message}", e)
            }
        }
    }

    // 供 MainActivity 直接调用的公共方法

    /**
     * 设置覆盖层可见性。
     *
     * @param visible 是否可见
     * @return 操作是否成功
     */
    fun setOverlayVisible(visible: Boolean): Boolean {
        return try {
            configManager.overlayVisible = visible

            mainHandler.post {
                if (visible) {
                    overlayManager.showOverlay()
                    // 显示时立即刷新一次
                    refreshVisibleElements()
                } else {
                    overlayManager.hideOverlay()
                }
            }

            Log.d(TAG, "覆盖层可见性设置为: $visible")
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置覆盖层可见性时出错: ${e.message}", e)
            false
        }
    }

    /**
     * 获取覆盖层当前可见性。
     */
    fun isOverlayVisible(): Boolean = configManager.overlayVisible

    /**
     * 设置覆盖层垂直偏移。
     *
     * @param offset 偏移量
     * @return 操作是否成功
     */
    fun setOverlayOffset(offset: Int): Boolean {
        return try {
            configManager.overlayOffset = offset

            mainHandler.post {
                overlayManager.setPositionOffsetY(offset)
            }

            Log.d(TAG, "覆盖层偏移设置为: $offset")
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置覆盖层偏移时出错: ${e.message}", e)
            false
        }
    }

    /**
     * 获取覆盖层当前垂直偏移。
     */
    fun getOverlayOffset(): Int = configManager.overlayOffset

    /**
     * 获取当前可见元素列表。
     */
    fun getVisibleElements(): MutableList<ElementNode> {
        return getVisibleElementsInternal()
    }

    /**
     * 内部方法：获取当前可见元素列表。
     */
    private fun getVisibleElementsInternal(): MutableList<ElementNode> {
        val elements = mutableListOf<ElementNode>()
        // 索引计数器，用于给元素分配唯一编号
        val indexCounter = IndexCounter(1) // 从 1 开始编号

        // 获取当前活动窗口的根节点
        val rootNode = rootInActiveWindow ?: return elements
        // 查找所有可见元素
        val rootElement = findAllVisibleElements(rootNode, 0, null, indexCounter)
        rootElement?.let {
            // 收集根元素
            collectRootElements(it, elements)
        }

        // 存储元素列表以便后续清理
        synchronized(visibleElements) {
            clearElementList()
            visibleElements.addAll(elements)
        }

        return elements
    }

    /**
     * 收集根元素。
     */
    private fun collectRootElements(element: ElementNode, rootElements: MutableList<ElementNode>) {
        rootElements.add(element)
    }

    /**
     * 递归查找所有可见元素。
     *
     * @param node 当前 AccessibilityNodeInfo 节点
     * @param windowLayer 窗口层级
     * @param parent 父元素节点
     * @param indexCounter 全局索引计数器
     * @return 构建的 ElementNode，如果不可见则为 null
     */
    private fun findAllVisibleElements(
        node: AccessibilityNodeInfo,
        windowLayer: Int,
        parent: ElementNode?,
        indexCounter: IndexCounter
    ): ElementNode? {
        try {
            // 获取元素在屏幕上的边界
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // 判断元素是否在屏幕范围内
            val isInScreen = Rect.intersects(rect, screenBounds)
            // 判断元素是否有足够尺寸
            val hasSize = rect.width() > MIN_ELEMENT_SIZE && rect.height() > MIN_ELEMENT_SIZE

            var currentElement: ElementNode? = null

            // 如果元素可见且尺寸足够
            if (isInScreen && hasSize) {
                // 提取元素信息
                val text = node.text?.toString() ?: ""
                val contentDesc = node.contentDescription?.toString() ?: ""
                val className = node.className?.toString() ?: ""
                val viewId = node.viewIdResourceName ?: ""

                // 确定在覆盖层上显示的文本
                val displayText = when {
                    text.isNotEmpty() -> text
                    contentDesc.isNotEmpty() -> contentDesc
                    viewId.isNotEmpty() -> viewId.substringAfterLast('/')
                    else -> className.substringAfterLast('.')
                }

                // 确定元素类型
                val elementType = if (node.isClickable) {
                    "Clickable" // 可点击
                } else if (node.isCheckable) {
                    "Checkable" // 可勾选
                } else if (node.isEditable) {
                    "Input" // 输入框
                } else if (text.isNotEmpty()) {
                    "Text" // 文本
                } else if (node.isScrollable) {
                    "Container" // 容器
                } else {
                    "View" // 视图
                }

                // 生成元素唯一 ID
                val id = ElementNode.createId(rect, className.substringAfterLast('.'), displayText)

                // 创建 ElementNode 对象
                currentElement = ElementNode(
                    AccessibilityNodeInfo(node), // 复制节点信息
                    Rect(rect), // 复制边界信息
                    displayText,
                    className.substringAfterLast('.'), // 类名（简单类名）
                    windowLayer,
                    System.currentTimeMillis(), // 时间戳
                    id
                )

                // 分配唯一索引
                currentElement.overlayIndex = indexCounter.getNext()

                // 设置父子关系
                parent?.addChild(currentElement)
            }

            // 递归处理子节点
            for (i in 0 until node.childCount) {
                val childNode = node.getChild(i) ?: continue
                val childElement = findAllVisibleElements(childNode, windowLayer, currentElement, indexCounter)
                // 子元素已在上面的 parent?.addChild() 调用中添加到 currentElement
            }
//            Log.i(TAG, "没个节点的信息: $currentElement")
            return currentElement

        } catch (e: Exception) {
            Log.e(TAG, "findAllVisibleElements 中出错: ${e.message}", e)
            return null
        }
    }

    /**
     * 获取当前手机状态信息。
     */
    fun getPhoneState(): PhoneState {
        // 获取当前焦点元素
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        // 检测键盘是否可见
        val keyboardVisible = detectKeyboardVisibility()
        // 获取当前包名
        val currentPackage = rootInActiveWindow?.packageName?.toString()
        // 获取应用名称
        val appName = getAppName(currentPackage)

        return PhoneState(focusedNode, keyboardVisible, currentPackage, appName)
    }

    /**
     * 检测软键盘是否可见。
     */
    private fun detectKeyboardVisibility(): Boolean {
        try {
            val windows = windows
            if (windows != null) {
                // 检查是否存在输入法窗口
                val hasInputMethodWindow = windows.any { window -> window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                windows.forEach { it.recycle() } // 回收窗口信息
                return hasInputMethodWindow
            } else {
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 根据包名获取应用名称。
     */
    private fun getAppName(packageName: String?): String? {
        return try {
            if (packageName == null) return null

            val packageManager = packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取包名 $packageName 的应用名称时出错: ${e.message}")
            null
        }
    }

    // 辅助类：维护全局索引计数器
    private class IndexCounter(private var current: Int = 1) {
        fun getNext(): Int = current++
    }

    // 套接字服务器管理方法

    /**
     * 如果配置启用，则启动套接字服务器。
     */
    private fun startSocketServerIfEnabled() {
        if (configManager.socketServerEnabled) {
            startSocketServer()
        }
    }

    /**
     * 启动套接字服务器。
     */
    private fun startSocketServer() {
        socketServer?.let { server ->
            if (!server.isRunning()) {
                val port = configManager.socketServerPort
                val success = server.start(port)
                if (success) {
                    Log.i(TAG, "套接字服务器已在端口 $port 启动")
                } else {
                    Log.e(TAG, "无法在端口 $port 启动套接字服务器")
                }
            }
        }
    }

    /**
     * 停止套接字服务器。
     */
    private fun stopSocketServer() {
        socketServer?.let { server ->
            if (server.isRunning()) {
                server.stop()
                Log.i(TAG, "套接字服务器已停止")
            }
        }
    }

    /**
     * 获取套接字服务器状态。
     */
    fun getSocketServerStatus(): String {
        return socketServer?.let { server ->
            if (server.isRunning()) {
                "运行中，端口 ${server.getPort()}"
            } else {
                "已停止"
            }
        } ?: "未初始化"
    }

    /**
     * 获取 ADB 转发命令。
     */
    fun getAdbForwardCommand(): String {
        val port = configManager.socketServerPort
        return "adb forward tcp:$port tcp:$port"
    }

    // ConfigManager.ConfigChangeListener 实现

    /**
     * 配置监听器：覆盖层可见性变化。
     */
    override fun onOverlayVisibilityChanged(visible: Boolean) {
        // 已在 setOverlayVisible 方法中处理
    }

    /**
     * 配置监听器：覆盖层偏移变化。
     */
    override fun onOverlayOffsetChanged(offset: Int) {
        // 已在 setOverlayOffset 方法中处理
    }

    /**
     * 配置监听器：套接字服务器启用状态变化。
     */
    override fun onSocketServerEnabledChanged(enabled: Boolean) {
        if (enabled) {
            startSocketServer()
        } else {
            stopSocketServer()
        }
    }

    /**
     * 配置监听器：套接字服务器端口变化。
     */
    override fun onSocketServerPortChanged(port: Int) {
        // 如果服务器正在运行，则重启以使用新端口
        socketServer?.let { server ->
            if (server.isRunning()) {
                server.stop()
                val success = server.start(port)
                if (success) {
                    Log.i(TAG, "套接字服务器已在新端口 $port 重启")
                } else {
                    Log.e(TAG, "无法在新端口 $port 重启套接字服务器")
                }
            }
        }
    }

    // 截图功能

    /**
     * 获取 Base64 编码的屏幕截图。
     *
     * @param hideOverlay 截图前是否隐藏覆盖层
     * @return 包含 Base64 字符串或错误信息的 CompletableFuture
     */
    fun takeScreenshotBase64(hideOverlay: Boolean = true): CompletableFuture<String> {
        val future = CompletableFuture<String>()

        // 如果需要隐藏覆盖层，则临时禁用绘制
        val wasOverlayDrawingEnabled = if (hideOverlay) {
            val enabled = overlayManager.isDrawingEnabled()
            overlayManager.setDrawingEnabled(false)
            enabled
        } else {
            true
        }

        try {
            if (hideOverlay) {
                // 添加小延迟以确保覆盖层已隐藏
                mainHandler.postDelayed({
                    performScreenshotCapture(future, wasOverlayDrawingEnabled, hideOverlay)
                }, 100)
            } else {
                performScreenshotCapture(future, wasOverlayDrawingEnabled, hideOverlay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图时出错", e)
            future.complete("error: 截图失败: ${e.message}")

            // 发生异常时恢复覆盖层绘制状态
            if (hideOverlay) {
                overlayManager.setDrawingEnabled(wasOverlayDrawingEnabled)
            }
        }

        return future
    }

    /**
     * 执行截图捕获操作。
     */
    private fun performScreenshotCapture(future: CompletableFuture<String>, wasOverlayDrawingEnabled: Boolean, hideOverlay: Boolean) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY, // 默认显示屏
                // 使用主线程的类加载器创建单线程执行器
                mainHandler.looper.thread.contextClassLoader?.let {
                    java.util.concurrent.Executors.newSingleThreadExecutor()
                } ?: java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            // 从硬件缓冲区创建 Bitmap
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )

                            if (bitmap == null) {
                                Log.e(TAG, "从硬件缓冲区创建位图失败")
                                screenshotResult.hardwareBuffer.close()
                                future.complete("error: 从截图数据创建位图失败")
                                return
                            }

                            // 将 Bitmap 压缩为 PNG 格式的字节数组
                            val byteArrayOutputStream = ByteArrayOutputStream()
                            val compressionSuccess = bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)

                            if (!compressionSuccess) {
                                Log.e(TAG, "将位图压缩为 PNG 失败")
                                bitmap.recycle()
                                screenshotResult.hardwareBuffer.close()
                                byteArrayOutputStream.close()
                                future.complete("error: 将截图压缩为 PNG 格式失败")
                                return
                            }

                            // 将字节数组编码为 Base64 字符串
                            val byteArray = byteArrayOutputStream.toByteArray()
                            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                            // 清理资源
                            bitmap.recycle()
                            screenshotResult.hardwareBuffer.close()
                            byteArrayOutputStream.close()

                            future.complete(base64String)
                            Log.d(TAG, "截图成功捕获，大小: ${byteArray.size} 字节")
                        } catch (e: Exception) {
                            Log.e(TAG, "处理截图时出错", e)
                            try {
                                screenshotResult.hardwareBuffer.close()
                            } catch (closeException: Exception) {
                                Log.e(TAG, "关闭硬件缓冲区时出错", closeException)
                            }
                            future.complete("error: 处理截图失败: ${e.message}")
                        } finally {
                            // 恢复覆盖层绘制状态
                            if (hideOverlay) {
                                overlayManager.setDrawingEnabled(wasOverlayDrawingEnabled)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        // 根据错误码生成错误信息
                        val errorMessage = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "发生内部错误"
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截图间隔时间过短"
                            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "无效的显示屏"
                            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "无无障碍访问权限"
                            ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "安全窗口无法被捕获"
                            else -> "未知错误 (代码: $errorCode)"
                        }
                        Log.e(TAG, "截图失败: $errorMessage")
                        future.complete("error: 截图失败: $errorMessage")

                        // 恢复覆盖层绘制状态
                        if (hideOverlay) {
                            overlayManager.setDrawingEnabled(wasOverlayDrawingEnabled)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "截图时出错", e)
            future.complete("error: 截图失败: ${e.message}")

            // 发生异常时恢复覆盖层绘制状态
            if (hideOverlay) {
                overlayManager.setDrawingEnabled(wasOverlayDrawingEnabled)
            }
        }
    }

    /**
     * 服务被中断时调用。
     * 停止周期性更新和套接字服务器。
     */
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
        stopPeriodicUpdates()
        stopSocketServer()
    }

    /**
     * 服务销毁时调用。
     * 停止更新、服务器，清理资源。
     */
    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicUpdates()
        stopSocketServer()
        clearElementList()
        configManager.removeListener(this)
        instance = null
        Log.d(TAG, "无障碍服务已销毁")
    }

    /**
     * 将元素及其所有子元素添加到覆盖层。
     */
    private fun addElementAndChildrenToOverlay(element: ElementNode, depth: Int) {
        // 将当前元素添加到覆盖层
        overlayManager.addElement(
            text = element.text,
            rect = element.rect,
            type = element.className,
            index = element.overlayIndex
        )

        // 递归添加子元素
        for (child in element.children) {
            addElementAndChildrenToOverlay(child, depth + 1)
        }
    }
}



