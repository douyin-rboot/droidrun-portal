// 指定此 Kotlin 文件所属的包，用于组织和管理代码，表明这个类属于 com.droidrun.portal.model 包。
package com.droidrun.portal.model

// 导入 Android SDK 中的 AccessibilityNodeInfo 类，该类用于表示屏幕上的可访问性节点信息，
// 通常包含 UI 元素的详细属性，如文本、类名、状态等。
import android.view.accessibility.AccessibilityNodeInfo

// 使用 data 关键字定义一个数据类 PhoneState。
// 数据类在 Kotlin 中会自动生成 equals(), hashCode(), toString() 以及 componentN() 函数，
// 非常适合用来存储数据或状态。
data class PhoneState (
    // val 表示这是一个只读属性。
    // focusedElement: AccessibilityNodeInfo? 定义了一个名为 focusedElement 的属性，
    // 类型是 AccessibilityNodeInfo? (可空的 AccessibilityNodeInfo)。
    // 这个属性用于存储当前获得焦点的 UI 元素的信息。
    // 如果当前没有元素获得焦点，则该值为 null。
    val focusedElement: AccessibilityNodeInfo?,

    // val 表示这是一个只读属性。
    // keyboardVisible: Boolean 定义了一个名为 keyboardVisible 的属性，类型是 Boolean (布尔值)。
    // 这个属性用于表示屏幕上的软键盘当前是否处于可见状态。
    // true 表示键盘可见，false 表示键盘不可见。
    val keyboardVisible: Boolean,

    // val 表示这是一个只读属性。
    // packageName: String? 定义了一个名为 packageName 的属性，
    // 类型是 String? (可空的 String)。
    // 这个属性用于存储当前处于前台的应用程序的包名 (Package Name)。
    // 例如："com.example.myapp"。
    // 如果无法确定或没有前台应用，则该值可能为 null。
    val packageName: String?,

    // val 表示这是一个只读属性。
    // appName: String? 定义了一个名为 appName 的属性，
    // 类型是 String? (可空的 String)。
    // 这个属性用于存储当前处于前台的应用程序的用户可见名称 (App Name)。
    // 例如："我的应用"。
    // 如果无法确定应用名称或没有前台应用，则该值可能为 null。
    val appName: String?
)



