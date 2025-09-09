package com.droidrun.portal // 定义此 Kotlin 文件所属的包名，用于组织和管理代码。

import android.content.Context // 导入用于访问应用特定资源和类的上下文类。
import android.content.Intent // 导入用于在不同组件（如 Activity）之间进行导航和传递数据的类。
import android.os.Bundle // 导入用于保存和恢复 Activity 状态的 Bundle 类。
import android.util.Log // 导入用于记录调试和错误信息的日志类。
import android.widget.TextView // 导入用于显示文本的 UI 组件类。
import androidx.appcompat.app.AppCompatActivity // 导入 AndroidX 库中的基础 Activity 类，提供兼容性支持。
import android.text.Editable // 导入表示可编辑文本内容的 Editable 类。
import android.text.TextWatcher // 导入用于监听文本变化的接口。
import android.view.inputmethod.EditorInfo // 导入包含输入法编辑器操作信息的常量类。
import android.widget.SeekBar // 导入用于通过滑动选择数值的 UI 组件类。
import android.widget.Toast // 导入用于显示简短提示消息的 Toast 类。
import com.google.android.material.button.MaterialButton // 导入 Material Design 风格的按钮组件。
import com.google.android.material.switchmaterial.SwitchMaterial // 导入 Material Design 风格的开关组件。
import com.google.android.material.textfield.TextInputEditText // 导入 Material Design 风格的可编辑文本输入框。
import com.google.android.material.textfield.TextInputLayout // 导入用于包装 TextInputEditText 并提供额外功能（如错误提示）的布局。
import android.provider.Settings // 导入用于访问系统设置的类。
import android.widget.ImageView // 导入用于显示图片的 UI 组件类。
import android.view.View // 导入所有 UI 组件的基类 View。
import android.os.Handler // 导入用于在特定线程（如主线程）上执行代码的 Handler 类。
import android.os.Looper // 导入用于管理线程消息队列的 Looper 类。
import android.net.Uri // 导入用于表示统一资源标识符 (URI) 的类。
import android.database.Cursor // 导入用于遍历数据库查询结果集的 Cursor 类。
import org.json.JSONObject // 导入用于处理 JSON 数据的 JSONObject 类。

/**
 * MainActivity 是应用程序的主入口点，负责用户界面交互和与辅助功能服务通信。
 * 它管理可视化叠加层的开关、元素偏移量调整、数据获取以及 Socket 服务器设置。
 */
class MainActivity : AppCompatActivity() { // 定义 MainActivity 类，继承自 AppCompatActivity。

    // 声明用于显示状态信息的 TextView UI 元素。
    private lateinit var statusText: TextView
    // 声明用于显示响应数据的 TextView UI 元素。
    private lateinit var responseText: TextView
    // 声明用于显示应用版本号的 TextView UI 元素。
    private lateinit var versionText: TextView
    // 声明控制可视化叠加层开关的 SwitchMaterial UI 元素。
    private lateinit var toggleOverlay: SwitchMaterial
    // 声明用于触发数据获取操作的 MaterialButton UI 元素。
    private lateinit var fetchButton: MaterialButton
    // 声明用于调整元素偏移量的 SeekBar UI 元素。
    private lateinit var offsetSlider: SeekBar
    // 声明用于手动输入元素偏移量值的 TextInputEditText UI 元素。
    private lateinit var offsetInput: TextInputEditText
    // 声明包装 offsetInput 的 TextInputLayout UI 元素，用于显示错误信息。
    private lateinit var offsetInputLayout: TextInputLayout
    // 声明用于显示辅助功能服务状态指示器的 View UI 元素。
    private lateinit var accessibilityIndicator: View
    // 声明用于显示辅助功能服务状态文本 ("ENABLED"/"DISABLED") 的 TextView UI 元素。
    private lateinit var accessibilityStatusText: TextView
    // 声明包含辅助功能服务状态信息的整体容器 View UI 元素。
    private lateinit var accessibilityStatusContainer: View
    // 声明包含辅助功能服务状态信息的卡片式 MaterialCardView UI 元素。
    private lateinit var accessibilityStatusCard: com.google.android.material.card.MaterialCardView

    // 声明用于输入 Socket 服务器端口号的 TextInputEditText UI 元素。
    private lateinit var socketPortInput: TextInputEditText
    // 声明包装 socketPortInput 的 TextInputLayout UI 元素，用于显示错误信息。
    private lateinit var socketPortInputLayout: TextInputLayout
    // 声明用于显示 Socket 服务器当前状态的 TextView UI 元素。
    private lateinit var socketServerStatus: TextView
    // 声明用于显示 ADB 端口转发命令的 TextView UI 元素。
    private lateinit var adbForwardCommand: TextView

    // 声明一个布尔标志，用于防止在程序更新 UI 时触发 TextWatcher 监听器导致的无限循环。
    private var isProgrammaticUpdate = false
    // 声明一个 Handler 实例，用于将任务发布到主线程的消息队列中执行。
    private val mainHandler = Handler(Looper.getMainLooper())

