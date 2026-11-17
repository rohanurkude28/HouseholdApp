package com.houseaffairs.app.ui

import androidx.compose.runtime.Composable
import com.houseaffairs.app.core.ui.HouseBottomBar
import com.houseaffairs.app.core.ui.HouseTab

/** Android's tabs drawn by the shared bar, so both phones' navigation looks the same. */
@Composable
internal fun HaBottomBar(tabs: List<Tab>, selected: Tab?, onSelect: (Tab) -> Unit, onAdd: () -> Unit) {
    HouseBottomBar(tabs.map { it.shared }, selected?.shared, { picked -> tabs.first { it.shared == picked }.let(onSelect) }, onAdd)
}

private val Tab.shared: HouseTab
    get() = when (this) {
        Tab.HOME -> HouseTab.HOME
        Tab.MONEY -> HouseTab.MONEY
        Tab.STOCK -> HouseTab.KITCHEN
        Tab.PLANS -> HouseTab.PLANS
        Tab.VAULT -> HouseTab.THINGS
    }
