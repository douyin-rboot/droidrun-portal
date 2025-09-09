// 定义包名，用于组织和管理相关的类
package com.droidrun.portal

// 导入所需的Android工具类和网络类
import android.util.Log // 用于记录日志
import android.net.Uri // 用于处理URI，特别是解码URL参数
import android.content.ContentValues // 用于存储键值对数据，常用于解析POST请求体
import org.json.JSONObject // 用于处理JSON数据
import java.io.* // 导入Java IO相关的类，如BufferedReader, InputStreamReader等
import java.net.ServerSocket // 服务端套接字，用于监听客户端连接
import java.net.Socket // 客户端套接字，代表一个客户端连接
import java.net.SocketException // 网络套接字异常
import java.util.concurrent.ExecutorService // 线程池接口，用于管理线程
import java.util.concurrent.Executors // 线程池工厂类，用于创建线程池
import java.util.concurrent.atomic.AtomicBoolean // 原子布尔值，用于线程安全地表示服务器运行状态

// 定义Socket服务器类，它需要访问AccessibilityService来获取状态或执行操作
class SocketServer(private val accessibilityService: DroidrunAccessibilityService) {
    // 伴生对象，用于定义类级别的常量和日志标签
    companion object {
        // 定义日志标签，方便在日志中识别此类的日志信息
        private const val TAG = "DroidrunSocketServer"
        // 定义服务器默认监听的端口号
        private const val DEFAULT_PORT = 8080
        // 定义线程池的大小，即同时处理客户端连接的最大线程数
        private const val THREAD_POOL_SIZE = 5
    }

    // 可空的ServerSocket实例，用于监听和接受客户端连接
    private var serverSocket: ServerSocket? = null
    // 使用AtomicBoolean来安全地表示服务器是否正在运行，适用于多线程环境
    private var isRunning = AtomicBoolean(false)
    // 创建一个固定大小的线程池，用于处理客户端连接任务
    private val executorService: ExecutorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    // 存储当前服务器实际监听的端口号，默认为DEFAULT_PORT
    private var port: Int = DEFAULT_PORT

    /**
     * 启动Socket服务器
     * @param port 指定服务器监听的端口号，默认为DEFAULT_PORT
     * @return Boolean 启动成功返回true，失败返回false
     */
    fun start(port: Int = DEFAULT_PORT): Boolean {
        // 检查服务器是否已经在运行
        if (isRunning.get()) {
            // 如果已经在运行，则记录警告日志并返回true
            Log.w(TAG, "Server already running on port ${this.port}")
            return true
        }

        // 设置当前服务器要监听的端口号
        this.port = port
        // 记录启动服务器的日志信息
        Log.i(TAG, "Starting socket server on port $port...")

        // 尝试启动服务器
        return try {
            // 创建ServerSocket实例，绑定到指定端口开始监听
            serverSocket = ServerSocket(port)
            // 将服务器运行状态设置为true
            isRunning.set(true)

            // 记录服务器Socket创建成功的日志
            Log.i(TAG, "ServerSocket created successfully on port $port")

            // 提交一个任务到线程池，异步执行acceptConnections方法来接受客户端连接
            executorService.submit {
                // 记录开始接受连接的线程启动日志
                Log.i(TAG, "Starting connection acceptor thread")
                // 调用acceptConnections方法处理连接
                acceptConnections()
            }

            // 记录服务器启动成功的日志
            Log.i(TAG, "Socket server started successfully on port $port")
            // 返回true表示启动成功
            true
        } catch (e: Exception) {
            // 捕获启动过程中可能发生的异常
            // 记录启动失败的错误日志
            Log.e(TAG, "Failed to start socket server on port $port", e)
            // 将服务器运行状态设置为false
            isRunning.set(false)
            // 返回false表示启动失败
            false
        }
    }

    /**
     * 停止Socket服务器
     */
    fun stop() {
        // 检查服务器是否正在运行，如果未运行则直接返回
        if (!isRunning.get()) return

        // 将服务器运行状态设置为false，通知acceptConnections循环停止
        isRunning.set(false)

        // 尝试关闭资源
        try {
            // 关闭ServerSocket，停止监听新的连接
            serverSocket?.close()
            // 关闭线程池，不再接受新任务，并等待已提交任务完成
            executorService.shutdown()
            // 记录服务器停止成功的日志
            Log.i(TAG, "Socket server stopped")
        } catch (e: Exception) {
            // 捕获关闭资源时可能发生的异常
            // 记录停止服务器时的错误日志
            Log.e(TAG, "Error stopping socket server", e)
        }
    }

