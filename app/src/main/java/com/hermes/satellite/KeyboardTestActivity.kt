package com.hermes.satellite

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 键盘测试 Activity — 无 enableEdgeToEdge
 *
 * 如果这个 Activity 键盘正常而主 App 不正常，
 * 则问题锁定在 enableEdgeToEdge() 与 adjustResize 的冲突。
 */
class KeyboardTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 故意不调用 enableEdgeToEdge()，测试纯传统 View 行为
        setContentView(R.layout.activity_keyboard_test)
    }
}
