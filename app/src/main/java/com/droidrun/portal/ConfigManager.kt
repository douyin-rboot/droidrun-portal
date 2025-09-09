// 声明此 Kotlin 文件属于 com.droidrun.portal 包
package com.droidrun.portal

// 导入 Android 开发中用于访问应用偏好设置 (SharedPreferences) 的相关类
import android.content.Context
import android.content.SharedPreferences

/**
 * Droidrun Portal 的中心化配置管理器
 * 负责处理 SharedPreferences 操作，并为配置管理提供一个清晰的 API
 * (这是一个多行注释，用于描述类的功能)
 */
class ConfigManager private constructor(private val context: Context) {
    // 'class ConfigManager' 定义一个名为 ConfigManager 的类。
    // 'private constructor' 表示这个类的主构造函数是私有的，不能从类外部直接调用 'new ConfigManager()'。
    // '(private val context: Context)' 是主构造函数的参数。
    // 'private' 意味着 'context' 参数在类内部被声明为一个私有属性。
    // 'val' 表示 'context' 是一个只读属性（一旦赋值就不能改变）。
    // ': Context' 指定 'context' 参数的类型是 Android 的 Context 对象，通常用于访问应用级资源和服务。

    // 伴生对象 (Companion Object)，用于存放与类本身相关的常量和静态方法
    // 在 Kotlin 中，它提供了一种定义类级别的属性和函数的方式，类似于 Java 的 static 成员。
    companion object {
        // 定义共享偏好设置文件的名称常量
        private const val PREFS_NAME = "droidrun_config"
        // 定义表示悬浮窗可见性的键的常量
        private const val KEY_OVERLAY_VISIBLE = "overlay_visible"
        // 定义表示悬浮窗偏移量的键的常量
        private const val KEY_OVERLAY_OFFSET = "overlay_offset"
        // 定义表示 Socket 服务器是否启用的键的常量
        private const val KEY_SOCKET_SERVER_ENABLED = "socket_server_enabled"
        // 定义表示 Socket 服务器端口的键的常量
        private const val KEY_SOCKET_SERVER_PORT = "socket_server_port"
        // 定义悬浮窗偏移量的默认值常量
        private const val DEFAULT_OFFSET = 0
        // 定义 Socket 服务器端口的默认值常量
        private const val DEFAULT_SOCKET_PORT = 8080

        // 使用 @Volatile 注解声明一个可变的、线程安全的单例实例变量
        // '@Volatile' 确保了多线程环境下对 INSTANCE 变量的可见性。
        // 'private' 限制其作用域仅在 companion object 内部。
        // '? = null' 表示 INSTANCE 可以为 null (可空类型)。
        @Volatile
        private var INSTANCE: ConfigManager? = null

        // 定义一个公共的静态方法（在 companion object 中）用于获取 ConfigManager 的单例实例
        // 'fun getInstance(context: Context): ConfigManager' 声明了一个名为 getInstance 的函数，
        // 它接受一个 Context 参数并返回一个 ConfigManager 类型的对象。
        fun getInstance(context: Context): ConfigManager {
            // 返回已存在的实例（如果非空），否则执行创建逻辑
            // 'return INSTANCE ?: ...' 使用 Elvis 操作符。如果 INSTANCE 不为 null，则直接返回 INSTANCE；
            // 否则，执行 Elvis 操作符后面的表达式。
            return INSTANCE ?: synchronized(this) {
                // 'synchronized(this) { ... }' 是 Kotlin 中实现同步代码块的方式，
                // 确保在同一时刻只有一个线程可以执行大括号内的代码，防止多线程下创建多个实例。
                // 'this' 指的是当前的 companion object。

                // 再次检查 INSTANCE 是否为 null（双重检查锁定模式的一部分）
                // 'INSTANCE ?: ...' 再次使用 Elvis 操作符检查。
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
                // 'ConfigManager(context.applicationContext)' 调用私有构造函数创建 ConfigManager 的新实例。
                // 'context.applicationContext' 获取应用的全局上下文，避免内存泄漏。
                // '.also { INSTANCE = it }' 是一个作用域函数，它会执行大括号内的 lambda 表达式，
                // 并将创建的实例 (it) 赋值给 INSTANCE 变量，然后返回该实例。
            }
        }
    }

    // 声明并初始化一个私有的 SharedPreferences 实例，用于持久化存储配置
    // 'private' 限制访问范围为类内部。
    // 'val' 表示 sharedPrefs 是一个只读属性。
    // '= context.getSharedPreferences(...)' 调用 Context 的 getSharedPreferences 方法来获取实例。
    // 'PREFS_NAME' 是之前定义的偏好设置文件名。
    // 'Context.MODE_PRIVATE' 指定偏好设置文件只能被当前应用访问。
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- 配置属性及其访问器 ---