    // 定义一个伴生对象，用于存放常量，这些常量与位置偏移滑块相关。
    companion object {
        // 定义默认的元素偏移量值。
        private const val DEFAULT_OFFSET = 0
        // 定义元素偏移量的最小允许值。
        private const val MIN_OFFSET = -256
        // 定义元素偏移量的最大允许值。
        private const val MAX_OFFSET = 256
        // 计算滑块的总范围值 (MAX_OFFSET - MIN_OFFSET)。
        private const val SLIDER_RANGE = MAX_OFFSET - MIN_OFFSET
    }

    /**
     * onCreate 是 Activity 生命周期的第一个回调方法，在 Activity 首次创建时调用。
     * 在这里进行 UI 初始化、设置监听器等操作。
     */
    override fun onCreate(savedInstanceState: Bundle?) { // 重写父类的 onCreate 方法，接收保存的状态信息。
        super.onCreate(savedInstanceState) // 调用父类的 onCreate 方法，执行标准的初始化。
        setContentView(R.layout.activity_main) // 设置当前 Activity 的布局文件为 activity_main.xml。

        // 通过 findViewById 方法初始化各个 UI 元素，将布局文件中的视图与代码中的变量关联起来。
        statusText = findViewById(R.id.status_text)
        responseText = findViewById(R.id.response_text)
        versionText = findViewById(R.id.version_text)
        fetchButton = findViewById(R.id.fetch_button)
        toggleOverlay = findViewById(R.id.toggle_overlay)
        offsetSlider = findViewById(R.id.offset_slider)
        offsetInput = findViewById(R.id.offset_input)
        offsetInputLayout = findViewById(R.id.offset_input_layout)
        accessibilityIndicator = findViewById(R.id.accessibility_indicator)
        accessibilityStatusText = findViewById(R.id.accessibility_status_text)
        accessibilityStatusContainer = findViewById(R.id.accessibility_status_container)
        accessibilityStatusCard = findViewById(R.id.accessibility_status_card)

        // 初始化 Socket 服务器相关的 UI 元素。
        socketPortInput = findViewById(R.id.socket_port_input)
        socketPortInputLayout = findViewById(R.id.socket_port_input_layout)
        socketServerStatus = findViewById(R.id.socket_server_status)
        adbForwardCommand = findViewById(R.id.adb_forward_command)

        // 调用 setAppVersion 方法，显示当前应用的版本号。
        setAppVersion()

        // 调用 setupOffsetSlider 方法，配置元素偏移量滑块的行为。
        setupOffsetSlider()
        // 调用 setupOffsetInput 方法，配置元素偏移量输入框的行为。
        setupOffsetInput()

        // 调用 setupSocketServerControls 方法，配置 Socket 服务器控制项的行为。
        setupSocketServerControls()

        // 为 fetchButton 设置点击监听器，当用户点击按钮时，执行 fetchElementData 方法获取数据。
        fetchButton.setOnClickListener {
            fetchElementData()
        }

        // 为 toggleOverlay 开关设置状态改变监听器，当用户切换开关时，执行 toggleOverlayVisibility 方法。
        toggleOverlay.setOnCheckedChangeListener { _, isChecked -> // 参数 _: CompoundButton (未使用), isChecked: Boolean (当前开关状态)
            toggleOverlayVisibility(isChecked) // 调用方法，根据 isChecked 的值显示或隐藏叠加层。
        }

        // 为 accessibilityStatusContainer 设置点击监听器，当用户点击状态卡片时，打开系统辅助功能设置。
        accessibilityStatusContainer.setOnClickListener {
            openAccessibilitySettings() // 调用方法，启动辅助功能设置页面。
        }

        // 调用 updateAccessibilityStatusIndicator 方法，检查并更新辅助功能服务状态指示器的 UI。
        updateAccessibilityStatusIndicator()
        // 调用 syncUIWithAccessibilityService 方法，将 UI 状态与辅助功能服务的当前状态同步。
        syncUIWithAccessibilityService()
        // 调用 updateSocketServerStatus 方法，检查并更新 Socket 服务器状态的 UI。
        updateSocketServerStatus()
    }

    /**
     * onResume 是 Activity 生命周期的回调方法，在 Activity 开始与用户交互前调用。
     * 在这里更新 UI 状态，确保它们反映最新的应用状态。
     */
    override fun onResume() { // 重写父类的 onResume 方法。
        super.onResume() // 调用父类的 onResume 方法，执行标准的恢复操作。
        // 当 Activity 恢复时，再次更新辅助功能服务状态指示器。
        updateAccessibilityStatusIndicator()
        // 当 Activity 恢复时，再次同步 UI 与辅助功能服务的状态。
        syncUIWithAccessibilityService()
        // 当 Activity 恢复时，再次更新 Socket 服务器状态。
        updateSocketServerStatus()
    }

