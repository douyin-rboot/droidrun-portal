// 定义包名，用于组织和管理类文件
package com.droidrun.portal

// 导入必要的 Android 类库
// InputMethodService 是输入法服务的核心基类
import android.inputmethodservice.InputMethodService
// Base64 用于处理 Base64 编码和解码
import android.util.Base64
// Log 用于输出调试信息到 Android Logcat
import android.util.Log
// KeyEvent 用于表示和发送键盘按键事件
import android.view.KeyEvent
// View 是 Android UI 的基础组件
import android.view.View
// ExtractedTextRequest 用于请求获取当前编辑框的文本内容
import android.view.inputmethod.ExtractedTextRequest

// 定义一个自定义输入法服务类，继承自 Android 的 InputMethodService
class DroidrunKeyboardIME : InputMethodService() {
    // 定义一个常量 TAG，用于在 Logcat 中标识此类的日志信息
    private val TAG = "DroidrunKeyboardIME"

    // 定义伴生对象（Companion Object），用于实现类似静态成员的功能
    companion object {
        // 声明一个私有的、可空的静态实例变量，用于存储 DroidrunKeyboardIME 的单例
        private var instance: DroidrunKeyboardIME? = null

        // 定义一个公共的静态方法 getInstance()，用于获取 DroidrunKeyboardIME 的单例实例
        // 如果实例尚未创建，则返回 null
        fun getInstance(): DroidrunKeyboardIME? = instance

        /**
         * 检查 DroidrunKeyboardIME 当前是否处于活跃且可用状态
         * 通过判断单例实例是否为 null 来实现
         */
        fun isAvailable(): Boolean = instance != null
    }

    // 重写 onCreate() 方法，当服务被创建时调用
    override fun onCreate() {
        // 调用父类 InputMethodService 的 onCreate() 方法，执行标准初始化
        super.onCreate()
        // 将当前创建的实例赋值给伴生对象中的静态变量 instance，实现单例模式
        instance = this
        // 使用 Log.d() 输出调试信息，表明 onCreate() 方法已被调用
        Log.d(TAG, "DroidrunKeyboardIME: onCreate() called")
    }

    /**
     * 提供一个公共方法 inputText(text: String): Boolean，用于直接向当前输入框插入指定文本
     * @param text 要插入的文本字符串
     * @return Boolean 操作是否成功
     */
    fun inputText(text: String): Boolean {
        // 使用 try-catch 块捕获可能发生的异常
        return try {
            // 获取当前的输入连接对象 (InputConnection)，它是与目标应用编辑框通信的接口
            val ic = currentInputConnection
            // 检查输入连接对象是否有效（不为 null）
            if (ic != null) {
                // 如果输入连接有效，则调用其 commitText() 方法将文本提交到输入框
                // 第二个参数 1 表示新文本相对于光标位置的偏移量（通常为1）
                ic.commitText(text, 1)
                // 使用 Log.d() 记录成功插入文本的日志
                Log.d(TAG, "Direct text input successful: $text")
                // 返回 true 表示操作成功
                true
            } else {
                // 如果输入连接无效（为 null），则记录警告日志
                Log.w(TAG, "No input connection available for direct input")
                // 返回 false 表示操作失败
                false
            }
        } catch (e: Exception) {
            // 如果在 try 块中发生任何异常，则在此捕获
            // 使用 Log.e() 记录错误日志，包括异常信息
            Log.e(TAG, "Error in direct text input", e)
            // 返回 false 表示操作失败
            false
        }
    }

    /**
     * 提供一个公共方法 inputB64Text(base64Text: String, append: Boolean = false): Boolean，
     * 用于直接向当前输入框插入经过 Base64 解码的文本
     * @param base64Text Base64 编码的文本字符串
     * @param append Boolean 标志，指示是追加文本 (true) 还是替换现有文本 (false，默认)
     * @return Boolean 操作是否成功
     */
    fun inputB64Text(base64Text: String, append: Boolean = false): Boolean {
        // 使用 try-catch 块捕获可能发生的异常
        return try {
            // 使用 Base64.decode() 方法将 Base64 字符串解码为字节数组
            // Base64.DEFAULT 指定了解码时使用的标志
            val decoded = Base64.decode(base64Text, Base64.DEFAULT)
            // 将解码后的字节数组使用 UTF-8 字符集转换为字符串
            val text = String(decoded, Charsets.UTF_8)
            // 调用另一个重载的 inputText() 方法，将解码后的文本插入输入框
            // 传递 append 参数以控制是追加还是替换
            inputText(text, append)
        } catch (e: Exception) {
            // 如果在解码或插入过程中发生任何异常，则在此捕获
            // 使用 Log.e() 记录错误日志，包括异常信息
            Log.e(TAG, "Error decoding base64 for direct input", e)
            // 返回 false 表示操作失败
            false
        }
    }