    // 悬浮窗可见性配置属性
    // 'var overlayVisible: Boolean' 声明一个可变 (var) 属性 overlayVisible，类型为 Boolean。
    // 自定义 getter (get) 和 setter (set) 来操作 SharedPreferences。
    var overlayVisible: Boolean
        // 'get()' 定义自定义的 getter。
        // 'sharedPrefs.getBoolean(KEY_OVERLAY_VISIBLE, true)' 从 SharedPreferences 中读取键为 KEY_OVERLAY_VISIBLE 的布尔值。
        // 如果该键不存在，则返回默认值 true。
        get() = sharedPrefs.getBoolean(KEY_OVERLAY_VISIBLE, true)
        // 'set(value)' 定义自定义的 setter。
        // 'value' 是赋给 overlayVisible 的新值。
        // 'sharedPrefs.edit()' 获取一个 SharedPreferences.Editor 实例用于修改。
        // '.putBoolean(KEY_OVERLAY_VISIBLE, value)' 将新的布尔值存入。
        // '.apply()' 异步提交更改，不阻塞当前线程。
        set(value) {
            sharedPrefs.edit().putBoolean(KEY_OVERLAY_VISIBLE, value).apply()
        }

    // 悬浮窗偏移量配置属性
    // 逻辑与 overlayVisible 类似，但处理的是 Int 类型数据。
    // 默认值使用 DEFAULT_OFFSET 常量。
    var overlayOffset: Int
        get() = sharedPrefs.getInt(KEY_OVERLAY_OFFSET, DEFAULT_OFFSET)
        set(value) {
            sharedPrefs.edit().putInt(KEY_OVERLAY_OFFSET, value).apply()
        }

