package fan.x0.focus.feature.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

internal class OverlayWindowOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

internal fun View.attachOverlayOwners(owner: OverlayWindowOwner) {
    setOwner("androidx.lifecycle.ViewTreeLifecycleOwner", "androidx.lifecycle.LifecycleOwner", owner)
    setOwner("androidx.lifecycle.ViewTreeViewModelStoreOwner", "androidx.lifecycle.ViewModelStoreOwner", owner)
    setOwner("androidx.savedstate.ViewTreeSavedStateRegistryOwner", "androidx.savedstate.SavedStateRegistryOwner", owner)
}

private fun View.setOwner(ownerClass: String, interfaceClass: String, value: Any) {
    runCatching {
        val type = Class.forName(interfaceClass)
        val method = Class.forName(ownerClass).getMethod("set", View::class.java, type)
        method.invoke(null, this, value)
    }
}
