package com.itd.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.R

data class BottomTab(val route: String, val label: String, @DrawableRes val icon: Int, val badge: Int? = null)

fun bottomTabs(unread: Int) = listOf(
    BottomTab("feed", "Лента", R.drawable.ic_feed),
    BottomTab("shop", "Магаз", R.drawable.ic_shop),
    BottomTab("notifications", "Уведы", R.drawable.ic_notifications, unread.takeIf { it > 0 }),
    BottomTab("profile", "Профиль", R.drawable.ic_profile),
)

/** Floating pill navigation bar with an optional portal button and the create-post button. */
@Composable
fun ItdBottomBar(
    current: String,
    tabs: List<BottomTab>,
    portalTitle: String?,
    onSelect: (String) -> Unit,
    onPortal: () -> Unit,
    onCreatePost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val border = (if (dark) Color.White else Color.Black).copy(alpha = 0.12f)
    val pill = if (dark) lerp(colors.surface, Color.White, 0.08f) else lerp(colors.surface, Color.Black, 0.08f)
    val items: List<String> = buildList {
        tabs.forEachIndexed { index, tab ->
            if (index == 2 && portalTitle != null) add("portal")
            add(tab.route)
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(60.dp)
                .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(50))
                .border(1.dp, border, RoundedCornerShape(50))
                .padding(4.dp),
        ) {
            val itemWidth = maxWidth / items.size
            val selectedIndex = items.indexOf(current).coerceAtLeast(0)
            val left by animateDpAsState(itemWidth * selectedIndex, spring(dampingRatio = 0.7f, stiffness = 400f), label = "pillLeft")
            val right by animateDpAsState(itemWidth * (selectedIndex + 1), spring(dampingRatio = 0.7f, stiffness = 200f), label = "pillRight")
            if (current in items) {
                Box(Modifier.offset(x = minOf(left, right)).width((right - left).coerceAtLeast(0.dp)).fillMaxHeight().background(pill, RoundedCornerShape(50)))
            }
            Row(Modifier.fillMaxWidth()) {
                items.forEach { route ->
                    val tab = tabs.firstOrNull { it.route == route }
                    val selected = route == current
                    val tint = if (selected) colors.onSurface else colors.onSurfaceVariant
                    Column(
                        Modifier.width(itemWidth).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (route == "portal") onPortal() else onSelect(route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Box {
                            if (tab != null) {
                                Icon(painterResource(tab.icon), tab.label, Modifier.size(22.dp), tint = tint)
                            } else {
                                AsyncImage("file:///android_asset/ic_portal_active.gif", portalTitle, Modifier.size(22.dp))
                            }
                            tab?.badge?.let { count ->
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).height(16.dp)
                                        .border(1.5.dp, colors.surface, CircleShape)
                                        .background(Color(0xFFFF5050), CircleShape)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            tab?.label ?: portalTitle.orEmpty(),
                            color = tint,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (current == "feed") {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = (-64).dp).size(50.dp)
                    .background(colors.primary.copy(alpha = 0.92f), CircleShape)
                    .clickable(onClick = onCreatePost),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add_post), "Создать пост", Modifier.size(18.dp), tint = colors.onPrimary)
            }
        }
    }
}
