package org.thoughtcrime.securesms.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.utilities.Util
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentsPage(
    nestedScrollConnection: NestedScrollConnection,
    content: TabContent?,
    onItemClicked: (MediaOverviewItem) -> Unit,
) {
    when {
        content == null -> {
            // Loading
        }

        content.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.media_overview_documents_fragment__no_documents_found),
                    style = LocalType.current.base,
                    color = LocalColors.current.text
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(nestedScrollConnection)
                    .fillMaxSize()
                    .padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxsSpacing)
            ) {
                for ((bucketTitle, files) in content) {
                    stickyHeader {
                        AttachmentHeader(text = bucketTitle)
                    }

                    items(files) { file ->
                        Row(
                            modifier = Modifier
                                .clickable(onClick = { onItemClicked(file) })
                                .padding(LocalDimensions.current.smallSpacing),
                            horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxsSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painterResource(R.drawable.ic_document_large_dark),
                                contentDescription = null
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing)) {
                                Text(
                                    text = file.fileName.orEmpty(),
                                    style = LocalType.current.large,
                                    color = LocalColors.current.text
                                )

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = Util.getPrettyFileSize(file.fileSize),
                                        style = LocalType.current.small,
                                        color = LocalColors.current.textSecondary,
                                        textAlign = TextAlign.Start,
                                    )

                                    Text(
                                        text = file.date,
                                        style = LocalType.current.small,
                                        color = LocalColors.current.textSecondary,
                                        textAlign = TextAlign.End,
                                    )
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}