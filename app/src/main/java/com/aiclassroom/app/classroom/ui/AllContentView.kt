package com.aiclassroom.app.classroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiclassroom.app.classroom.ClassroomService
import com.aiclassroom.classroom.domain.ContentBlock
import com.aiclassroom.classroom.domain.ProjectId

/**
 * 全书查看：把所有已提交的节按顺序连起来。
 *
 * 只读正式版本，草稿与未提交内容不出现在这里 —— 它是「看成品」的入口，
 * 不是继续编辑的工作区。
 */
@Composable
internal fun AllContentView(
    service: ClassroomService,
    projectId: ProjectId,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val sections = remember(projectId) {
        service.store.units(projectId).sortedBy { it.index }.mapNotNull { unit ->
            val revision = service.store.officialRevision(unit.id) ?: return@mapNotNull null
            val content = revision.content ?: return@mapNotNull null
            Triple(unit, content, revision)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("返回") }
            Spacer(Modifier.width(8.dp))
            Text("全部内容 · " + sections.size + " 节", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (sections.isEmpty()) {
            Text("还没有已提交的内容。", fontSize = 13.sp)
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            sections.forEach { section ->
                val unit = section.first
                val content = section.second
                val revision = section.third
                Text("第 " + unit.index + " 节 · " + unit.chapter, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "正式版本 " + revision.version,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                content.blocks.forEach { block ->
                    when (block) {
                        is ContentBlock.Heading -> {
                            Text(block.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                        }
                        is ContentBlock.Dialogue -> {
                            Text(block.text, fontSize = 15.sp, lineHeight = 24.sp)
                            Spacer(Modifier.height(6.dp))
                        }
                        is ContentBlock.Narration -> {
                            Text(block.text, fontSize = 15.sp, lineHeight = 24.sp)
                            Spacer(Modifier.height(10.dp))
                        }
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
