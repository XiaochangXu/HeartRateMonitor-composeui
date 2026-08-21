package com.github.heartratemonitor_compose.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.heartratemonitor_compose.feature.settings.R

@Composable
internal fun AboutHeaderCard(
    currentVersion: String,
    onCopyVersion: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = rememberAppIconPainter()
    val appName = stringResource(com.github.heartratemonitor_compose.service.R.string.app_name)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(MaterialTheme.shapes.extraLarge)
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryContainer,
                                secondaryContainer.copy(alpha = 0.85f)
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = appIcon,
                        contentDescription = appName,
                        modifier = Modifier.size(84.dp)
                    )
                }

             Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                  Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onCopyVersion() })
                            },
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = context.getString(R.string.version_format, currentVersion),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 关于页功能入口组：开源许可/隐私政策/检查更新/GitHub 仓库 */
@Composable
internal fun AboutActionGroup(
    onOpenLicense: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenRepository: () -> Unit,
    updateTitle: String
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = onOpenLicense) {
            SettingsLink(
                title = stringResource(R.string.open_source_license),
                subtitle = stringResource(R.string.subtitle_open_source_license),
                leadingIcon = painterResource(R.drawable.ic_license),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = onOpenPrivacy) {
            SettingsLink(
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.subtitle_privacy_policy),
                leadingIcon = painterResource(R.drawable.ic_privacy),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = onCheckUpdate) {
            SettingsLink(
                title = updateTitle,
                subtitle = stringResource(R.string.subtitle_check_update),
                leadingIcon = painterResource(R.drawable.ic_check_update),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true, onClick = onOpenRepository) {
            SettingsLink(
                title = stringResource(R.string.github_repo),
                subtitle = stringResource(R.string.subtitle_github_repo),
                leadingIcon = painterResource(R.drawable.ic_github_repo),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }
    }
}

/** 关于页维护者卡片：圆形头像（Gitee 优先、GitHub 兜底）+ 名称 */
// 头像源：Gitee CDN 直链优先，失败后回退 GitHub 头像重定向地址
private const val MAINTAINER_AVATAR_GITEE =
    "https://foruda.gitee.com/avatar/1786838770088745389/17345020_xiaochang-xu_1786838770.png!avatar200"
private const val MAINTAINER_AVATAR_GITHUB = "https://github.com/XiaochangXu.png"
private const val MAINTAINER_NAME = "XiaochangXu"

@Composable
internal fun MaintainerCard() {
    val context = LocalContext.current
    var avatarUrl by remember { mutableStateOf(MAINTAINER_AVATAR_GITEE) }
    var avatarFailed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!avatarFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .size(96)
                            .build(),
                        contentDescription = stringResource(R.string.maintainer),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        onError = {
                            if (avatarUrl == MAINTAINER_AVATAR_GITEE) {
                                avatarUrl = MAINTAINER_AVATAR_GITHUB
                            } else {
                                avatarFailed = true
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.maintainer),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MAINTAINER_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun rememberAppIconPainter(): Painter = painterResource(R.drawable.about)