    /**
     * 检查服务器是否正在运行
     * @return Boolean 服务器运行返回true，否则返回false
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * 获取服务器当前监听的端口号
     * @return Int 当前监听的端口号
     */
    fun getPort(): Int = port

    /**
     * 接受客户端连接的主循环方法
     */
    private fun acceptConnections() {
        // 记录开始接受连接的日志
        Log.i(TAG, "acceptConnections() started, waiting for clients...")
        // 循环接受客户端连接，只要服务器处于运行状态
        while (isRunning.get()) {
            try {
                // 记录等待客户端连接的日志
                Log.d(TAG, "Waiting for client connection...")
                // 调用ServerSocket的accept方法，阻塞等待客户端连接
                // 如果服务器停止(isRunning变为false)，serverSocket被关闭，accept会抛出SocketException
                val clientSocket = serverSocket?.accept() ?: break
                // 记录客户端连接成功的日志
                Log.i(TAG, "Client connected: ${clientSocket.remoteSocketAddress}")
                // 将处理客户端连接的任务提交到线程池中异步执行，避免阻塞主线程
                executorService.submit {
                    // 调用handleClient方法处理具体的客户端请求
                    handleClient(clientSocket)
                }
            } catch (e: SocketException) {
                // 捕获SocketException，通常发生在服务器关闭时
                // 检查服务器是否仍在运行
                if (isRunning.get()) {
                    // 如果服务器仍在运行但发生了SocketException，则记录错误日志
                    Log.e(TAG, "Socket exception while accepting connections", e)
                } else {
                    // 如果服务器已停止，则记录正常关闭的日志
                    Log.i(TAG, "Socket closed normally during shutdown")
                }
                // 无论哪种情况，都跳出循环，停止接受新连接
                break
            } catch (e: Exception) {
                // 捕获其他可能的异常
                // 记录接受连接时的错误日志
                Log.e(TAG, "Error accepting connection", e)
            }
        }
        // 记录接受连接循环停止的日志
        Log.i(TAG, "acceptConnections() stopped")
    }

    /**
     * 处理单个客户端连接的请求
     * @param clientSocket 代表客户端连接的Socket对象
     */
    private fun handleClient(clientSocket: Socket) {
        // 使用try-with-resources语法，确保Socket在使用完毕后自动关闭
        try {
            Log.d(TAG, "Handling client connection from: ${clientSocket.remoteSocketAddress}")
            clientSocket.use { socket ->
                // 获取客户端输入流，用于读取客户端发送的数据
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                // 获取客户端输出流，用于向客户端发送响应数据
                val writer = OutputStreamWriter(socket.getOutputStream())

                // 读取HTTP请求的第一行（请求行），例如 "GET /a11y_tree HTTP/1.1"
                val requestLine = reader.readLine()
                // 记录读取到的请求行日志
                Log.d(TAG, "Request line: $requestLine")

                // 检查是否成功读取到请求行
                if (requestLine == null) {
                    // 如果没有读取到请求行，则记录警告日志并返回
                    Log.w(TAG, "No request line received")
                    return
                }

                // 按空格分割请求行，得到方法、路径和协议版本
                val parts = requestLine.split(" ")

                // 检查分割后的部分是否足够（至少需要方法和路径）
                if (parts.size < 2) {
                    // 如果格式不正确，则记录警告日志
                    Log.w(TAG, "Invalid request line format: $requestLine")
                    // 发送400 Bad Request错误响应
                    sendErrorResponse(writer, 400, "Bad Request")
                    return
                }

                // 提取HTTP方法（如GET、POST）
                val method = parts[0]
                // 提取请求路径（如/a11y_tree）
                val path = parts[1]
                // 记录处理请求的日志
                Log.d(TAG, "Processing request: $method $path")

                // 跳过HTTP请求头部分（简化处理，不解析具体头信息）
                var line: String?
                do {
                    // 逐行读取请求头
                    line = reader.readLine()
                    // 如果读取到非空行，则记录为请求头
                    if (line != null) Log.v(TAG, "Header: $line")
                    // 循环直到读取到空行，表示请求头结束
                } while (line?.isNotEmpty() == true)

                // 根据HTTP方法和路径处理请求，并生成响应内容
                val response = when (method) {
                    // 如果是GET请求，则调用handleGetRequest方法处理
                    "GET" -> {
                        Log.d(TAG, "Handling GET request for: $path")
                        handleGetRequest(path)
                    }
                    // 如果是POST请求，则调用handlePostRequest方法处理
                    "POST" -> {
                        Log.d(TAG, "Handling POST request for: $path")
                        handlePostRequest(path, reader)
                    }
                    // 对于其他不支持的方法，返回错误响应
                    else -> {
                        Log.w(TAG, "Unsupported method: $method")
                        createErrorResponse("Method not allowed: $method")
                    }
                }

                // 记录即将发送的响应内容（仅前100个字符）的日志
                Log.d(TAG, "Sending response: ${response.take(100)}...")
                // 发送HTTP响应给客户端
                sendHttpResponse(writer, response)
            }
        } catch (e: Exception) {
            // 捕获处理客户端请求时可能发生的异常
            // 记录处理客户端时的错误日志
            Log.e(TAG, "Error handling client", e)
        }
    }