    /**
     * 提供一个公共方法 inputText(text: String, append: Boolean = false): Boolean，
     * 用于向当前输入框插入或追加指定文本
     * 这是 inputB64Text() 方法调用的重载版本
     * @param text 要插入或追加的文本字符串
     * @param append Boolean 标志，指示是追加文本 (true) 还是替换现有文本 (false，默认)
     * @return Boolean 操作是否成功
     */
    fun inputText(text: String, append: Boolean = false): Boolean {
        // 使用 try-catch 块捕获可能发生的异常
        return try {
            // 获取当前的输入连接对象 (InputConnection)
            val ic = currentInputConnection
            // 检查输入连接对象是否有效（不为 null）
            if (ic != null) {
                // 如果不是追加模式 (append 为 false)
                if (!append) {
                    // 则先调用 clearText() 方法清除当前输入框中的所有文本
                    clearText()
                }
                // 调用输入连接的 commitText() 方法将文本提交到输入框
                // 第二个参数 0 表示光标应放置在新插入文本的末尾
                ic.commitText(text, 0)
                // 使用 Log.d() 记录成功插入或追加文本的日志，包括 append 状态
                Log.d(TAG, "Text input successful: $text (append=$append)")
                // 返回 true 表示操作成功
                true
            } else {
                // 如果输入连接无效（为 null），则记录警告日志
                Log.w(TAG, "No input connection available for text input")
                // 返回 false 表示操作失败
                false
            }
        } catch (e: Exception) {
            // 如果在插入或清除过程中发生任何异常，则在此捕获
            // 使用 Log.e() 记录错误日志，包括异常信息
            Log.e(TAG, "Error in text input", e)
            // 返回 false 表示操作失败
            false
        }
    }


    /**
     * 提供一个公共方法 clearText(): Boolean，用于直接清除当前输入框中的所有文本
     * @return Boolean 操作是否成功
     */
    fun clearText(): Boolean {
        // 使用 try-catch 块捕获可能发生的异常
        return try {
            // 获取当前的输入连接对象 (InputConnection)
            val ic = currentInputConnection
            // 检查输入连接对象是否有效（不为 null）
            if (ic != null) {
                // 创建一个 ExtractedTextRequest 对象，用于请求提取文本
                val extractedTextRequest = ExtractedTextRequest()
                // 调用输入连接的 getExtractedText() 方法，获取当前编辑框的文本信息
                // 第二个参数 0 是 flags，通常传 0
                val extractedText = ic.getExtractedText(extractedTextRequest, 0)
                // 检查提取到的文本信息对象是否有效（不为 null）
                if (extractedText != null) {
                    // 获取提取到的完整文本内容
                    val fullText = extractedText.text
                    // 获取光标前的文本内容
                    val beforePos = ic.getTextBeforeCursor(fullText.length, 0)
                    // 获取光标后的文本内容
                    val afterPos = ic.getTextAfterCursor(fullText.length, 0)
                    // 调用输入连接的 deleteSurroundingText() 方法删除光标周围的文本
                    // 第一个参数是要删除的光标前字符数，第二个参数是要删除的光标后字符数
                    ic.deleteSurroundingText(beforePos?.length ?: 0, afterPos?.length ?: 0)
                    // 使用 Log.d() 记录成功清除文本的日志
                    Log.d(TAG, "Direct text clear successful")
                    // 返回 true 表示操作成功
                    true
                } else {
                    // 如果无法提取到文本信息，则记录警告日志
                    Log.w(TAG, "No extracted text available for clearing")
                    // 返回 false 表示操作失败
                    false
                }
            } else {
                // 如果输入连接无效（为 null），则记录警告日志
                Log.w(TAG, "No input connection available for direct clear")
                // 返回 false 表示操作失败
                false
            }
        } catch (e: Exception) {
            // 如果在提取或删除文本过程中发生任何异常，则在此捕获
            // 使用 Log.e() 记录错误日志，包括异常信息
            Log.e(TAG, "Error in direct text clear", e)
            // 返回 false 表示操作失败
            false
        }
    }

