package com.droidrun.portal

// 导入 Android 基础类和接口
import android.R // Android 系统资源 (通常不需要显式导入，除非引用系统资源)
import android.content.ContentProvider // 内容提供者基类，用于向其他应用提供结构化数据
import android.content.ContentValues // 用于存储键值对的容器，常用于 ContentProvider 的 insert 和 update 操作
import android.content.UriMatcher // 用于匹配 URI 路径，简化路由逻辑
import android.content.pm.ApplicationInfo // 包含有关应用程序的信息，如是否为系统应用
import android.content.pm.PackageInfo // 包含有关已安装包的详细信息，如版本号
import android.database.Cursor // 数据库查询结果集的接口
import android.database.MatrixCursor // Cursor 的一个简单实现，用于构建自定义查询结果
import android.graphics.Rect // 矩形类，用于表示 UI 元素的边界
import android.net.Uri // 统一资源标识符，用于标识 ContentProvider 中的数据
import android.os.Build // 提供有关当前 Android 平台版本的信息
import android.util.Log // 日志工具类
import android.view.accessibility.AccessibilityNodeInfo // 表示无障碍服务看到的 UI 层次结构中的一个节点

// 导入 JSON 处理库
import org.json.JSONArray // 用于处理 JSON 数组
import org.json.JSONException // JSON 处理时可能抛出的异常
import org.json.JSONObject // 用于处理 JSON 对象

// 导入 Kotlin 和 AndroidX 扩展
import androidx.core.net.toUri // Kotlin 扩展函数，方便地将 String 转换为 Uri

// 导入 Android 基础类 (续)
import android.os.Bundle // 用于在组件之间传递数据的键值对容器
import android.content.Intent // 用于在组件（如 Activity、Service）之间传递消息和启动组件
import android.content.pm.PackageManager // 提供对系统中已安装包信息的访问
import android.content.pm.ResolveInfo // 包含系统解析 Intent 后返回的信息，如匹配的 Activity

// 导入项目内部类
import com.droidrun.portal.model.ElementNode // 自定义模型类，表示一个 UI 元素节点
import com.droidrun.portal.model.PhoneState // 自定义模型类，表示设备当前状态
// 注意：DroidrunAccessibilityService 和 DroidrunKeyboardIME 类未在此文件中导入，
// 但代码中使用了它们，因此它们必须在同一个包内或已通过其他方式导入。

/**
 * DroidrunContentProvider
 *
 * 这个类是一个自定义的 ContentProvider，它充当了外部应用（或同一应用的其他部分）
 * 与 Droidrun 无障碍服务及键盘服务进行交互的桥梁。
 * 它通过定义特定的 URI 端点来暴露功能，允许外部通过 ContentResolver
 * 执行查询 (query)、插入 (insert) 等操作来获取信息或触发动作。
 */
