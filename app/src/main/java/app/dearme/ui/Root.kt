package app.dearme.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.dearme.BuildConfig
import app.dearme.R
import app.dearme.container
import app.dearme.data.Call
import app.dearme.data.Capsule
import app.dearme.data.Photo
import app.dearme.data.Ritual
import app.dearme.data.Settings
import app.dearme.notify.Alarms
import app.dearme.ui.capsule.CapsuleScreen
import app.dearme.ui.components.rememberNow
import app.dearme.ui.moment.MomentScreen
import app.dearme.ui.onboarding.OnboardingScreen
import app.dearme.ui.rituals.RitualScreen
import app.dearme.ui.rituals.RitualsTab
import app.dearme.ui.settings.SettingsScreen
import app.dearme.ui.theme.Inter
import app.dearme.ui.theme.U
import app.dearme.ui.vault.VaultTab
import app.dearme.ui.write.WriteScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class Tab { VAULT, RITUALS }

/** Everything stored, plus what lists need, recomputed once per second-ish tick or change. */
@Immutable
class DearData(
    val capsules: List<Capsule>,
    val photos: List<Photo>,
    val rituals: List<Ritual>,
    val calls: List<Call>,
    val settings: Settings,
    val now: Long,
) {
    val photosBy: Map<Long, List<Photo>> = photos.groupBy { it.capsuleId }
    val ritualsById: Map<Long, Ritual> = rituals.associateBy { it.id }
    val sealed: List<Capsule> = capsules.filter { it.openedAt == null && it.openAt > now }.sortedBy { it.openAt }
    val ready: List<Capsule> = capsules.filter { it.isDue(now) }.sortedBy { it.openAt }
    val opened: List<Capsule> = capsules.filter { it.isOpen() }.sortedByDescending { it.openedAt }

    /** Calls from the last half day that weren't answered or waved away, newest per ritual. */
    val waiting: List<Call> = calls
        .filter { it.capsuleId == null && !it.dismissed && now - it.firedAt < 12 * 3_600_000L && ritualsById[it.ritualId] != null }
        .groupBy { it.ritualId }.map { (_, l) -> l.maxBy { it.firedAt } }
        .sortedByDescending { it.firedAt }

    fun thenPhotos(id: Long): List<Photo> = photosBy[id].orEmpty().filter { it.role == Photo.ROLE_THEN }
}

/** Everything a tab can open. */
class Go(private val nav: NavHostController) {
    fun write(replyTo: Long = 0) = nav.navigate("write?replyTo=$replyTo")
    fun capsule(id: Long) = nav.navigate("capsule/$id")
    fun call(id: Long) = nav.navigate("call/$id")
    fun ritual(id: Long, template: String = "") = nav.navigate("ritual/$id?template=$template")
    fun settings() = nav.navigate("settings")
    fun back() = nav.popBackStack()
    fun home() = nav.popBackStack("main", inclusive = false)
}

@Composable
fun DearRoot(openRoute: MutableStateFlow<String?>, settings: Settings) {
    val context = LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val go = remember(nav) { Go(nav) }
    var tab by rememberSaveable { mutableStateOf(Tab.VAULT) }
    val start = remember { if (settings.onboarded) "main" else "welcome" }

    val dao = container.db.dao()
    val capsules by dao.observeCapsules().collectAsStateWithLifecycle(initialValue = null)
    val photos by dao.observePhotos().collectAsStateWithLifecycle(initialValue = emptyList())
    val rituals by dao.observeRituals().collectAsStateWithLifecycle(initialValue = emptyList())
    val calls by dao.observeCalls().collectAsStateWithLifecycle(initialValue = emptyList())
    val tick = rememberNow(1000)
    val data = remember(capsules, photos, rituals, calls, settings, tick) {
        DearData(capsules.orEmpty(), photos, rituals, calls, settings, tick)
    }

    val requested by openRoute.collectAsStateWithLifecycle()
    LaunchedEffect(requested, settings.onboarded, capsules != null) {
        val route = requested ?: return@LaunchedEffect
        if (!settings.onboarded || capsules == null) return@LaunchedEffect
        nav.popBackStack("main", inclusive = false)
        tab = Tab.VAULT
        when {
            route.startsWith("call/") -> route.removePrefix("call/").toLongOrNull()?.let { go.call(it) }
            route.startsWith("capsule/") -> route.removePrefix("capsule/").toLongOrNull()?.let { go.capsule(it) }
        }
        openRoute.value = null
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.scope.launch { container.changed() }
    }
    LaunchedEffect(settings.onboarded) {
        if (settings.onboarded && Build.VERSION.SDK_INT >= 33 && !Alarms.canNotify(context) && !BuildConfig.DEMO) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.background(U.c.bg),
        enterTransition = { fadeIn(tween(240)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable("welcome") {
            OnboardingScreen { nav.navigate("main") { popUpTo("welcome") { inclusive = true } } }
        }
        composable("main") { MainTabs(tab, { tab = it }, go, data) }
        composable("write?replyTo={replyTo}", arguments = listOf(navArgument("replyTo") { type = NavType.LongType; defaultValue = 0L })) { e ->
            WriteScreen(e.arguments?.getLong("replyTo")?.takeIf { it > 0 }, data, go)
        }
        composable("capsule/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            CapsuleScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable("call/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            MomentScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable(
            "ritual/{id}?template={template}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("template") { type = NavType.StringType; defaultValue = "" }),
        ) { e ->
            RitualScreen(e.arguments?.getLong("id") ?: 0L, e.arguments?.getString("template").orEmpty(), data, go)
        }
        composable("settings") { SettingsScreen(data, onBack = { go.back() }) }
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, go: Go, data: DearData) {
    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.VAULT -> VaultTab(data, go)
                Tab.RITUALS -> RitualsTab(data, go)
            }
        }
        HorizontalDivider(color = U.c.line)
        Row(
            Modifier.fillMaxWidth().background(U.c.surface).navigationBarsPadding().height(70.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Inventory2, stringResource(R.string.tab_vault), tab == Tab.VAULT) { onTab(Tab.VAULT) }
            Box(Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(U.c.accent).clickable { go.write() }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(19.dp), tint = U.c.onAccent)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.write), color = U.c.onAccent, fontFamily = Inter, fontSize = 15.sp, maxLines = 1)
                }
            }
            NavItem(Icons.Rounded.Schedule, stringResource(R.string.tab_rituals), tab == Tab.RITUALS) { onTab(Tab.RITUALS) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) U.c.accent else U.c.faint
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) U.c.accentSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp),
        ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
        Spacer(Modifier.height(3.dp))
        Text(label, fontFamily = Inter, fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}
