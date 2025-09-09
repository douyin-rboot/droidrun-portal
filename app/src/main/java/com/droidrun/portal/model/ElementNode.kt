// 指定此 Kotlin 文件所属的包，用于代码组织和命名空间管理。
package com.droidrun.portal.model

// 导入 Android 图形库中的 Rect 类，用于表示矩形区域（位置和大小）。
import android.graphics.Rect
// 导入 Android 无障碍服务中的 AccessibilityNodeInfo 类，它封装了屏幕 UI 元素的可访问性信息。
import android.view.accessibility.AccessibilityNodeInfo
// 从 Kotlin 标准库导入 max 函数，用于获取两个数中的较大值。
import kotlin.math.max
// 从 Kotlin 标准库导入 min 函数，用于获取两个数中的较小值。
import kotlin.math.min

/**
 * Represents a UI element detected by the accessibility service
 * 文档注释：描述 ElementNode 类的用途，表示由无障碍服务检测到的 UI 元素。
 */
// 定义一个数据类 (data class) ElementNode，Kotlin 会自动生成 equals, hashCode, toString 等方法。
data class ElementNode(
    // val 表示只读属性 nodeInfo，存储与该 UI 元素关联的原始 AccessibilityNodeInfo 对象。
    val nodeInfo: AccessibilityNodeInfo,
    // val 表示只读属性 rect，存储该 UI 元素在屏幕上的边界矩形 (位置和大小)。
    val rect: Rect,
    // val 表示只读属性 text，存储该 UI 元素的文本内容（如果有的话）。
    val text: String,
    // val 表示只读属性 className，存储该 UI 元素的类名（例如 "android.widget.Button"）。
    val className: String,
    // val 表示只读属性 windowLayer，存储该 UI 元素所在的窗口层级（Z 轴顺序）。
    val windowLayer: Int,
    // var 表示可变属性 creationTime，存储该 ElementNode 对象的创建时间戳（毫秒），可以被修改。
    var creationTime: Long,
    // val 表示只读属性 id，存储该 UI 元素的唯一标识符。
    val id: String,
    // var 表示可变属性 parent，默认值为 null，用于存储指向其父 ElementNode 的引用。
    var parent: ElementNode? = null,
    // val 表示只读属性 children，默认值为一个空的可变列表，用于存储其子 ElementNode 对象。
    val children: MutableList<ElementNode> = mutableListOf(),
    // var 表示可变属性 clickableIndex，默认值为 -1，用于存储该元素在可点击元素列表中的索引。
    var clickableIndex: Int = -1,
    // var 表示可变属性 nestingLevel，默认值为 0，用于存储该元素在 UI 层次结构中的嵌套级别（深度）。
    var nestingLevel: Int = 0,
    // var 表示可变属性 semanticParentId，默认值为 null，用于存储语义上的父元素 ID（可能与结构父元素不同）。
    var semanticParentId: String? = null,
    // var 表示可变属性 overlayIndex，默认值为 -1，用于存储该元素在叠加层中显示的确切索引。
    var overlayIndex: Int = -1 // Store the exact index shown in the overlay // 注释：存储在叠加层中显示的确切索引。
) {
    // 定义伴生对象 (Companion Object)，用于存放与 ElementNode 类相关的常量和静态方法。
    companion object {
        // private const val 定义一个私有的、编译时常量 FADE_DURATION_MS，值为 60000L 毫秒（即 60 秒）。
        private const val FADE_DURATION_MS = 60000L // Time to fade from weight 1.0 to 0.0 (60 seconds) // 注释：权重从 1.0 衰减到 0.0 所需的时间（60 秒）。

        /**
         * Creates a unique ID for an element based on its properties
         * 文档注释：描述 createId 方法的功能，根据元素的属性创建唯一 ID。
         */
        // 定义一个公共的、静态的函数 createId，用于生成元素的唯一 ID。
        fun createId(rect: Rect, className: String, text: String): String {
            // 使用字符串模板和属性组合生成唯一 ID：
            // - rect.toShortString() 获取矩形的简短字符串表示。
            // - className 是元素的类名。
            // - text.take(20) 取文本的前 20 个字符（如果文本不足 20 个字符，则取全部）。
            // 这些部分用下划线连接起来形成最终的 ID 字符串。
            return "${rect.toShortString()}_${className}_${text.take(20)}"
        }
    }

    /**
     * Calculates the current weight of this element based on its age
     * Returns a value between 0.0 and 1.0
     * 文档注释：描述 calculateWeight 方法的功能和返回值范围。
     */
    // 定义一个公共方法 calculateWeight，用于根据元素的年龄计算其当前权重。
    fun calculateWeight(): Float {
        // 获取当前系统时间戳（毫秒）。
        val now = System.currentTimeMillis()
        // 计算元素的年龄：当前时间减去创建时间。
        val age = now - creationTime
        // 计算权重：
        // 1. age.toFloat() / FADE_DURATION_MS.toFloat() 计算年龄占衰减时间的比例。
        // 2. 1f - (...) 计算剩余权重比例。
        // 3. min(1f, ...) 确保权重最大为 1.0。
        // 4. max(0f, ...) 确保权重最小为 0.0。
        // 这样，新创建的元素权重为 1.0，随着时间推移线性递减，FADE_DURATION_MS 后变为 0.0。
        return max(0f, min(1f, 1f - (age.toFloat() / FADE_DURATION_MS.toFloat())))
    }

    /**
     * Checks if this element overlaps with another element
     * 文档注释：描述 overlaps 方法的功能。
     */
    // 定义一个公共方法 overlaps，接收另一个 ElementNode 对象作为参数。
    fun overlaps(other: ElementNode): Boolean {
        // 使用 Android SDK 提供的 Rect.intersects 静态方法，检查 this.rect 和 other.rect 是否相交。
        // 如果相交则返回 true，否则返回 false。
        return Rect.intersects(this.rect, other.rect)
    }

    // 定义一个公共方法 contains，检查此元素的矩形区域是否完全包含另一个元素的矩形区域。
    fun contains(other: ElementNode): Boolean {
        // 调用 this.rect 对象的 contains 方法，传入 other.rect 进行包含性检查。
        // 如果 this.rect 完全包含 other.rect，则返回 true，否则返回 false。
        return this.rect.contains(other.rect)
    }

    // 定义一个公共方法 isClickable，检查此元素是否可点击。
    fun isClickable(): Boolean {
        // 直接返回与此 ElementNode 关联的 AccessibilityNodeInfo 对象的 isClickable 属性值。
        // 如果 AccessibilityNodeInfo.isClickable 为 true，则此方法返回 true，否则返回 false。
        return nodeInfo.isClickable
    }

    // 定义一个公共方法 isText，检查此元素是否为纯文本元素。
    fun isText(): Boolean {
        // 检查条件：text 属性不为空 (text.isNotEmpty()) 且 nodeInfo 不可点击 (!nodeInfo.isClickable)。
        // 同时满足这两个条件才认为是纯文本元素。
        return text.isNotEmpty() && !nodeInfo.isClickable
    }

    // Calculate nesting level (depth in the hierarchy)
    // 注释：计算嵌套级别（在层次结构中的深度）。
    // 定义一个公共方法 calculateNestingLevel，用于计算并返回此节点的嵌套深度。
    fun calculateNestingLevel(): Int {
        // 检查 nestingLevel 属性是否已经被计算过（大于 0）。
        // 如果已计算，则直接返回缓存的值，避免重复计算。
        if (nestingLevel > 0) {
            return nestingLevel
        }

        // 初始化一个临时变量 current，指向当前 ElementNode 实例 (this)。
        var current = this
        // 初始化嵌套级别计数器 level 为 0。
        var level = 0

        // 开始循环，向上遍历父节点，直到到达根节点（parent 为 null）。
        // 当 current.parent 不为 null 时，循环继续。
        while (current.parent != null) {
            // 每向上一级，嵌套级别计数器 level 自增 1。
            level++
            // 将 current 更新为其父节点。使用 !! 操作符断言 parent 不为 null（因为 while 条件已检查）。
            current = current.parent!!
        }

        // 将计算得到的嵌套级别 level 缓存到 nestingLevel 属性中，供后续调用使用。
        nestingLevel = level
        // 返回计算得到的嵌套级别。
        return level
    }

    // Get the root ancestor
    // 注释：获取根祖先节点。
    // 定义一个公共方法 getRootAncestor，用于查找并返回此节点的根祖先节点。
    fun getRootAncestor(): ElementNode {
        // 初始化一个临时变量 current，指向当前 ElementNode 实例 (this)。
        var current = this
        // 开始循环，向上遍历父节点，直到到达根节点（parent 为 null）。
        // 当 current.parent 不为 null 时，循环继续。
        while (current.parent != null) {
            // 将 current 更新为其父节点。使用 !! 操作符断言 parent 不为 null。
            current = current.parent!!
        }
        // 循环结束后，current 指向的就是根祖先节点，将其返回。
        return current
    }

    // Add a child node
    // 注释：添加一个子节点。
    // 定义一个公共方法 addChild，用于将一个 ElementNode 添加为当前节点的子节点。
    fun addChild(child: ElementNode) {
        // 检查要添加的子节点 child 是否已经存在于当前节点的 children 列表中。
        // 如果不存在于列表中，则执行添加操作。
        if (!children.contains(child)) {
            // 将子节点 child 添加到当前节点的 children 列表末尾。
            children.add(child)
            // 将子节点 child 的 parent 属性设置为当前节点 (this)，建立父子关系。
            child.parent = this
        }
    }

    // Remove a child node
    // 注释：移除一个子节点。
    // 定义一个公共方法 removeChild，用于从当前节点的子节点列表中移除指定的子节点。
    fun removeChild(child: ElementNode) {
        // 从当前节点的 children 列表中移除指定的子节点 child。
        children.remove(child)
        // 将被移除的子节点 child 的 parent 属性设置为 null，断开其与父节点的引用关系。
        child.parent = null
    }

    // Get all descendants (children, grandchildren, etc.)
    // 注释：获取所有后代节点（子节点、孙节点等）。
    // 定义一个公共方法 getAllDescendants，用于获取并返回当前节点的所有后代节点列表。
    fun getAllDescendants(): List<ElementNode> {
        // 创建一个可变列表 descendants，用于存储所有找到的后代节点。
        val descendants = mutableListOf<ElementNode>()
        // 遍历当前节点的直接子节点列表 children。
        for (child in children) {
            // 将当前遍历到的直接子节点 child 添加到 descendants 列表中。
            descendants.add(child)
            // 递归调用 child.getAllDescendants() 获取该子节点的所有后代，
            // 并将返回的后代列表全部添加到当前的 descendants 列表中。
            descendants.addAll(child.getAllDescendants())
        }
        // 返回包含所有后代节点的列表。
        return descendants
    }

    // Get path from root to this node
    // 注释：获取从根节点到当前节点的路径。
    // 定义一个公共方法 getPathFromRoot，用于获取并返回从根节点到当前节点的路径列表。
    fun getPathFromRoot(): List<ElementNode> {
        // 创建一个可变列表 path，用于存储路径上的节点。
        val path = mutableListOf<ElementNode>()
        // 初始化一个可空的临时变量 current，初始值为当前节点 (this)。
        var current: ElementNode? = this
        // 开始循环，向上遍历父节点，直到 current 变为 null（即到达根节点）。
        // 当 current 不为 null 时，循环继续。
        while (current != null) {
            // 将当前节点 current 添加到 path 列表的最前面（索引 0 处）。
            // 这样可以保证最终列表是从根到当前节点的顺序。
            path.add(0, current)
            // 将 current 更新为其父节点，准备下一次迭代。
            current = current.parent
        }
        // 返回构建好的路径列表。
        return path
    }

    // 重写 (override) Any 类的 toString 方法，提供 ElementNode 对象的自定义字符串表示。
    override fun toString(): String {
        // 返回一个格式化的字符串，包含 ElementNode 的关键属性信息。
        return "ElementNode(text='$text', className='$className', rect=$rect, id='$id')"
    }

    // 重写 (override) Any 类的 equals 方法，定义 ElementNode 对象之间的相等性比较规则。
    override fun equals(other: Any?): Boolean {
        // 首先检查是否是与自身进行比较，如果是，则直接返回 true。
        if (this === other) return true
        // 然后检查 other 是否为 ElementNode 类型，如果不是，则返回 false。
        if (other !is ElementNode) return false
        // 如果类型匹配，则比较两个 ElementNode 对象的 id 属性是否相等。
        // 因为 id 是唯一的，所以 id 相等意味着对象相等。
        return id == other.id
    }

    // 重写 (override) Any 类的 hashCode 方法，确保在 equals 返回 true 时，hashCode 也必须相等。
    override fun hashCode(): Int {
        // 基于 ElementNode 对象的 id 属性计算并返回其哈希码。
        // 这样可以保证具有相同 id 的对象具有相同的哈希码。
        return id.hashCode()
    }
}



