package com.github.heartratemonitor_compose.ui.util

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit

// 轻量级 Markdown 渲染：逐行解析 + AnnotatedString，零第三方依赖。
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textSize: TextUnit = TextUnit.Unspecified
) {
    val lines = markdown.lines()
    Column(modifier = modifier) {
        lines.forEachIndexed { index, line ->
            MarkdownLine(line, textColor, textSize)
            if (index < lines.lastIndex && line.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MarkdownLine(
    line: String,
    textColor: Color,
    textSize: TextUnit
) {
    when {
        line.startsWith("### ") -> Text(
            text = parseInline(line.removePrefix("### "), textColor),
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontSize = if (textSize != TextUnit.Unspecified) textSize else TextUnit.Unspecified
        )
        line.startsWith("## ") -> Text(
            text = parseInline(line.removePrefix("## "), textColor),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontSize = if (textSize != TextUnit.Unspecified) textSize else TextUnit.Unspecified
        )
        line.startsWith("# ") -> Text(
            text = parseInline(line.removePrefix("# "), textColor),
            style = MaterialTheme.typography.titleLarge,
            color = textColor,
            fontSize = if (textSize != TextUnit.Unspecified) textSize else TextUnit.Unspecified
        )
        line.startsWith("- ") || line.startsWith("* ") -> {
            val content = line.removePrefix("- ").removePrefix("* ")
            Text(
                text = parseInline("• $content", textColor),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontSize = if (textSize != TextUnit.Unspecified) textSize else TextUnit.Unspecified
            )
        }
        line.trim() == "---" -> {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = textColor.copy(alpha = 0.2f)
            )
        }
        line.isBlank() -> Spacer(Modifier.height(2.dp))
        else -> Text(
            text = parseInline(line, textColor),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontSize = if (textSize != TextUnit.Unspecified) textSize else TextUnit.Unspecified
        )
    }
}

private fun parseInline(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = baseColor.copy(alpha = 0.12f)
                    )
                ) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        if (text[i] == '*' && !text.startsWith("**", i)) {
            val end = text.indexOf('*', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}