    /**
     * 处理GET类型的HTTP请求
     * @param path 请求的路径
     * @return String 返回JSON格式的响应字符串
     */
    private fun handleGetRequest(path: String): String {
        // 记录处理GET请求的日志
        Log.d(TAG, "Handling GET request for path: $path")
        // 根据请求路径进行路由分发
        return when {
            // 如果路径以"/a11y_tree"开头，则处理获取无障碍树的请求
            path.startsWith("/a11y_tree") -> {
                Log.d(TAG, "Processing /a11y_tree request")
                getAccessibilityTree()
            }
            // 如果路径以"/phone_state"开头，则处理获取手机状态的请求
            path.startsWith("/phone_state") -> {
                Log.d(TAG, "Processing /phone_state request")
                getPhoneState()
            }
            // 如果路径以"/state"开头，则处理获取组合状态（无障碍树+手机状态）的请求
            path.startsWith("/state") -> {
                Log.d(TAG, "Processing /state request")
                getCombinedState()
            }
            // 如果路径以"/ping"开头，则处理ping请求，用于健康检查
            path.startsWith("/ping") -> {
                Log.d(TAG, "Processing /ping request")
                createSuccessResponse("pong")
            }
            // 如果路径以"/screenshot"开头，则处理获取屏幕截图的请求
            path.startsWith("/screenshot") -> {
                Log.d(TAG, "Processing /screenshot request")
                getScreenshot(path)
            }
            // 对于未知的路径，返回错误响应
            else -> {
                Log.w(TAG, "Unknown endpoint requested: $path")
                createErrorResponse("Unknown endpoint: $path")
            }
        }
    }

    /**
     * 处理POST类型的HTTP请求
     * @param path 请求的路径
     * @param reader 用于读取POST请求体的BufferedReader
     * @return String 返回JSON格式的响应字符串
     */
    private fun handlePostRequest(path: String, reader: BufferedReader): String {
        // 根据请求路径进行路由分发
        return when {
            // 如果路径以"/keyboard/"开头，则处理键盘相关的操作
            path.startsWith("/keyboard/") -> {
                // 从路径中提取具体的操作（例如"/keyboard/input"中的"input"）
                val action = path.substringAfterLast("/")
                // 调用handleKeyboardAction方法处理具体的键盘操作
                handleKeyboardAction(action, reader)
            }
            // 如果路径以"/overlay_offset"开头，则处理设置覆盖层偏移量的请求
            path.startsWith("/overlay_offset") -> {
                // 调用handleOverlayOffset方法处理偏移量设置
                handleOverlayOffset(reader)
            }
            // 对于其他未知的POST路径，返回错误响应
            else -> createErrorResponse("Unknown POST endpoint: $path")
        }
    }

