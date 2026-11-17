package com.houseaffairs.app.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Txn
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.AddRow
import com.houseaffairs.app.core.ui.SectionTitle

/**
 * Money for kids, teens and helpers without the Money module: add what you spent and see your
 * own entries. The family's budget, totals and breakdown stay with the grown-ups (and the rules).
 */
@Composable
fun MySpendsList(spends: List<Txn>, currency: String, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val accent = LocalHouseColors.current.money
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("My spends", style = MaterialTheme.typography.headlineLarge)
                Text("Add what you spend. The family budget is looked after by the grown-ups.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { AddRow("Add what I spent", accent, onAdd) }
        if (spends.isEmpty()) {
            item { Text("Nothing yet. Say “200 stationery” or tap above.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
        } else {
            item { SectionTitle("What I added") }
            items(spends, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${t.category} · ${com.houseaffairs.app.feature.add.formatDate(t.date)}" + if (t.planned) " · planned" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(com.houseaffairs.app.core.util.moneyText(t.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