    /**
     * syncUIWithAccessibilityService 方法用于将 MainActivity 的 UI 状态与 DroidrunAccessibilityService 的当前状态进行同步。
     * 这包括叠加层的可见性、偏移量设置等。
     */
    private fun syncUIWithAccessibilityService() { // 定义私有方法 syncUIWithAccessibilityService。
        // 获取 DroidrunAccessibilityService 的单例实例。
        val accessibilityService = DroidrunAccessibilityService.getInstance()
        // 检查辅助功能服务实例是否可用（不为 null）。
        if (accessibilityService != null) { // 如果服务实例存在
            // 将 toggleOverlay 开关的状态设置为服务中记录的叠加层可见性状态。
            toggleOverlay.isChecked = accessibilityService.isOverlayVisible()

            // 获取服务中记录的当前叠加层偏移量。
            val currentOffset = accessibilityService.getOverlayOffset()
            // 更新滑块 UI 以反映从服务获取的当前偏移量。
            updateOffsetSlider(currentOffset)
            // 更新输入框 UI 以反映从服务获取的当前偏移量。
            updateOffsetInputField(currentOffset)

            // 更新状态文本，显示已成功连接到辅助功能服务。
            statusText.text = "Connected to accessibility service"
        } else { // 如果服务实例不存在（为 null）
            // 更新状态文本，提示辅助功能服务不可用。
            statusText.text = "Accessibility service not available"
        }
    }

