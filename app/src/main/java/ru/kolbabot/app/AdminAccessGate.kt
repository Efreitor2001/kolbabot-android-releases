package ru.kolbabot.app

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

internal data class VerifiedAccount(
    val user: JSONObject? = null,
    val checked: Boolean = false,
    val error: String = "",
    val refresh: () -> Unit = {},
) {
    fun isAdmin(api: ApiClient, expectedUserId: Long): Boolean = checked && expectedUserId > 0 &&
        api.userId == expectedUserId && user?.optLong("id") == expectedUserId && user?.optBoolean("is_admin") == true
}

/** Permission proof is never saved with navigation state or borrowed from another account. */
@Composable
internal fun rememberVerifiedAccount(api: ApiClient, expectedUserId: Long): VerifiedAccount {
    val owner = LocalLifecycleOwner.current
    var state by remember(api, expectedUserId) { mutableStateOf(VerifiedAccount()) }
    var revision by remember(api, expectedUserId) { mutableIntStateOf(0) }
    LaunchedEffect(api, expectedUserId, owner, revision) {
        if (expectedUserId <= 0 || api.userId != expectedUserId) {
            state = VerifiedAccount(checked = true)
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val current = api.get("/api/auth/me").optJSONObject("user")
                    state = if (api.userId == expectedUserId && current?.optLong("id") == expectedUserId) {
                        VerifiedAccount(current, checked = true)
                    } else VerifiedAccount(checked = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    state = VerifiedAccount(checked = true, error = if (failure is ApiException && failure.status in listOf(401, 403)) "" else "Не удалось проверить доступ к аккаунту.")
                }
                if (api.userId != expectedUserId) break
                // A role revoked on the website also disappears while this screen stays open.
                delay(30_000)
            }
        }
    }
    return state.copy(refresh = { revision++ })
}