    /**
     * 提供一个公共方法 sendKeyEventDirect(keyCode: Int): Boolean，用于直接发送指定的按键事件
     * @param keyCode 要发送的按键的键码 (KeyEvent.KEYCODE_*)
     * @return Boolean 操作是否成功
     */
    fun sendKeyEventDirect(keyCode: Int): Boolean {
        // 使用 try-catch 块捕获可能发生的异常
        return try {
            // 获取当前的输入连接对象 (InputConnection)
            val ic = currentInputConnection
            // 检查输入连接对象是否有效（不为 null）
            if (ic != null) {
                // 创建一个 ACTION_DOWN（按键按下）事件对象
                val keyEventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                // 创建一个 ACTION_UP（按键抬起）事件对象
                val keyEventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                // 调用输入连接的 sendKeyEvent() 方法发送按键按下事件
                ic.sendKeyEvent(keyEventDown)
                // 调用输入连接的 sendKeyEvent() 方法发送按键抬起事件
                ic.sendKeyEvent(keyEventUp)
                // 使用 Log.d() 记录成功发送按键事件的日志，包括键码
                Log.d(TAG, "Direct key event sent: $keyCode")
                // 返回 true 表示操作成功
                true
            } else {
                // 如果输入连接无效（为 null），则记录警告日志
                Log.w(TAG, "No input connection available for direct key event")
                // 返回 false 表示操作失败
                false
            }
        } catch (e: Exception) {
            // 如果在创建或发送按键事件过程中发生任何异常，则在此捕获
            // 使用 Log.e() 记录错误日志，包括异常信息
            Log.e(TAG, "Error sending direct key event", e)
            // 返回 false 表示操作失败
            false
        }
    }

    /**
     * 提供一个公共方法 hasInputConnection(): Boolean，用于检查当前是否存在有效的输入连接
     * @return Boolean 当前是否存在有效的输入连接
     */
    fun hasInputConnection(): Boolean {
        // 直接检查 currentInputConnection 属性是否不为 null
        return currentInputConnection != null
    }


    /**
     * 重写 onCreateInputView() 方法，当需要创建输入法的视图（即软键盘界面）时调用。
     * 这里通过加载一个 XML 布局文件来创建键盘视图。
     * @return 返回创建的 View 对象，作为软键盘的界面。
     */
    override fun onCreateInputView(): View {
        // 使用 Log.d() 输出调试信息，表明此方法已被调用
        Log.d(TAG, "onCreateInputView called")

        // 使用 layoutInflater.inflate() 方法从 res/layout/keyboard_view.xml 文件中加载布局
        // R.layout.keyboard_view 是 Android 资源编译器根据 XML 文件自动生成的 ID
        // null 表示没有父视图
        // 返回加载并实例化后的 View 对象
        return layoutInflater.inflate(R.layout.keyboard_view, null)
    }


    // 重写 onStartInput() 方法，当开始输入会话（例如用户点击输入框）时调用
    // attribute 包含目标编辑框的信息，restarting 表示是否是重新开始（如配置更改）
    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        // 调用父类 InputMethodService 的 onStartInput() 方法，执行标准处理
        super.onStartInput(attribute, restarting)
        // 使用 Log.d() 输出调试信息，包括 restarting 参数的值
        Log.d(TAG, "onStartInput called - restarting: $restarting")
    }

    // 重写 onStartInputView() 方法，当输入视图（软键盘）即将显示给用户时调用
    // attribute 包含目标编辑框的信息，restarting 表示是否是重新开始
    override fun onStartInputView(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        // 调用父类 InputMethodService 的 onStartInputView() 方法，执行标准处理
        super.onStartInputView(attribute, restarting)
        // 使用 Log.d() 输出调试信息，表明键盘现在应该可见了
        Log.d(TAG, "onStartInputView called - keyboard should be visible now")
    }

    // 重写 onDestroy() 方法，当服务被销毁时调用
    override fun onDestroy() {
        // 使用 Log.d() 输出调试信息，表明 onDestroy() 方法已被调用
        Log.d(TAG, "DroidrunKeyboardIME: onDestroy() called")
        // 将伴生对象中的静态实例变量 instance 设置为 null，释放单例引用
        instance = null
        // 调用父类 InputMethodService 的 onDestroy() 方法，执行标准清理
        super.onDestroy()
    }
}



