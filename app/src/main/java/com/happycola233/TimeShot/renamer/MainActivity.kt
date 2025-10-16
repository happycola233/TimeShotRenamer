package com.happycola233.TimeShot.Renamer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.happycola233.TimeShot.Renamer.MediaRenameViewModel
import com.happycola233.TimeShot.Renamer.ui.theme.TimeShotRenamerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val renameViewModel: MediaRenameViewModel = viewModel()
            PhotoRenamerScreen(viewModel = renameViewModel)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TimeShotRenamerTheme {
        // 预览暂不展示主要界面内容
    }
}