    // Socket 服务器启用状态配置属性
    // 逻辑与 overlayVisible 类似，但处理的是 Boolean 类型数据。
    // 默认值为 true。
    var socketServerEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_SOCKET_SERVER_ENABLED, true)
        set(value) {
            sharedPrefs.edit().putBoolean(KEY_SOCKET_SERVER_ENABLED, value).apply()
        }

    // Socket 服务器端口配置属性
    // 逻辑与 overlayVisible 类似，但处理的是 Int 类型数据。
    // 默认值使用 DEFAULT_SOCKET_PORT 常量。
    var socketServerPort: Int
        get() = sharedPrefs.getInt(KEY_SOCKET_SERVER_PORT, DEFAULT_SOCKET_PORT)
        set(value) {
            sharedPrefs.edit().putInt(KEY_SOCKET_SERVER_PORT, value).apply()
        }

    // --- 配置变更监听器 ---

    // 定义一个接口 ConfigChangeListener，用于监听配置项的变化
    // 'interface ConfigChangeListener' 声明一个接口。
    interface ConfigChangeListener {
        // 定义当悬浮窗可见性改变时调用的函数
        fun onOverlayVisibilityChanged(visible: Boolean)
        // 定义当悬浮窗偏移量改变时调用的函数
        fun onOverlayOffsetChanged(offset: Int)
        // 定义当 Socket 服务器启用状态改变时调用的函数
        fun onSocketServerEnabledChanged(enabled: Boolean)
        // 定义当 Socket 服务器端口改变时调用的函数
        fun onSocketServerPortChanged(port: Int)
    }

    // 使用一个可变集合 (mutableSetOf) 来存储所有注册的监听器实例
    // 'private' 限制访问范围为类内部。
    // 'val' 表示 listeners 是一个只读属性，但集合本身的内容是可变的。
    // '<ConfigChangeListener>' 指定集合中元素的类型是 ConfigChangeListener 接口的实现。
    private val listeners = mutableSetOf<ConfigChangeListener>()

    // 添加一个监听器到监听器集合中
    // 'fun addListener(listener: ConfigChangeListener)' 声明一个公共函数 addListener。
    // 'listener: ConfigChangeListener' 是要添加的监听器实例。
    fun addListener(listener: ConfigChangeListener) {
        // 'listeners.add(listener)' 将监听器添加到 Set 集合中。
        // Set 会自动处理重复添加的情况。
        listeners.add(listener)
    }

    // 从监听器集合中移除一个监听器
    // 'fun removeListener(listener: ConfigChangeListener)' 声明一个公共函数 removeListener。
    fun removeListener(listener: ConfigChangeListener) {
        // 'listeners.remove(listener)' 将指定的监听器从 Set 集合中移除。
        listeners.remove(listener)
    }

    // --- 带通知的设置方法 ---

    // 设置悬浮窗可见性并通知所有监听器
    // 'fun setOverlayVisibleWithNotification(visible: Boolean)' 声明一个公共函数。
    fun setOverlayVisibleWithNotification(visible: Boolean) {
        // 'overlayVisible = visible' 调用上面定义的 overlayVisible 属性的 setter 方法，
        // 将新值保存到 SharedPreferences。
        overlayVisible = visible
        // 'listeners.forEach { ... }' 遍历 listeners 集合中的每一个监听器。
        // 'it' 是遍历过程中当前的监听器实例。
        // 'it.onOverlayVisibilityChanged(visible)' 调用监听器的 onOverlayVisibilityChanged 方法，并传递新值。
        listeners.forEach { it.onOverlayVisibilityChanged(visible) }
    }

    // 设置悬浮窗偏移量并通知所有监听器
    // 逻辑与 setOverlayVisibleWithNotification 类似。
    fun setOverlayOffsetWithNotification(offset: Int) {
        overlayOffset = offset
        listeners.forEach { it.onOverlayOffsetChanged(offset) }
    }

    // 设置 Socket 服务器启用状态并通知所有监听器
    // 逻辑与 setOverlayVisibleWithNotification 类似。
    fun setSocketServerEnabledWithNotification(enabled: Boolean) {
        socketServerEnabled = enabled
        listeners.forEach { it.onSocketServerEnabledChanged(enabled) }
    }

    // 设置 Socket 服务器端口并通知所有监听器
    // 逻辑与 setOverlayVisibleWithNotification 类似。
    fun setSocketServerPortWithNotification(port: Int) {
        socketServerPort = port
        listeners.forEach { it.onSocketServerPortChanged(port) }
    }

    // --- 批量配置更新 ---

    // 提供一个方法来批量更新多个配置项
    // 'fun updateConfiguration(...)' 声明一个公共函数。
    // 所有参数都是可空类型 (Int?, Boolean?) 并带有默认值 null，
    // 这意味着调用时可以选择性地更新部分或全部配置。
    fun updateConfiguration(
        overlayVisible: Boolean? = null,
        overlayOffset: Int? = null,
        socketServerEnabled: Boolean? = null,
        socketServerPort: Int? = null
    ) {
        // 获取一个 SharedPreferences.Editor 实例用于批量修改
        val editor = sharedPrefs.edit()
        // 声明一个标志位，用于跟踪是否有任何配置被更改
        var hasChanges = false

        // 'overlayVisible?.let { ... }' 使用安全调用操作符 (?) 和 let 函数。
        // 如果 overlayVisible 参数不为 null，则执行大括号内的 lambda 表达式。
        // 'it' 在 lambda 中代表 overlayVisible 的非空值。
        overlayVisible?.let {
            editor.putBoolean(KEY_OVERLAY_VISIBLE, it) // 将新值放入编辑器
            hasChanges = true // 标记有更改发生
        }

        // 对 overlayOffset 执行相同的操作
        overlayOffset?.let {
            editor.putInt(KEY_OVERLAY_OFFSET, it)
            hasChanges = true
        }

        // 对 socketServerEnabled 执行相同的操作
        socketServerEnabled?.let {
            editor.putBoolean(KEY_SOCKET_SERVER_ENABLED, it)
            hasChanges = true
        }

        // 对 socketServerPort 执行相同的操作
        socketServerPort?.let {
            editor.putInt(KEY_SOCKET_SERVER_PORT, it)
            hasChanges = true
        }

        // 如果有任何配置被更改
        if (hasChanges) {
            // '.apply()' 异步提交所有更改
            editor.apply()

            // 通知所有监听器具体的变更项
            // 再次使用 ?.let 检查每个参数是否非空，如果非空则通知对应的所有监听器
            overlayVisible?.let { listeners.forEach { listener -> listener.onOverlayVisibilityChanged(it) } }
            overlayOffset?.let { listeners.forEach { listener -> listener.onOverlayOffsetChanged(it) } }
            socketServerEnabled?.let { listeners.forEach { listener -> listener.onSocketServerEnabledChanged(it) } }
            socketServerPort?.let { listeners.forEach { listener -> listener.onSocketServerPortChanged(it) } }
        }
    }

    // --- 获取完整配置 ---

    // 定义一个数据类 (data class) Configuration 来封装所有当前配置项
    // 'data class Configuration(...)' 声明一个数据类，Kotlin 会自动生成 equals(), hashCode(), toString() 等方法。
    data class Configuration(
        // 声明四个只读属性，对应四种配置项
        val overlayVisible: Boolean,
        val overlayOffset: Int,
        val socketServerEnabled: Boolean,
        val socketServerPort: Int
    )

    // 提供一个方法获取当前所有配置的快照
    // 'fun getCurrentConfiguration(): Configuration' 声明一个公共函数，返回 Configuration 数据类实例。
    fun getCurrentConfiguration(): Configuration {
        // 直接使用已定义的属性 getter 来获取当前值，并用它们创建一个新的 Configuration 对象返回
        return Configuration(
            overlayVisible = overlayVisible, // 调用 overlayVisible 的 getter
            overlayOffset = overlayOffset,   // 调用 overlayOffset 的 getter
            socketServerEnabled = socketServerEnabled, // 调用 socketServerEnabled 的 getter
            socketServerPort = socketServerPort      // 调用 socketServerPort 的 getter
        )
    }
}