    /**
     * 处理键盘相关的POST请求操作
     * @param action 具体的键盘操作名称（如input, clear, key）
     * @param reader 用于读取POST请求体的BufferedReader
     * @return String 返回操作结果的字符串
     */
    private fun handleKeyboardAction(action: String, reader: BufferedReader): String {
        // 使用try-catch块捕获处理过程中的异常
        return try {
            // 初始化内容长度和POST数据存储
            val contentLength = 0 // 本实现中未实际使用此变量
            val postData = StringBuilder()
            // 检查reader中是否有数据可读
            if (reader.ready()) {
                // 创建字符数组缓冲区
                val char = CharArray(1024)
                // 从reader中读取字符到缓冲区
                val bytesRead = reader.read(char)
                // 如果读取到了数据，则将其追加到postData中
                if (bytesRead > 0) {
                    postData.append(char, 0, bytesRead)
                }
            }

            // 解析POST请求体中的数据，得到键值对
            val values = parsePostData(postData.toString())

            // 根据具体的操作名称执行相应的处理
            when (action) {
                // 如果操作是"input"，则处理输入文本的请求
                "input" -> performKeyboardInputBase64(values)
                // 如果操作是"clear"，则处理清空文本的请求
                "clear" -> performKeyboardClear()
                // 如果操作是"key"，则处理发送按键事件的请求
                "key" -> performKeyboardKey(values)
                // 对于未知的操作，返回错误信息
                else -> "error: Unknown keyboard action: $action"
            }
        } catch (e: Exception) {
            // 捕获处理过程中的异常
            // 记录错误日志
            Log.e(TAG, "Error handling keyboard action", e)
            // 返回包含错误信息的字符串
            "error: Failed to handle keyboard action: ${e.message}"
        }
    }

    /**
     * 处理设置覆盖层偏移量的POST请求
     * @param reader 用于读取POST请求体的BufferedReader
     * @return String 返回操作结果的字符串
     */
    private fun handleOverlayOffset(reader: BufferedReader): String {
        // 使用try-catch块捕获处理过程中的异常
        return try {
            // 初始化POST数据存储
            val postData = StringBuilder()
            // 检查reader中是否有数据可读
            if (reader.ready()) {
                // 创建字符数组缓冲区
                val char = CharArray(1024)
                // 从reader中读取字符到缓冲区
                val bytesRead = reader.read(char)
                // 如果读取到了数据，则将其追加到postData中
                if (bytesRead > 0) {
                    postData.append(char, 0, bytesRead)
                }
            }

            // 解析POST请求体中的数据，得到键值对
            val values = parsePostData(postData.toString())
            // 从解析出的数据中获取"offset"的整数值，如果不存在则返回错误
            val offset = values.getAsInteger("offset")
                ?: return "error: No offset provided"

            // 调用accessibilityService的setOverlayOffset方法设置偏移量
            val success = accessibilityService.setOverlayOffset(offset)

            // 根据设置结果返回相应的成功或失败信息
            if (success) {
                "success: Overlay offset updated to $offset"
            } else {
                "error: Failed to update overlay offset"
            }
        } catch (e: Exception) {
            // 捕获处理过程中的异常
            // 记录错误日志
            Log.e(TAG, "Error handling overlay offset", e)
            // 返回包含错误信息的字符串
            "error: Failed to handle overlay offset: ${e.message}"
        }
    }

