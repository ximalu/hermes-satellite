package com.hermes.satellite

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 键盘测试 Activity — 匹配 Element X 的基础配置
 *
 * 使用 AppCompatActivity + Material3 主题
 * 无 enableEdgeToEdge，使用 adjustResize
 */
class KeyboardTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_test)
    }
}