class DroidrunContentProvider : ContentProvider() {
    companion object {
        private const val TAG = "DroidrunContentProvider" // 用于 Log 的标签
        private const val AUTHORITY = "com.droidrun.portal" // ContentProvider 的唯一标识符 (Authority)

        // 定义 URI 匹配码，用于区分不同的端点
        private const val A11Y_TREE = 1       // 获取无障碍节点树
        private const val PHONE_STATE = 2     // 获取设备状态
        private const val PING = 3            // 简单的健康检查
        private const val KEYBOARD_ACTIONS = 4 // 执行键盘相关操作
        private const val STATE = 5           // 获取组合状态（节点树 + 设备状态）
        private const val OVERLAY_OFFSET = 6  // 更新悬浮窗偏移量
        private const val PACKAGES = 7        // 获取已安装的可启动应用包列表

        // UriMatcher 用于根据 URI 路径匹配到对应的码
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "a11y_tree", A11Y_TREE)
            addURI(AUTHORITY, "phone_state", PHONE_STATE)
            addURI(AUTHORITY, "ping", PING)
            addURI(AUTHORITY, "keyboard/*", KEYBOARD_ACTIONS) // * 通配符匹配具体动作
            addURI(AUTHORITY, "state", STATE)
            addURI(AUTHORITY, "overlay_offset", OVERLAY_OFFSET)
            addURI(AUTHORITY, "packages", PACKAGES)
        }
    }

    /**
     * 当 ContentProvider 首次被创建时调用。
     * @return true 表示初始化成功。
     */
    override fun onCreate(): Boolean {
        Log.d(TAG, "DroidrunContentProvider created")
        return true
    }

    /**
     * 处理外部应用的查询请求 (ContentResolver.query)。
     * 根据 URI 路径分发到不同的处理函数，并将结果（通常是 JSON 字符串）封装在 Cursor 中返回。
     * @param uri 请求的 URI。
     * @param projection 查询的列（在此实现中未使用）。
     * @param selection 查询条件（在此实现中未使用）。
     * @param selectionArgs 查询条件参数（在此实现中未使用）。
     * @param sortOrder 排序方式（在此实现中未使用）。
     * @return 包含查询结果的 Cursor。
     */
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        // 使用 MatrixCursor 创建一个简单的结果集，只有一列 "result"
        val cursor = MatrixCursor(arrayOf("result"))

        try {
            // 根据 URI 匹配码决定调用哪个函数处理请求
            val result = when (uriMatcher.match(uri)) {
                A11Y_TREE -> getAccessibilityTree() // 获取并返回无障碍节点树 JSON
                PHONE_STATE -> getPhoneState()     // 获取并返回设备状态 JSON
                PING -> createSuccessResponse("pong") // 返回 "pong" 表示存活
                STATE -> getCombinedState()        // 获取并返回组合状态 JSON
                PACKAGES -> getInstalledPackagesJson() // 获取并返回已安装包列表 JSON
                else -> createErrorResponse("Unknown endpoint: ${uri.path}") // 未知端点
            }
            // 将处理结果添加到 Cursor 的一行中
            cursor.addRow(arrayOf(result))

        } catch (e: Exception) {
            // 捕获并记录处理过程中发生的任何异常
            Log.e(TAG, "Query execution failed", e)
            // 将错误信息作为结果添加到 Cursor
            cursor.addRow(arrayOf(createErrorResponse("Execution failed: ${e.message}")))
        }
        // 返回包含结果或错误的 Cursor
        return cursor
    }

    /**
     * 处理外部应用的插入请求 (ContentResolver.insert)。
     * 主要用于触发操作，如执行键盘动作或更新设置。
     * @param uri 请求的 URI。
     * @param values 包含操作参数的 ContentValues。
     * @return 一个 Uri，通常包含操作结果的状态和消息（编码在 URI 中）。
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return when (uriMatcher.match(uri)) {
            // 分发到处理键盘动作的函数
            KEYBOARD_ACTIONS -> executeKeyboardAction(uri, values)
            // 分发到处理悬浮窗偏移量更新的函数
            OVERLAY_OFFSET -> updateOverlayOffset(uri, values)
            // 不支持的插入端点
            else -> "content://$AUTHORITY/result?status=error&message=${Uri.encode("Unsupported insert endpoint: ${uri.path}")}".toUri()
        }
    }

    /**
     * 执行键盘相关操作。
     * @param uri 包含具体操作类型（如 "input", "clear", "key"）的 URI。
     * @param values 包含操作所需参数的 ContentValues。
     * @return 一个 Uri，编码了操作的成功或失败状态及消息。
     */
    private fun executeKeyboardAction(uri: Uri, values: ContentValues?): Uri? {
        // 检查参数是否为空
        if (values == null) {
            return "content://$AUTHORITY/result?status=error&message=No values provided".toUri()
        }

        try {
            // 从 URI 路径的最后一段获取操作类型
            val action = uri.lastPathSegment ?: return "content://$AUTHORITY/result?status=error&message=No action specified".toUri()

            // 根据操作类型调用相应的处理函数
            val result = when (action) {
                "input" -> performKeyboardInputBase64(values) // 执行 Base64 文本输入
                "clear" -> performKeyboardClear()             // 清除文本
                "key" -> performKeyboardKey(values)           // 发送按键事件
                else -> "error: Unknown keyboard action: $action" // 未知操作
            }

            // 根据操作结果构建返回的 URI
            return if (result.startsWith("success")) {
                "content://$AUTHORITY/result?status=success&message=${Uri.encode(result)}".toUri()
            } else {
                "content://$AUTHORITY/result?status=error&message=${Uri.encode(result)}".toUri()
            }

        } catch (e: Exception) {
            // 捕获并记录异常
            Log.e(TAG, "Keyboard action execution failed", e)
            return "content://$AUTHORITY/result?status=error&message=${Uri.encode("Execution failed: ${e.message}")}".toUri()
        }
    }

    /**
     * 更新悬浮窗的垂直偏移量。
     * @param uri 请求的 URI。
     * @param values 包含 "offset" 参数的 ContentValues。
     * @return 一个 Uri，编码了操作的成功或失败状态及消息。
     */
    private fun updateOverlayOffset(uri: Uri, values: ContentValues?): Uri? {
        // 检查参数是否为空
        if (values == null) {
            return "content://$AUTHORITY/result?status=error&message=No values provided".toUri()
        }

        try {
            // 从 ContentValues 中获取偏移量
            val offset = values.getAsInteger("offset")
                ?: return "content://$AUTHORITY/result?status=error&message=No offset provided".toUri()

            // 获取无障碍服务实例
            val accessibilityService = DroidrunAccessibilityService.getInstance()
                ?: return "content://$AUTHORITY/result?status=error&message=Accessibility service not available".toUri()

            // 调用服务的方法设置偏移量
            val success = accessibilityService.setOverlayOffset(offset)

            // 根据结果构建返回的 URI
            return if (success) {
                "content://$AUTHORITY/result?status=success&message=${Uri.encode("Overlay offset updated to $offset")}".toUri()
            } else {
                "content://$AUTHORITY/result?status=error&message=Failed to update overlay offset".toUri()
            }

        } catch (e: Exception) {
            // 捕获并记录异常
            Log.e(TAG, "Failed to update overlay offset", e)
            return "content://$AUTHORITY/result?status=error&message=${Uri.encode("Execution failed: ${e.message}")}".toUri()
        }
    }

    /**
     * 获取当前屏幕可见的无障碍节点树，并将其序列化为 JSON 字符串。
     * @return 成功时返回包含节点树 JSON 的字符串，失败时返回错误信息 JSON 字符串。
     */
    private fun getAccessibilityTree(): String {
        // 获取无障碍服务实例
        val accessibilityService = DroidrunAccessibilityService.getInstance()
            ?: return createErrorResponse("Accessibility service not available")
        return try {
            // 调用服务方法获取可见元素列表
            val treeJson = accessibilityService.getVisibleElements().map { element ->
                buildElementNodeJson(element) // 递归构建每个元素的 JSON 对象
            }
            // 使用专门处理节点树的函数返回成功响应
            createSuccessResponse节点树(treeJson.toString())
        } catch (e: Exception) {
            // 捕获并记录异常
            Log.e(TAG, "Failed to get accessibility tree", e)
            createErrorResponse("Failed to get accessibility tree: ${e.message}")
        }
    }

    /**
     * 将 ElementNode 对象递归地转换为 JSONObject。
     * @param element 要转换的 ElementNode。
     * @return 对应的 JSONObject。
     */
    private fun buildElementNodeJson(element: ElementNode): JSONObject {
        return JSONObject().apply {
            put("index", element.overlayIndex) // 元素索引
            put("resourceId", element.nodeInfo.viewIdResourceName ?: "") // 资源 ID
            put("className", element.className) // 类名
            put("text", element.text) // 显示文本
            // 格式化边界矩形坐标
            put("bounds", "${element.rect.left}, ${element.rect.top}, ${element.rect.right}, ${element.rect.bottom}")

            // 递归处理子元素
            val childrenArray = JSONArray()
            element.children.forEach { child ->
                childrenArray.put(buildElementNodeJson(child))
            }
            put("children", childrenArray) // 添加子元素数组
        }
    }

    /**
     * 获取当前设备状态，并将其序列化为 JSON 字符串。
     * @return 成功时返回包含设备状态 JSON 的字符串，失败时返回错误信息 JSON 字符串。
     */
    private fun getPhoneState(): String {
        // 获取无障碍服务实例
        val accessibilityService = DroidrunAccessibilityService.getInstance()
            ?: return createErrorResponse("Accessibility service not available")
        return try {
            // 调用服务方法获取 PhoneState 对象，并构建 JSON
            val phoneState = buildPhoneStateJson(accessibilityService.getPhoneState())
            createSuccessResponse(phoneState.toString())
        } catch (e: Exception) {
            // 捕获并记录异常 (注：日志消息似乎有误，应为获取 PhoneState 失败)
            Log.e(TAG, "Failed to get phone state", e)
            createErrorResponse("Failed to get phone state: ${e.message}")
        }
    }

    /**
     * 将 PhoneState 对象转换为 JSONObject。
     * @param phoneState 要转换的 PhoneState。
     * @return 对应的 JSONObject。
     */
    private fun buildPhoneStateJson(phoneState: PhoneState) =
        JSONObject().apply {
            put("currentApp", phoneState.appName) // 当前应用名称
            put("packageName", phoneState.packageName) // 当前应用包名
            put("keyboardVisible", phoneState.keyboardVisible) // 键盘是否可见
            // 构建当前焦点元素的信息
            put("focusedElement", JSONObject().apply {
                put("text", phoneState.focusedElement?.text) // 焦点元素文本
                put("className", phoneState.focusedElement?.className) // 焦点元素类名
                put("resourceId", phoneState.focusedElement?.viewIdResourceName ?: "") // 焦点元素资源 ID
            })
        }

    /**
     * 获取组合状态（节点树 + 设备状态），并将其序列化为 JSON 字符串。
     * @return 成功时返回包含组合状态 JSON 的字符串，失败时返回错误信息 JSON 字符串。
     */
    private fun getCombinedState(): String {
        // 获取无障碍服务实例
        val accessibilityService = DroidrunAccessibilityService.getInstance()
            ?: return createErrorResponse("Accessibility service not available")

        return try {
            // 获取无障碍节点树 JSON
            val treeJson = accessibilityService.getVisibleElements().map { element ->
                buildElementNodeJson(element)
            }

            // 获取设备状态 JSON
            val phoneStateJson = buildPhoneStateJson(accessibilityService.getPhoneState())

            // 将两者合并到一个顶层 JSON 对象中
            val combinedState = JSONObject().apply {
                put("a11y_tree", JSONArray(treeJson)) // 节点树数组
                put("phone_state", phoneStateJson)   // 设备状态对象
            }

            createSuccessResponse(combinedState.toString())
        } catch (e: Exception) {
            // 捕获并记录异常
            Log.e(TAG, "Failed to get combined state", e)
            createErrorResponse("Failed to get combined state: ${e.message}")
        }
    }

    // 注意：performTextInput 函数在此文件中定义但似乎未被使用（KEYBOARD_ACTIONS 路由到了 performKeyboardInputBase64）。
    // 它展示了另一种通过无障碍服务直接在焦点元素上设置文本的方法（使用 ACTION_SET_TEXT）。
    // 代码逻辑清晰，此处省略详细注释。

    /**
     * 通过自定义键盘服务 (DroidrunKeyboardIME) 执行 Base64 编码文本的输入。
     * @param values 包含 "base64_text" 和可选 "append" 参数的 ContentValues。
     * @return 描述操作结果的字符串 ("success: ..." 或 "error: ...")。
     */
    private fun performKeyboardInputBase64(values: ContentValues): String {
        // 获取 Base64 编码的文本
        val base64Text = values.getAsString("base64_text") ?: return "error: no text provided"
        // 获取是否追加的标志，默认为 false (替换)
        val append = values.getAsBoolean("append") ?: false

        // 检查键盘服务是否可用
        return if (DroidrunKeyboardIME.getInstance() != null) {
            // 调用键盘服务的方法执行输入
            val ok = DroidrunKeyboardIME.getInstance()!!.inputB64Text(base64Text, append)
            if (ok) "success: input done (append=$append)" else "error: input failed"
        } else {
            "error: IME not active" // 键盘服务未激活
        }
    }

    /**
     * 通过自定义键盘服务清除当前焦点输入框的文本。
     * @return 描述操作结果的字符串。
     */
    private fun performKeyboardClear(): String {
        // 获取键盘服务实例
        val keyboardIME = DroidrunKeyboardIME.getInstance()
            ?: return "error: DroidrunKeyboardIME not active or available"

        // 检查是否有输入连接（即是否聚焦在输入框）
        if (!keyboardIME.hasInputConnection()) {
            return "error: No input connection available - keyboard may not be focused on an input field"
        }

        // 调用服务方法清除文本
        return if (keyboardIME.clearText()) {
            "success: Text cleared via keyboard"
        } else {
            "error: Failed to clear text via keyboard"
        }
    }

    /**
     * 通过自定义键盘服务发送一个按键事件。
     * @param values 包含 "key_code" 参数的 ContentValues。
     * @return 描述操作结果的字符串。
     */
    private fun performKeyboardKey(values: ContentValues): String {
        // 获取键盘服务实例
        val keyboardIME = DroidrunKeyboardIME.getInstance()
            ?: return "error: DroidrunKeyboardIME not active or available"

        // 检查是否有输入连接
        if (!keyboardIME.hasInputConnection()) {
            return "error: No input connection available - keyboard may not be focused on an input field"
        }

        // 获取按键码
        val keyCode = values.getAsInteger("key_code")
            ?: return "error: No key_code provided"

        // 调用服务方法发送按键事件
        return if (keyboardIME.sendKeyEventDirect(keyCode)) {
            "success: Key event sent via keyboard - code: $keyCode"
        } else {
            "error: Failed to send key event via keyboard"
        }
    }

    /**
     * 枚举所有已安装的、具有启动器 Activity 的应用包信息，并序列化为 JSON 字符串。
     * @return 成功时返回包含包列表 JSON 的字符串，失败时返回错误信息 JSON 字符串。
     */
    private fun getInstalledPackagesJson(): String {
        // 获取 PackageManager
        val pm = context?.packageManager ?: return createErrorResponse("PackageManager unavailable")

        return try {
            // 创建一个 Intent 来查找所有启动器应用
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            // 查询匹配的 ResolveInfo 列表 (需要处理不同 Android 版本的 API 差异)
            val resolvedApps: List<ResolveInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION") // 抑制旧 API 警告
                    pm.queryIntentActivities(mainIntent, 0)
                }

            // 创建一个 JSONArray 来存储所有包的信息
            val arr = JSONArray()

            // 遍历每个 ResolveInfo
            for (resolveInfo in resolvedApps) {
                // 获取 PackageInfo (可能抛出异常)
                val pkgInfo = try {
                    pm.getPackageInfo(resolveInfo.activityInfo.packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    continue // 如果找不到包信息，则跳过
                }

                // 获取 ApplicationInfo
                val appInfo = resolveInfo.activityInfo.applicationInfo
                // 创建一个 JSONObject 来存储当前包的信息
                val obj = JSONObject()

                obj.put("packageName", pkgInfo.packageName) // 包名
                obj.put("label", resolveInfo.loadLabel(pm).toString()) // 应用名称
                obj.put("versionName", pkgInfo.versionName ?: JSONObject.NULL) // 版本名 (可能为 null)

                // 获取版本码 (需要处理不同 Android 版本的 API 差异)
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                obj.put("versionCode", versionCode) // 版本码

                // 判断是否为系统应用
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                obj.put("isSystemApp", isSystem) // 是否系统应用

                // 将当前包的 JSONObject 添加到总 JSONArray 中
                arr.put(obj)
            }

            // 创建顶层 JSON 对象，包含状态、数量和包列表
            val root = JSONObject()
            root.put("status", "success")
            root.put("count", arr.length())
            root.put("packages", arr)

            root.toString() // 返回格式化的 JSON 字符串

        } catch (e: Exception) {
            // 捕获并记录异常
            Log.e(TAG, "Failed to enumerate launchable apps", e)
            createErrorResponse("Failed to enumerate launchable apps: ${e.message}")
        }
    }

    /**
     * 创建一个表示操作成功的 JSON 响应字符串。
     * @param data 成功时返回的数据（字符串形式）。
     * @return JSON 格式的响应字符串。
     */
    private fun createSuccessResponse(data: String): String {
        return JSONObject().apply {
            put("status", "success")
            put("message", data)
        }.toString()
    }

    /**
     * 专门为无障碍节点树响应创建成功的 JSON 字符串。
     * 它确保 "message" 字段包含的是一个真正的 JSON 数组，而不是字符串。
     * @param data 节点树的 JSON 字符串表示（由 JSONArray.toString() 生成）。
     * @return 格式正确的 JSON 响应字符串。
     */
    private fun createSuccessResponse节点树(data: String): String {
        val jsonObject = JSONObject()
        try {
            // 尝试将传入的字符串 data 解析为 JSONArray
            val jsonArray = JSONArray(data)
            jsonObject.put("status", "success")
            // 将解析后的 JSONArray 作为 "message" 的值
            jsonObject.put("message", jsonArray)
        } catch (e: JSONException) {
            // 如果解析失败（data 不是有效的 JSON 数组），则将其作为普通字符串处理
            Log.w(TAG, "Data is not a valid JSON array, treating as string", e)
            jsonObject.put("status", "success")
            jsonObject.put("message", data)
        }
        // toString(2) 用于格式化输出，使其在日志或调试时更易读
        return jsonObject.toString(2)
    }

    /**
     * 创建一个表示操作失败的 JSON 响应字符串。
     * @param error 错误信息。
     * @return JSON 格式的错误响应字符串。
     */
    private fun createErrorResponse(error: String): String {
        return JSONObject().apply {
            put("status", "error")
            put("message", error)
        }.toString()
    }

    // 以下方法未实现具体功能，返回默认值。
    // ContentProvider 需要实现这些方法，即使不使用它们。
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
    override fun getType(uri: Uri): String? = null // 返回给定 URI 的 MIME 类型
}