    /**
     * 解析POST请求体中的数据
     * @param data 包含POST数据的字符串
     * @return ContentValues 解析后的键值对数据
     */
    private fun parsePostData(data: String): ContentValues {
        // 创建一个ContentValues实例用于存储解析出的键值对
        val values = ContentValues()

        // 如果数据为空白，则直接返回空的ContentValues
        if (data.isBlank()) return values

        // 使用try-catch块捕获解析过程中的异常
        try {
            // 首先尝试将数据解析为JSON格式
            if (data.trim().startsWith("{")) {
                // 创建JSONObject实例
                val json = JSONObject(data)
                // 遍历JSON对象中的所有键
                json.keys().forEach { key ->
                    // 获取键对应的值
                    val value = json.get(key)
                    // 根据值的类型将其放入ContentValues中
                    when (value) {
                        is String -> values.put(key, value)
                        is Int -> values.put(key, value)
                        is Boolean -> values.put(key, value)
                        else -> values.put(key, value.toString())
                    }
                }
            } else {
                // 如果不是JSON格式，则尝试解析为URL编码的格式（key1=value1&key2=value2）
                data.split("&").forEach { pair ->
                    // 按"="分割键值对
                    val parts = pair.split("=", limit = 2)
                    // 确保分割后有键和值两部分
                    if (parts.size == 2) {
                        // 对键和值进行URL解码
                        val key = Uri.decode(parts[0])
                        val value = Uri.decode(parts[1])

                        // 尝试将值解析为整数
                        val intValue = value.toIntOrNull()
                        if (intValue != null) {
                            // 如果是整数，则以整数形式存储
                            values.put(key, intValue)
                        } else {
                            // 如果不是整数，则尝试解析为布尔值
                            when (value.lowercase()) {
                                "true" -> values.put(key, true)
                                "false" -> values.put(key, false)
                                else -> values.put(key, value)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 捕获解析过程中的异常
            // 记录错误日志
            Log.e(TAG, "Error parsing POST data", e)
        }

        // 返回解析后的ContentValues
        return values
    }

    /**
     * 发送标准的HTTP成功响应
     * @param writer 用于向客户端写入数据的OutputStreamWriter
     * @param response 要发送的响应体内容（JSON字符串）
     */
    private fun sendHttpResponse(writer: OutputStreamWriter, response: String) {
        // 使用try-catch块捕获发送过程中的异常
        try {
            // 将响应体字符串转换为字节数组，用于计算Content-Length
            val responseBytes = response.toByteArray(Charsets.UTF_8)
            // 构建完整的HTTP响应头和响应体
            val httpResponse = "HTTP/1.1 200 OK\r\n" +
                    // 设置响应内容类型为JSON
                    "Content-Type: application/json\r\n" +
                    // 设置响应内容长度
                    "Content-Length: ${responseBytes.size}\r\n" +
                    // 设置CORS头，允许跨域请求
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type\r\n" +
                    // 设置连接为关闭，表示响应后断开连接
                    "Connection: close\r\n" +
                    // 空行，分隔响应头和响应体
                    "\r\n" +
                    // 实际的响应体内容
                    response

            // 将构建好的HTTP响应写入输出流
            writer.write(httpResponse)
            // 刷新输出流，确保数据发送出去
            writer.flush()
            // 记录发送响应的日志
            Log.d(TAG, "HTTP response sent: ${response.length} chars")
        } catch (e: Exception) {
            // 捕获发送过程中的异常
            // 记录发送HTTP响应时的错误日志
            Log.e(TAG, "Error sending HTTP response", e)
        }
    }

    /**
     * 发送标准的HTTP错误响应
     * @param writer 用于向客户端写入数据的OutputStreamWriter
     * @param code HTTP错误状态码（如400, 404, 500）
     * @param message HTTP错误状态消息（如"Bad Request", "Not Found"）
     */
    private fun sendErrorResponse(writer: OutputStreamWriter, code: Int, message: String) {
        // 使用try-catch块捕获发送过程中的异常
        try {
            // 创建包含错误信息的JSON响应体
            val errorResponse = createErrorResponse(message)
            // 将错误响应体字符串转换为字节数组，用于计算Content-Length
            val responseBytes = errorResponse.toByteArray(Charsets.UTF_8)
            // 构建完整的HTTP错误响应头和响应体
            val httpResponse = "HTTP/1.1 $code $message\r\n" +
                    // 设置响应内容类型为JSON
                    "Content-Type: application/json\r\n" +
                    // 设置响应内容长度
                    "Content-Length: ${responseBytes.size}\r\n" +
                    // 设置连接为关闭
                    "Connection: close\r\n" +
                    // 空行，分隔响应头和响应体
                    "\r\n" +
                    // 实际的错误响应体内容
                    errorResponse

            // 将构建好的HTTP错误响应写入输出流
            writer.write(httpResponse)
            // 刷新输出流，确保数据发送出去
            writer.flush()
            // 记录发送错误响应的日志
            Log.d(TAG, "HTTP error response sent: $code $message")
        } catch (e: Exception) {
            // 捕获发送过程中的异常
            // 记录发送错误响应时的错误日志
            Log.e(TAG, "Error sending error response", e)
        }
    }

    // 以下是一些处理具体业务逻辑的方法，它们调用accessibilityService或相关工具类来完成任务

    /**
     * 获取当前屏幕的无障碍树信息
     * @return String 返回JSON格式的无障碍树数据
     */
    private fun getAccessibilityTree(): String {
        // 使用try-catch块捕获获取过程中的异常
        return try {
            // 记录开始获取无障碍树的日志
            Log.d(TAG, "Getting accessibility tree...")
            // 调用accessibilityService获取可见的元素列表
            val elements = accessibilityService.getVisibleElements()
            // 记录找到的元素数量日志
            Log.d(TAG, "Found ${elements.size} visible elements")

            // 将每个元素转换为JSON对象
            val treeJson = elements.map { element ->
                buildElementNodeJson(element)
            }
            // 创建包含无障碍树数据的成功响应
            val response = createSuccessResponse(treeJson.toString())
            // 记录创建响应的日志
            Log.d(TAG, "Accessibility tree response created: ${response.length} chars")
            // 返回响应字符串
            response
        } catch (e: Exception) {
            // 捕获获取过程中的异常
            // 记录获取失败的错误日志
            Log.e(TAG, "Failed to get accessibility tree", e)
            // 返回包含错误信息的响应
            createErrorResponse("Failed to get accessibility tree: ${e.message}")
        }
    }

    /**
     * 获取当前手机的状态信息
     * @return String 返回JSON格式的手机状态数据
     */
    private fun getPhoneState(): String {
        // 使用try-catch块捕获获取过程中的异常
        return try {
            // 记录开始获取手机状态的日志
            Log.d(TAG, "Getting phone state...")
            // 调用accessibilityService获取手机状态对象
            val phoneState = accessibilityService.getPhoneState()
            // 将手机状态对象转换为JSON对象
            val phoneStateJson = buildPhoneStateJson(phoneState)
            // 创建包含手机状态数据的成功响应
            val response = createSuccessResponse(phoneStateJson.toString())
            // 记录创建响应的日志
            Log.d(TAG, "Phone state response created: ${response.length} chars")
            // 返回响应字符串
            response
        } catch (e: Exception) {
            // 捕获获取过程中的异常
            // 记录获取失败的错误日志
            Log.e(TAG, "Failed to get phone state", e)
            // 返回包含错误信息的响应
            createErrorResponse("Failed to get phone state: ${e.message}")
        }
    }

    /**
     * 获取组合状态信息（无障碍树 + 手机状态）
     * @return String 返回JSON格式的组合状态数据
     */
    private fun getCombinedState(): String {
        // 使用try-catch块捕获获取过程中的异常
        return try {
            // 获取无障碍树数据
            val treeJson = accessibilityService.getVisibleElements().map { element ->
                buildElementNodeJson(element)
            }

            // 获取手机状态数据
            val phoneStateJson = buildPhoneStateJson(accessibilityService.getPhoneState())

            // 将两者组合成一个JSON对象
            val combinedState = JSONObject().apply {
                put("a11y_tree", org.json.JSONArray(treeJson))
                put("phone_state", phoneStateJson)
            }

            // 创建包含组合状态数据的成功响应
            createSuccessResponse(combinedState.toString())
        } catch (e: Exception) {
            // 捕获获取过程中的异常
            // 记录获取失败的错误日志
            Log.e(TAG, "Failed to get combined state", e)
            // 返回包含错误信息的响应
            createErrorResponse("Failed to get combined state: ${e.message}")
        }
    }

    /**
     * 获取当前屏幕的截图（Base64编码）
     * @param path 请求路径，可能包含查询参数
     * @return String 返回Base64编码的截图数据或错误信息
     */
    private fun getScreenshot(path: String): String {
        // 使用try-catch块捕获获取过程中的异常
        return try {
            // 解析查询参数，判断是否需要隐藏覆盖层
            val hideOverlay = if (path.contains("?")) {
                // 提取查询字符串部分
                val queryString = path.substringAfter("?")
                // 将查询字符串分割为键值对，并构造成Map
                val params = queryString.split("&").associate { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) {
                        // 对键和值进行URL解码
                        Uri.decode(parts[0]) to Uri.decode(parts[1])
                    } else {
                        // 如果没有值，则默认为"true"
                        parts[0] to "true"
                    }
                }
                // 默认隐藏覆盖层，除非显式设置hideOverlay=false
                params["hideOverlay"]?.lowercase() != "false"
            } else {
                true // 默认值：隐藏覆盖层
            }

            // 记录开始截图的日志
            Log.d(TAG, "Taking screenshot... (hideOverlay: $hideOverlay)")
            // 调用accessibilityService获取Base64编码的截图，返回一个Future对象
            val screenshotFuture = accessibilityService.takeScreenshotBase64(hideOverlay)

            // 等待截图完成，设置5秒超时时间
            val screenshotBase64 = screenshotFuture.get(5, java.util.concurrent.TimeUnit.SECONDS)

            // 检查截图结果是否为错误信息
            if (screenshotBase64.startsWith("error:")) {
                // 如果是错误信息，则记录错误日志
                Log.e(TAG, "Screenshot failed: $screenshotBase64")
                // 去掉"error:"前缀后，创建错误响应
                createErrorResponse(screenshotBase64.substring(7)) // Remove "error:" prefix
            } else {
                // 如果截图成功，则记录成功日志
                Log.d(TAG, "Screenshot captured successfully, base64 length: ${screenshotBase64.length}")
                // 创建包含Base64截图数据的成功响应
                createSuccessResponse(screenshotBase64)
            }
        } catch (e: java.util.concurrent.TimeoutException) {
            // 捕获超时异常
            // 记录超时错误日志
            Log.e(TAG, "Screenshot timeout", e)
            // 返回超时错误响应
            createErrorResponse("Screenshot timeout - operation took too long")
        } catch (e: Exception) {
            // 捕获其他获取过程中的异常
            // 记录获取失败的错误日志
            Log.e(TAG, "Failed to get screenshot", e)
            // 返回包含错误信息的响应
            createErrorResponse("Failed to get screenshot: ${e.message}")
        }
    }

    /**
     * 将ElementNode对象转换为JSON对象
     * @param element 要转换的ElementNode对象
     * @return JSONObject 转换后的JSON对象
     */
    private fun buildElementNodeJson(element: com.droidrun.portal.model.ElementNode): JSONObject {
        // 创建并配置JSONObject
        return JSONObject().apply {
            // 添加元素的索引
            put("index", element.overlayIndex)
            // 添加资源ID
            put("resourceId", element.nodeInfo.viewIdResourceName ?: "")
            // 添加类名
            put("className", element.className)
            // 添加文本内容
            put("text", element.text)
            // 添加边界坐标信息
            put("bounds", "${element.rect.left}, ${element.rect.top}, ${element.rect.right}, ${element.rect.bottom}")

            // 递归处理子元素
            val childrenArray = org.json.JSONArray()
            element.children.forEach { child ->
                // 将每个子元素也转换为JSON对象并添加到数组中
                childrenArray.put(buildElementNodeJson(child))
            }
            // 将子元素数组添加到当前JSON对象中
            put("children", childrenArray)
        }
    }

    /**
     * 将PhoneState对象转换为JSON对象
     * @param phoneState 要转换的PhoneState对象
     * @return JSONObject 转换后的JSON对象
     */
    private fun buildPhoneStateJson(phoneState: com.droidrun.portal.model.PhoneState) =
        // 创建并配置JSONObject
        JSONObject().apply {
            // 添加当前应用名称
            put("currentApp", phoneState.appName)
            // 添加当前应用包名
            put("packageName", phoneState.packageName)
            // 添加键盘是否可见的状态
            put("keyboardVisible", phoneState.keyboardVisible)
            // 添加当前获得焦点的元素信息
            put("focusedElement", JSONObject().apply {
                // 添加焦点元素的文本
                put("text", phoneState.focusedElement?.text)
                // 添加焦点元素的类名
                put("className", phoneState.focusedElement?.className)
                // 添加焦点元素的资源ID
                put("resourceId", phoneState.focusedElement?.viewIdResourceName ?: "")
            })
        }

    /**
     * 通过键盘输入Base64编码的文本
     * @param values 包含"base64_text"键的ContentValues
     * @return String 返回操作结果的字符串
     */
    private fun performKeyboardInputBase64(values: ContentValues): String {
        // 获取DroidrunKeyboardIME的单例实例，如果不存在则返回错误
        val keyboardIME = DroidrunKeyboardIME.getInstance()
            ?: return "error: DroidrunKeyboardIME not active or available"

        // 检查是否有可用的输入连接（即键盘是否聚焦在输入框上）
        if (!keyboardIME.hasInputConnection()) {
            return "error: No input connection available - keyboard may not be focused on an input field"
        }

        // 从ContentValues中获取Base64编码的文本，如果不存在则返回错误
        val base64Text = values.getAsString("base64_text")
            ?: return "error: No base64_text provided"

        // 使用try-catch块捕获解码和输入过程中的异常
        return try {
            // 调用keyboardIME的inputB64Text方法输入Base64文本
            if (keyboardIME.inputB64Text(base64Text)) {
                // 如果输入成功，则解码Base64文本用于日志记录
                val decoded = android.util.Base64.decode(base64Text, android.util.Base64.DEFAULT)
                val decodedText = String(decoded, Charsets.UTF_8)
                // 返回成功信息，包含解码后的文本
                "success: Base64 text input via keyboard - '$decodedText'"
            } else {
                // 如果输入失败，则返回错误信息
                "error: Failed to input base64 text via keyboard"
            }
        } catch (e: Exception) {
            // 捕获解码过程中的异常
            // 返回包含错误信息的字符串
            "error: Invalid base64 encoding: ${e.message}"
        }
    }

    /**
     * 清空当前输入框中的文本
     * @return String 返回操作结果的字符串
     */
    private fun performKeyboardClear(): String {
        // 获取DroidrunKeyboardIME的单例实例，如果不存在则返回错误
        val keyboardIME = DroidrunKeyboardIME.getInstance()
            ?: return "error: DroidrunKeyboardIME not active or available"

        // 检查是否有可用的输入连接
        if (!keyboardIME.hasInputConnection()) {
            return "error: No input connection available - keyboard may not be focused on an input field"
        }

        // 调用keyboardIME的clearText方法清空文本
        return if (keyboardIME.clearText()) {
            // 如果清空成功，则返回成功信息
            "success: Text cleared via keyboard"
        } else {
            // 如果清空失败，则返回错误信息
            "error: Failed to clear text via keyboard"
        }
    }

    /**
     * 发送指定键码的按键事件
     * @param values 包含"key_code"键的ContentValues
     * @return String 返回操作结果的字符串
     */
    private fun performKeyboardKey(values: ContentValues): String {
        // 获取DroidrunKeyboardIME的单例实例，如果不存在则返回错误
        val keyboardIME = DroidrunKeyboardIME.getInstance()
            ?: return "error: DroidrunKeyboardIME not active or available"

        // 检查是否有可用的输入连接
        if (!keyboardIME.hasInputConnection()) {
            return "error: No input connection available - keyboard may not be focused on an input field"
        }

        // 从ContentValues中获取键码，如果不存在则返回错误
        val keyCode = values.getAsInteger("key_code")
            ?: return "error: No key_code provided"

        // 调用keyboardIME的sendKeyEventDirect方法发送按键事件
        return if (keyboardIME.sendKeyEventDirect(keyCode)) {
            // 如果发送成功，则返回成功信息，包含键码
            "success: Key event sent via keyboard - code: $keyCode"
        } else {
            // 如果发送失败，则返回错误信息
            "error: Failed to send key event via keyboard"
        }
    }

    /**
     * 创建标准的成功响应JSON字符串
     * @param data 成功响应中包含的数据（通常是JSON字符串）
     * @return String 标准的成功响应JSON字符串
     */
    private fun createSuccessResponse(data: String): String {
        // 创建并配置JSONObject
        return JSONObject().apply {
            // 设置状态为"success"
            put("status", "success")
            // 添加数据部分
            put("data", data)
        }.toString()
    }

    /**
     * 创建标准的错误响应JSON字符串
     * @param error 错误信息
     * @return String 标准的错误响应JSON字符串
     */
    private fun createErrorResponse(error: String): String {
        // 创建并配置JSONObject
        return JSONObject().apply {
            // 设置状态为"error"
            put("status", "error")
            // 添加错误信息部分
            put("error", error)
        }.toString()
    }
}