    /**
     * setupOffsetSlider 方法用于配置元素偏移量滑块的初始状态和行为监听器。
     */
    private fun setupOffsetSlider() { // 定义私有方法 setupOffsetSlider。
        // 设置滑块的最大值为预定义的 SLIDER_RANGE 常量。
        offsetSlider.max = SLIDER_RANGE

        // 计算默认偏移量 (DEFAULT_OFFSET) 在滑块范围内的初始位置。
        // 因为滑块的 progress 从 0 开始，而偏移量可能为负数，所以需要转换。
        val initialSliderPosition = DEFAULT_OFFSET - MIN_OFFSET
        // 将滑块的进度设置为计算出的初始位置。
        offsetSlider.progress = initialSliderPosition

        // 为滑块设置状态改变监听器，监听用户滑动操作。
        offsetSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { // 创建匿名内部类实现监听器接口
            /**
             * onProgressChanged 在滑块进度改变时调用。
             * @param seekBar 发生改变的 SeekBar 实例。
             * @param progress 当前的进度值。
             * @param fromUser 此次改变是否由用户操作引起。
             */
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { // 重写 onProgressChanged 方法
                // 将滑块的进度值 (0-SLIDER_RANGE) 转换回实际的偏移量值 (MIN_OFFSET to MAX_OFFSET)。
                val offsetValue = progress + MIN_OFFSET

                // 仅当改变是由用户滑动引起的时，才更新输入框和应用偏移量。
                if (fromUser) { // 如果是用户操作
                    // 更新输入框的文本，使其与滑块当前值同步。
                    updateOffsetInputField(offsetValue)
                    // 调用方法，将新的偏移量值应用到辅助功能服务。
                    updateOverlayOffset(offsetValue)
                }
            }

            /**
             * onStartTrackingTouch 在用户开始触摸滑块时调用。
             * @param seekBar 被触摸的 SeekBar 实例。
             */
            override fun onStartTrackingTouch(seekBar: SeekBar?) { // 重写 onStartTrackingTouch 方法
                // 此方法体为空，表示在此事件发生时不需要执行任何特殊操作。
            }

            /**
             * onStopTrackingTouch 在用户停止触摸滑块时调用。
             * @param seekBar 被触摸的 SeekBar 实例。
             */
            override fun onStopTrackingTouch(seekBar: SeekBar?) { // 重写 onStopTrackingTouch 方法
                // 获取滑块最终的进度值，并转换为实际偏移量。
                // 使用安全调用 (?.) 和 Elvis 运算符 (?:) 处理可能的 null 情况，如果为 null 则使用默认值。
                val offsetValue = seekBar?.progress?.plus(MIN_OFFSET) ?: DEFAULT_OFFSET
                // 调用方法，将最终确定的偏移量值应用到辅助功能服务。
                updateOverlayOffset(offsetValue)
            }
        })
    }

    /**
     * setupOffsetInput 方法用于配置元素偏移量输入框的初始状态和行为监听器。
     */
    private fun setupOffsetInput() { // 定义私有方法 setupOffsetInput。
        // 在设置初始文本之前，设置标志位 isProgrammaticUpdate 为 true，
        // 以防止在设置初始文本时触发 TextWatcher 的监听逻辑。
        isProgrammaticUpdate = true
        // 将输入框的文本设置为默认偏移量值的字符串形式。
        offsetInput.setText(DEFAULT_OFFSET.toString())
        // 文本设置完成后，将标志位重置为 false。
        isProgrammaticUpdate = false

        // 为输入框设置编辑器动作监听器，监听用户在软键盘上按 "完成" (IME_ACTION_DONE) 键的动作。
        offsetInput.setOnEditorActionListener { _, actionId, _ -> // 参数 _: TextView (未使用), actionId: Int (动作ID), _: KeyEvent? (未使用)
            // 检查按下的动作键是否是 "完成"。
            if (actionId == EditorInfo.IME_ACTION_DONE) { // 如果是 "完成" 键
                // 调用 applyInputOffset 方法，处理并应用输入框中的值。
                applyInputOffset()
                true // 返回 true 表示已处理该动作
            } else { // 如果不是 "完成" 键
                false // 返回 false 表示未处理该动作
            }
        }

        // 为输入框添加文本变化监听器，实时监听和验证用户输入。
        offsetInput.addTextChangedListener(object : TextWatcher { // 创建匿名内部类实现 TextWatcher 接口
            /**
             * beforeTextChanged 在文本即将改变前调用。
             * @param s 改变前的文本。
             * @param start 文本改变的起始位置。
             * @param count 被改变的字符数。
             * @param after 改变后新增的字符数。
             */
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} // 空实现

            /**
             * onTextChanged 在文本正在改变时调用。
             * @param s 改变后的文本。
             * @param start 文本改变的起始位置。
             * @param before 被删除的字符数。
             * @param count 新增的字符数。
             */
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} // 空实现

            /**
             * afterTextChanged 在文本改变后调用。
             * @param s 改变后的文本 (Editable 对象)。
             */
            override fun afterTextChanged(s: Editable?) { // 重写 afterTextChanged 方法
                // 检查是否是程序代码更新了文本（例如上面的 setText），如果是，则跳过后续处理。
                if (isProgrammaticUpdate) return // 如果是程序更新，直接返回

                try { // 使用 try-catch 块捕获可能的 NumberFormatException
                    // 尝试将输入框中的文本转换为整数，如果无法转换则返回 null。
                    val value = s.toString().toIntOrNull()
                    // 检查转换是否成功且值不为 null。
                    if (value != null) { // 如果转换成功
                        // 检查转换后的整数值是否在预定义的有效范围内。
                        if (value < MIN_OFFSET || value > MAX_OFFSET) { // 如果超出范围
                            // 设置 TextInputLayout 的错误信息，提示用户输入无效。
                            offsetInputLayout.error = "Value must be between $MIN_OFFSET and $MAX_OFFSET"
                        } else { // 如果在有效范围内
                            // 清除 TextInputLayout 的错误信息。
                            offsetInputLayout.error = null
                            // 如果输入的字符长度大于1，或者长度为1且不是负号 "-"，则自动应用该值。
                            // 这提供了便捷的自动应用功能。
                            if (s.toString().length > 1 || (s.toString().length == 1 && !s.toString().startsWith("-"))) {
                                applyInputOffset() // 调用方法应用输入的值
                            }
                        }
                    } else if (s.toString().isNotEmpty() && s.toString() != "-") { // 如果转换失败且输入不为空且不是单独的负号
                        // 设置 TextInputLayout 的错误信息，提示用户输入无效。
                        offsetInputLayout.error = "Invalid number"
                    } else { // 如果输入为空或仅为负号
                        // 清除 TextInputLayout 的错误信息。
                        offsetInputLayout.error = null
                    }
                } catch (e: Exception) { // 捕获任何其他可能的异常
                    // 设置 TextInputLayout 的错误信息，显示异常消息。
                    offsetInputLayout.error = "Invalid number"
                }
            }
        })
    }

    /**
     * applyInputOffset 方法用于处理并应用用户在输入框中输入的偏移量值。
     */
    private fun applyInputOffset() { // 定义私有方法 applyInputOffset。
        try { // 使用 try-catch 块捕获可能的异常
            // 获取输入框中的文本内容。
            val inputText = offsetInput.text.toString()
            // 尝试将文本内容转换为整数。
            val offsetValue = inputText.toIntOrNull()

            // 检查转换是否成功。
            if (offsetValue != null) { // 如果转换成功
                // 使用 coerceIn 函数确保偏移量值在 MIN_OFFSET 和 MAX_OFFSET 之间。
                // 如果超出范围，coerceIn 会将其调整到边界值。
                val boundedValue = offsetValue.coerceIn(MIN_OFFSET, MAX_OFFSET)

                // 检查调整后的值是否与原始输入值不同。
                if (boundedValue != offsetValue) { // 如果值被调整了
                    // 更新标志位，防止在更新 UI 时触发 TextWatcher。
                    isProgrammaticUpdate = true
                    // 将调整后的有效值设置回输入框。
                    offsetInput.setText(boundedValue.toString())
                    // 重置标志位。
                    isProgrammaticUpdate = false
                    // 显示一个短暂的 Toast 消息，告知用户值已被调整。
                    Toast.makeText(this, "Value adjusted to valid range", Toast.LENGTH_SHORT).show()
                }

                // 计算调整后值在滑块上的对应位置。
                val sliderPosition = boundedValue - MIN_OFFSET
                // 将滑块的位置更新为计算出的位置，使其与输入框同步。
                offsetSlider.progress = sliderPosition
                // 调用方法，将最终确定的偏移量值应用到辅助功能服务。
                updateOverlayOffset(boundedValue)
            } else { // 如果转换失败（输入为空或无效）
                // 显示一个 Toast 消息，提示用户输入有效数字。
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 记录错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error applying input offset: ${e.message}")
            // 显示一个包含异常信息的 Toast 消息。
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * updateOffsetSlider 方法用于更新滑块的位置，使其与给定的偏移量值同步。
     * @param currentOffset 要同步到滑块的偏移量值。
     */
    private fun updateOffsetSlider(currentOffset: Int) { // 定义私有方法 updateOffsetSlider，接收一个整数参数 currentOffset。
        // 使用 coerceIn 确保传入的偏移量值在 MIN_OFFSET 和 MAX_OFFSET 之间。
        val boundedOffset = currentOffset.coerceIn(MIN_OFFSET, MAX_OFFSET)

        // 计算该偏移量值在滑块上的对应位置。
        val sliderPosition = boundedOffset - MIN_OFFSET
        // 将滑块的进度设置为计算出的位置。
        offsetSlider.progress = sliderPosition
    }

    /**
     * updateOffsetInputField 方法用于更新输入框的文本，使其与给定的偏移量值同步。
     * @param currentOffset 要同步到输入框的偏移量值。
     */
    private fun updateOffsetInputField(currentOffset: Int) { // 定义私有方法 updateOffsetInputField，接收一个整数参数 currentOffset。
        // 设置标志位，防止在更新 UI 时触发 TextWatcher。
        isProgrammaticUpdate = true

        // 将偏移量值转换为字符串并设置到输入框中。
        offsetInput.setText(currentOffset.toString())

        // 重置标志位。
        isProgrammaticUpdate = false
    }

    /**
     * updateOverlayOffset 方法用于将指定的偏移量值发送给 DroidrunAccessibilityService 进行应用。
     * @param offsetValue 要应用的偏移量值。
     */
    private fun updateOverlayOffset(offsetValue: Int) { // 定义私有方法 updateOverlayOffset，接收一个整数参数 offsetValue。
        try { // 使用 try-catch 块捕获可能的异常
            // 获取 DroidrunAccessibilityService 的单例实例。
            val accessibilityService = DroidrunAccessibilityService.getInstance()
            // 检查辅助功能服务实例是否可用。
            if (accessibilityService != null) { // 如果服务实例存在
                // 调用服务的 setOverlayOffset 方法，并接收操作是否成功的布尔返回值。
                val success = accessibilityService.setOverlayOffset(offsetValue)
                // 检查操作是否成功。
                if (success) { // 如果成功
                    // 更新状态文本，显示偏移量已成功更新。
                    statusText.text = "Element offset updated to: $offsetValue"
                    // 记录一条调试日志，包含更新的偏移量值。
                    Log.d("DROIDRUN_MAIN", "Offset updated successfully: $offsetValue")
                } else { // 如果失败
                    // 更新状态文本，提示更新失败。
                    statusText.text = "Failed to update offset"
                    // 记录一条错误日志，包含尝试更新的偏移量值。
                    Log.e("DROIDRUN_MAIN", "Failed to update offset: $offsetValue")
                }
            } else { // 如果服务实例不存在
                // 更新状态文本，提示辅助功能服务不可用。
                statusText.text = "Accessibility service not available"
                // 记录一条错误日志，提示服务不可用。
                Log.e("DROIDRUN_MAIN", "Accessibility service not available for offset update")
            }
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 更新状态文本，显示错误信息。
            statusText.text = "Error updating offset: ${e.message}"
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error updating offset: ${e.message}")
        }
    }

    /**
     * fetchElementData 方法用于从 ContentProvider 获取组合状态数据（无障碍树 + 手机状态）。
     */
    private fun fetchElementData() { // 定义私有方法 fetchElementData。
        try { // 使用 try-catch 块捕获可能的异常
            // 更新状态文本，提示正在获取数据。
            statusText.text = "Fetching combined state data..."

            // 构造 ContentProvider 的 URI。
            val uri = Uri.parse("content://com.droidrun.portal/state")

            // 使用 contentResolver.query 方法查询 ContentProvider。
            // 参数依次为：URI, projection (null 表示查询所有列), selection, selectionArgs, sortOrder。
            val cursor = contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            // 使用 cursor?.use {} 语法确保 Cursor 在使用完毕后被正确关闭。
            cursor?.use {
                // 检查 Cursor 是否包含至少一行数据。
                if (it.moveToFirst()) { // 如果有数据
                    // 获取第一列（索引为0）的数据，假设它是一个包含 JSON 的字符串。
                    val result = it.getString(0)
                    // 将字符串解析为 JSONObject。
                    val jsonResponse = JSONObject(result)

                    // 检查 JSON 响应中的状态字段是否为 "success"。
                    if (jsonResponse.getString("status") == "success") { // 如果成功
                        // 从 JSON 响应中获取数据字段的内容。
                        val data = jsonResponse.getString("data")
                        // 将获取到的数据设置到 responseText TextView 中显示。
                        responseText.text = data
                        // 更新状态文本，显示接收到的数据长度。
                        statusText.text = "Combined state data received: ${data.length} characters"
                        // 显示一个成功的 Toast 消息。
                        Toast.makeText(this, "Combined state received successfully!", Toast.LENGTH_SHORT).show()

                        // 记录一条调试日志，显示接收到的数据的前100个字符（或全部字符，如果少于100）。
                        Log.d("DROIDRUN_MAIN", "Combined state data received: ${data.substring(0, Math.min(100, data.length))}...")
                    } else { // 如果状态不是 "success"
                        // 从 JSON 响应中获取错误信息。
                        val error = jsonResponse.getString("error")
                        // 更新状态文本，显示错误信息。
                        statusText.text = "Error: $error"
                        // 将错误信息设置到 responseText TextView 中显示。
                        responseText.text = error
                    }
                }
            }

        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 更新状态文本，显示错误信息。
            statusText.text = "Error fetching data: ${e.message}"
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error fetching combined state data: ${e.message}")
        }
    }

    /**
     * toggleOverlayVisibility 方法用于控制可视化叠加层的显示或隐藏。
     * @param visible 一个布尔值，true 表示显示，false 表示隐藏。
     */
    private fun toggleOverlayVisibility(visible: Boolean) { // 定义私有方法 toggleOverlayVisibility，接收一个布尔参数 visible。
        try { // 使用 try-catch 块捕获可能的异常
            // 获取 DroidrunAccessibilityService 的单例实例。
            val accessibilityService = DroidrunAccessibilityService.getInstance()
            // 检查辅助功能服务实例是否可用。
            if (accessibilityService != null) { // 如果服务实例存在
                // 调用服务的 setOverlayVisible 方法，并接收操作是否成功的布尔返回值。
                val success = accessibilityService.setOverlayVisible(visible)
                // 检查操作是否成功。
                if (success) { // 如果成功
                    // 更新状态文本，根据 visible 参数显示相应的消息。
                    statusText.text = "Visualization overlays ${if (visible) "enabled" else "disabled"}"
                    // 记录一条调试日志，包含切换后的可见性状态。
                    Log.d("DROIDRUN_MAIN", "Overlay visibility toggled to: $visible")
                } else { // 如果失败
                    // 更新状态文本，提示切换失败。
                    statusText.text = "Failed to toggle overlay"
                    // 记录一条错误日志。
                    Log.e("DROIDRUN_MAIN", "Failed to toggle overlay visibility")
                }
            } else { // 如果服务实例不存在
                // 更新状态文本，提示辅助功能服务不可用。
                statusText.text = "Accessibility service not available"
                // 记录一条错误日志，提示服务不可用。
                Log.e("DROIDRUN_MAIN", "Accessibility service not available for overlay toggle")
            }
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 更新状态文本，显示错误信息。
            statusText.text = "Error changing visibility: ${e.message}"
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error toggling overlay: ${e.message}")
        }
    }

    /**
     * fetchPhoneStateData 方法用于从 ContentProvider 获取手机状态数据。
     * 注意：此方法在当前代码中未被调用，可能是预留功能。
     */
    private fun fetchPhoneStateData() { // 定义私有方法 fetchPhoneStateData。
        try { // 使用 try-catch 块捕获可能的异常
            // 更新状态文本，提示正在获取手机状态。
            statusText.text = "Fetching phone state..."

            // 构造 ContentProvider 的 URI。
            val uri = Uri.parse("content://com.droidrun.portal/")
            // 创建一个 JSONObject，用于封装查询命令。
            val command = JSONObject().apply {
                put("action", "phone_state") // 添加 "action" 字段，值为 "phone_state"。
            }

            // 使用 contentResolver.query 方法查询 ContentProvider。
            // 这里将 command.toString() 作为 selection 参数传递，可能 ContentProvider 会解析它。
            val cursor = contentResolver.query(
                uri,
                null,
                command.toString(), // 传递 JSON 命令字符串作为 selection
                null,
                null
            )

            // 使用 cursor?.use {} 语法确保 Cursor 在使用完毕后被正确关闭。
            cursor?.use {
                // 检查 Cursor 是否包含至少一行数据。
                if (it.moveToFirst()) { // 如果有数据
                    // 获取第一列（索引为0）的数据，假设它是一个包含 JSON 的字符串。
                    val result = it.getString(0)
                    // 将字符串解析为 JSONObject。
                    val jsonResponse = JSONObject(result)

                    // 检查 JSON 响应中的状态字段是否为 "success"。
                    if (jsonResponse.getString("status") == "success") { // 如果成功
                        // 从 JSON 响应中获取数据字段的内容。
                        val data = jsonResponse.getString("data")
                        // 将获取到的数据设置到 responseText TextView 中显示。
                        responseText.text = data
                        // 更新状态文本，显示接收到的数据长度。
                        statusText.text = "Phone state received: ${data.length} characters"
                        // 显示一个成功的 Toast 消息。
                        Toast.makeText(this, "Phone state received successfully!", Toast.LENGTH_SHORT).show()

                        // 记录一条调试日志，显示接收到的数据的前100个字符（或全部字符，如果少于100）。
                        Log.d("DROIDRUN_MAIN", "Phone state received: ${data.substring(0, Math.min(100, data.length))}...")
                    } else { // 如果状态不是 "success"
                        // 从 JSON 响应中获取错误信息。
                        val error = jsonResponse.getString("error")
                        // 更新状态文本，显示错误信息。
                        statusText.text = "Error: $error"
                        // 将错误信息设置到 responseText TextView 中显示。
                        responseText.text = error
                    }
                }
            }

        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 更新状态文本，显示错误信息。
            statusText.text = "Error fetching phone state: ${e.message}"
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error fetching phone state: ${e.message}")
        }
    }

    /**
     * isAccessibilityServiceEnabled 方法用于检查 DroidrunAccessibilityService 是否已在系统设置中启用。
     * @return 如果服务已启用则返回 true，否则返回 false。
     */
    private fun isAccessibilityServiceEnabled(): Boolean { // 定义私有方法 isAccessibilityServiceEnabled，返回布尔值。
        // 构造服务的完整类名字符串，格式为 packageName/ClassName。
        val accessibilityServiceName = packageName + "/" + DroidrunAccessibilityService::class.java.canonicalName

        try { // 使用 try-catch 块捕获可能的异常
            // 从系统安全设置中获取所有已启用的辅助功能服务列表字符串。
            val enabledServices = Settings.Secure.getString(
                contentResolver, // 用于访问 ContentProvider 的 ContentResolver 实例
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES // 设置项的键名
            )

            // 检查获取到的服务列表字符串是否不为 null，并且是否包含我们自己的服务名称。
            // 使用安全调用 (?.) 和 contains 方法进行检查。
            return enabledServices?.contains(accessibilityServiceName) == true
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error checking accessibility status: ${e.message}")
            // 如果发生异常，则默认返回 false。
            return false
        }
    }

    /**
     * updateAccessibilityStatusIndicator 方法用于根据 isAccessibilityServiceEnabled 的结果更新 UI 上的辅助功能服务状态指示器。
     */
    private fun updateAccessibilityStatusIndicator() { // 定义私有方法 updateAccessibilityStatusIndicator。
        // 调用 isAccessibilityServiceEnabled 方法检查服务是否已启用。
        val isEnabled = isAccessibilityServiceEnabled()

        // 根据服务启用状态更新 UI。
        if (isEnabled) { // 如果服务已启用
            // 将指示器的背景设置为绿色圆形，表示已启用。
            accessibilityIndicator.setBackgroundResource(R.drawable.circle_indicator_green)
            // 将状态文本设置为 "ENABLED"。
            accessibilityStatusText.text = "ENABLED"
            // 将卡片背景色设置为预定义的次要颜色。
            accessibilityStatusCard.setCardBackgroundColor(resources.getColor(R.color.droidrun_secondary, null))
        } else { // 如果服务未启用
            // 将指示器的背景设置为红色圆形，表示未启用。
            accessibilityIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
            // 将状态文本设置为 "DISABLED"。
            accessibilityStatusText.text = "DISABLED"
            // 将卡片背景色设置为预定义的次要颜色。
            accessibilityStatusCard.setCardBackgroundColor(resources.getColor(R.color.droidrun_secondary, null))
        }
    }

    /**
     * openAccessibilitySettings 方法用于启动系统的辅助功能设置页面，引导用户启用 DroidrunAccessibilityService。
     */
    private fun openAccessibilitySettings() { // 定义私有方法 openAccessibilitySettings。
        try { // 使用 try-catch 块捕获可能的异常
            // 创建一个 Intent，动作为 ACTION_ACCESSIBILITY_SETTINGS，用于打开辅助功能设置。
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            // 启动设置页面的 Activity。
            startActivity(intent)
            // 显示一个较长的 Toast 消息，提示用户在设置中启用服务。
            Toast.makeText(
                this,
                "Please enable Droidrun Portal in Accessibility Services",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error opening accessibility settings: ${e.message}")
            // 显示一个较短的 Toast 消息，提示打开设置失败。
            Toast.makeText(
                this,
                "Error opening accessibility settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * setupSocketServerControls 方法用于配置 Socket 服务器相关的 UI 控件，如端口输入框。
     */
    private fun setupSocketServerControls() { // 定义私有方法 setupSocketServerControls。
        // 获取 ConfigManager 的单例实例，用于读取和保存配置。
        val configManager = ConfigManager.getInstance(this) // 传入 Context (this)

        // 设置 Socket 服务器端口输入框的初始值为 ConfigManager 中保存的端口。
        // 在设置初始文本之前，设置标志位 isProgrammaticUpdate 为 true。
        isProgrammaticUpdate = true
        // 将 ConfigManager 中的 socketServerPort 转换为字符串并设置到输入框。
        socketPortInput.setText(configManager.socketServerPort.toString())
        // 文本设置完成后，将标志位重置为 false。
        isProgrammaticUpdate = false

        // 为 Socket 端口输入框添加文本变化监听器，用于验证和应用端口设置。
        socketPortInput.addTextChangedListener(object : TextWatcher { // 创建匿名内部类实现 TextWatcher 接口
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} // 空实现
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} // 空实现

            /**
             * afterTextChanged 在文本改变后调用。
             * @param s 改变后的文本 (Editable 对象)。
             */
            override fun afterTextChanged(s: Editable?) { // 重写 afterTextChanged 方法
                // 检查是否是程序代码更新了文本，如果是，则跳过后续处理。
                if (isProgrammaticUpdate) return // 如果是程序更新，直接返回

                try { // 使用 try-catch 块捕获可能的 NumberFormatException
                    // 获取输入框中的文本内容。
                    val portText = s.toString()
                    // 检查文本是否不为空。
                    if (portText.isNotEmpty()) { // 如果输入不为空
                        // 尝试将文本内容转换为整数。
                        val port = portText.toIntOrNull()
                        // 检查转换是否成功且端口号在有效范围内 (1-65535)。
                        if (port != null && port in 1..65535) { // 如果是有效的端口号
                            // 清除 TextInputLayout 的错误信息。
                            socketPortInputLayout.error = null
                            // 调用 updateSocketServerPort 方法，应用新的端口号。
                            updateSocketServerPort(port)
                        } else { // 如果端口号无效
                            // 设置 TextInputLayout 的错误信息，提示用户端口范围。
                            socketPortInputLayout.error = "Port must be between 1-65535"
                        }
                    } else { // 如果输入为空
                        // 清除 TextInputLayout 的错误信息。
                        socketPortInputLayout.error = null
                    }
                } catch (e: Exception) { // 捕获任何其他可能的异常
                    // 设置 TextInputLayout 的错误信息，提示用户输入无效。
                    socketPortInputLayout.error = "Invalid port number"
                }
            }
        })

        // 调用 updateSocketServerStatus 方法，更新 Socket 服务器状态的 UI 显示。
        updateSocketServerStatus()
        // 调用 updateAdbForwardCommand 方法，更新 ADB 转发命令的 UI 显示。
        updateAdbForwardCommand()
    }

    /**
     * updateSocketServerPort 方法用于更新 Socket 服务器的端口号，并保存到 ConfigManager。
     * @param port 新的端口号。
     */
    private fun updateSocketServerPort(port: Int) { // 定义私有方法 updateSocketServerPort，接收一个整数参数 port。
        try { // 使用 try-catch 块捕获可能的异常
            // 获取 ConfigManager 的单例实例。
            val configManager = ConfigManager.getInstance(this) // 传入 Context (this)
            // 将新的端口号保存到 ConfigManager 中。
            configManager.socketServerPort = port

            // 更新状态文本，显示端口已更新。
            statusText.text = "Socket server port updated to: $port"
            // 调用 updateAdbForwardCommand 方法，因为端口改变后 ADB 命令也需要更新。
            updateAdbForwardCommand()

            // 记录一条调试日志，包含更新的端口号。
            Log.d("DROIDRUN_MAIN", "Socket server port updated: $port")
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 更新状态文本，显示错误信息。
            statusText.text = "Error updating socket server port: ${e.message}"
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error updating socket server port: ${e.message}")
        }
    }

    /**
     * updateSocketServerStatus 方法用于从 DroidrunAccessibilityService 获取 Socket 服务器的当前状态并更新 UI。
     */
    private fun updateSocketServerStatus() { // 定义私有方法 updateSocketServerStatus。
        try { // 使用 try-catch 块捕获可能的异常
            // 获取 DroidrunAccessibilityService 的单例实例。
            val accessibilityService = DroidrunAccessibilityService.getInstance()
            // 检查辅助功能服务实例是否可用。
            if (accessibilityService != null) { // 如果服务实例存在
                // 调用服务的 getSocketServerStatus 方法获取状态字符串。
                val status = accessibilityService.getSocketServerStatus()
                // 将获取到的状态字符串设置到 socketServerStatus TextView 中显示。
                socketServerStatus.text = status
            } else { // 如果服务实例不存在
                // 将 socketServerStatus TextView 的文本设置为 "Service not available"。
                socketServerStatus.text = "手机未开启辅助功能服务"
            }
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error updating socket server status: ${e.message}")
            // 将 socketServerStatus TextView 的文本设置为 "Error"。
            socketServerStatus.text = "Error"
        }
    }

    /**
     * updateAdbForwardCommand 方法用于生成并显示当前 Socket 服务器对应的 ADB 端口转发命令。
     */
    private fun updateAdbForwardCommand() { // 定义私有方法 updateAdbForwardCommand。
        try { // 使用 try-catch 块捕获可能的异常
            // 获取 DroidrunAccessibilityService 的单例实例。
            val accessibilityService = DroidrunAccessibilityService.getInstance()
            // 检查辅助功能服务实例是否可用。
            if (accessibilityService != null) { // 如果服务实例存在
                // 调用服务的 getAdbForwardCommand 方法获取完整的 ADB 命令字符串。
                val command = accessibilityService.getAdbForwardCommand()
                // 将获取到的命令字符串设置到 adbForwardCommand TextView 中显示。
                adbForwardCommand.text = command
            } else { // 如果服务实例不存在
                // 如果服务不可用，则从 ConfigManager 获取端口号来构建默认命令。
                val configManager = ConfigManager.getInstance(this) // 传入 Context (this)
                val port = configManager.socketServerPort // 获取端口号
                // 构建默认的 ADB 转发命令字符串。
                adbForwardCommand.text = "adb forward tcp:$port tcp:$port"
            }
        } catch (e: Exception) { // 捕获任何其他可能的异常
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error updating ADB forward command: ${e.message}")
            // 将 adbForwardCommand TextView 的文本设置为 "Error"。
            adbForwardCommand.text = "Error"
        }
    }

    /**
     * setAppVersion 方法用于获取并显示当前应用程序的版本号。
     */
    private fun setAppVersion() { // 定义私有方法 setAppVersion。
        try { // 使用 try-catch 块捕获可能的 PackageManager.NameNotFoundException
            // 通过 packageManager 获取当前应用包的信息，参数为包名和标志位（0表示基本信息）。
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            // 从 packageInfo 中获取版本名字符串。
            val version = packageInfo.versionName
            // 将版本号信息设置到 versionText TextView 中显示。
            versionText.text = "Version: $version"
        } catch (e: Exception) { // 捕获任何其他可能的异常（主要是 PackageManager.NameNotFoundException）
            // 记录一条错误日志，包含异常信息。
            Log.e("DROIDRUN_MAIN", "Error getting app version: ${e.message}")
            // 如果获取失败，将 versionText TextView 的文本设置为 "Version: N/A"。
            versionText.text = "Version: N/A"
        }
    }
}



